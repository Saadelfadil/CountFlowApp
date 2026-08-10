package com.countflow.core.notifications

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.repository.ActiveReminder
import com.countflow.core.domain.repository.ReminderRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Exercises the full orchestration with fakes for both [NotificationSender] and
 * [NotificationAlarmScheduler] — no Android, no Robolectric, matching
 * `WidgetRefreshCoordinatorTest`'s own reasoning (Session 12): the sequencing is real business
 * logic and deserves real tests; the platform calls behind the two seams do not need this class
 * to prove them.
 */
class ReminderNotificationCoordinatorTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-08-09T12:00:00Z")
    private val clock = Clock.fixed(now, zone)

    private fun event(id: String, at: String) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.timed(LocalDateTime.parse(at), zone),
        createdAt = Instant.EPOCH,
        remindersEnabled = true,
    )

    private fun reminder(
        id: String,
        eventId: String,
        type: ReminderType = ReminderType.DAY_OF,
        deliveredForScheduledTime: Instant? = null,
    ) = Reminder(
        id = ReminderId(id),
        eventId = EventId(eventId),
        type = type,
        timeOfDay = LocalTime.of(9, 0),
        isEnabled = true,
        deliveredForScheduledTime = deliveredForScheduledTime,
    )

    private fun coordinator(
        repository: FakeReminderRepository,
        sender: FakeNotificationSender = FakeNotificationSender(),
        scheduler: FakeNotificationAlarmScheduler = FakeNotificationAlarmScheduler(),
    ) = ReminderNotificationCoordinator(repository, sender, scheduler, clock)

    @Test
    fun `no active reminders schedules no alarm`() = runTest {
        val repository = FakeReminderRepository(emptyList())
        val scheduler = FakeNotificationAlarmScheduler()

        val outcome = coordinator(repository, scheduler = scheduler).processDueAndReschedule()

        assertThat(outcome.nextReminderAt).isNull()
        assertThat(scheduler.scheduledAt).isNull()
        assertThat(scheduler.cancelCount).isEqualTo(1)
    }

    @Test
    fun `one future reminder schedules exactly one alarm at its trigger`() = runTest {
        val e = event("a", "2026-08-20T09:00:00")
        val r = reminder("r1", "a", ReminderType.DAY_OF)
        val repository = FakeReminderRepository(listOf(ActiveReminder(r, e)))
        val scheduler = FakeNotificationAlarmScheduler()

        val outcome = coordinator(repository, scheduler = scheduler).processDueAndReschedule()

        assertThat(outcome.nextReminderAt).isEqualTo(r.scheduledTime(e, zone).toInstant())
        assertThat(scheduler.scheduledAt).isEqualTo(outcome.nextReminderAt)
    }

    @Test
    fun `many future reminders coalesce to one alarm at the earliest trigger`() = runTest {
        val soon = event("soon", "2026-08-10T09:00:00")
        val later = event("later", "2026-09-01T09:00:00")
        val repository = FakeReminderRepository(
            listOf(
                ActiveReminder(reminder("r-later", "later", ReminderType.DAY_OF), later),
                ActiveReminder(reminder("r-soon", "soon", ReminderType.DAY_OF), soon),
            ),
        )
        val scheduler = FakeNotificationAlarmScheduler()

        val outcome = coordinator(repository, scheduler = scheduler).processDueAndReschedule()

        val soonTrigger = reminder("r-soon", "soon", ReminderType.DAY_OF).scheduledTime(soon, zone).toInstant()
        assertThat(outcome.nextReminderAt).isEqualTo(soonTrigger)
        assertThat(scheduler.scheduleCount).isEqualTo(1)
    }

    @Test
    fun `two reminders due at the exact same instant are both delivered in one cycle`() = runTest {
        val e = event("a", "2026-08-09T11:00:00") // already past `now`, both DAY_OF-equivalent
        val r1 = reminder("r1", "a", ReminderType.DAY_OF)
        val r2 = Reminder(
            id = ReminderId("r2"),
            eventId = EventId("a"),
            type = ReminderType.ONE_DAY,
            timeOfDay = LocalTime.of(11, 0),
            isEnabled = true,
        )
        // Both computed to fire at/around the same past instant relative to `now`.
        val repository = FakeReminderRepository(listOf(ActiveReminder(r1, e), ActiveReminder(r2, e)))
        val sender = FakeNotificationSender()

        val outcome = coordinator(repository, sender = sender).processDueAndReschedule()

        assertThat(outcome.remindersDelivered).isEqualTo(2)
        assertThat(sender.sent).hasSize(2)
    }

    @Test
    fun `an earlier reminder added after a later one is scheduled replaces the later alarm`() = runTest {
        val later = event("later", "2026-09-01T09:00:00")
        val repository = FakeReminderRepository(
            listOf(ActiveReminder(reminder("r-later", "later"), later)),
        )
        val scheduler = FakeNotificationAlarmScheduler()
        val c = coordinator(repository, scheduler = scheduler)
        c.processDueAndReschedule()
        val firstScheduled = scheduler.scheduledAt

        val soon = event("soon", "2026-08-10T09:00:00")
        repository.setActive(
            listOf(
                ActiveReminder(reminder("r-later", "later"), later),
                ActiveReminder(reminder("r-soon", "soon"), soon),
            ),
        )
        val outcome = c.processDueAndReschedule()

        assertThat(outcome.nextReminderAt).isLessThan(firstScheduled)
        assertThat(scheduler.scheduledAt).isEqualTo(outcome.nextReminderAt)
    }

    @Test
    fun `a later reminder added after an earlier one does not replace the earlier alarm`() = runTest {
        val soon = event("soon", "2026-08-10T09:00:00")
        val repository = FakeReminderRepository(
            listOf(ActiveReminder(reminder("r-soon", "soon"), soon)),
        )
        val c = coordinator(repository)
        val first = c.processDueAndReschedule()

        val later = event("later", "2026-09-01T09:00:00")
        repository.setActive(
            listOf(
                ActiveReminder(reminder("r-soon", "soon"), soon),
                ActiveReminder(reminder("r-later", "later"), later),
            ),
        )
        val second = c.processDueAndReschedule()

        assertThat(second.nextReminderAt).isEqualTo(first.nextReminderAt)
    }

    @Test
    fun `removing the earliest active reminder recalculates the schedule to the next earliest`() = runTest {
        val soon = event("soon", "2026-08-10T09:00:00")
        val later = event("later", "2026-09-01T09:00:00")
        val repository = FakeReminderRepository(
            listOf(
                ActiveReminder(reminder("r-soon", "soon"), soon),
                ActiveReminder(reminder("r-later", "later"), later),
            ),
        )
        val c = coordinator(repository)
        c.processDueAndReschedule()

        // Simulates the event completing, being deleted, or its reminder toggle turning off —
        // all of which remove a row from what ACTIVE_REMINDERS_QUERY returns.
        repository.setActive(listOf(ActiveReminder(reminder("r-later", "later"), later)))
        val outcome = c.processDueAndReschedule()

        assertThat(outcome.nextReminderAt)
            .isEqualTo(reminder("r-later", "later").scheduledTime(later, zone).toInstant())
    }

    @Test
    fun `editing an event to a nearer date recalculates the trigger`() = runTest {
        val original = event("a", "2026-09-01T09:00:00")
        val repository = FakeReminderRepository(
            listOf(ActiveReminder(reminder("r1", "a"), original)),
        )
        val c = coordinator(repository)
        val before = c.processDueAndReschedule()

        val edited = original.copy(target = EventTarget.timed(LocalDateTime.parse("2026-08-11T09:00:00"), zone))
        repository.setActive(listOf(ActiveReminder(reminder("r1", "a"), edited)))
        val after = c.processDueAndReschedule()

        assertThat(after.nextReminderAt).isLessThan(before.nextReminderAt)
    }

    @Test
    fun `a due reminder is delivered once and never redelivered on a later cycle`() = runTest {
        val e = event("a", "2026-08-09T11:00:00") // due relative to `now`
        val r = reminder("r1", "a", ReminderType.DAY_OF)
        val repository = FakeReminderRepository(listOf(ActiveReminder(r, e)))
        val sender = FakeNotificationSender()
        val c = coordinator(repository, sender = sender)

        val first = c.processDueAndReschedule()
        assertThat(first.remindersDelivered).isEqualTo(1)
        assertThat(sender.sent).hasSize(1)

        // The reactive scheduler re-triggers on ANY change, including an unrelated one — this
        // simulates that second call against the now-resolved state the first call persisted.
        val second = c.processDueAndReschedule()

        assertThat(second.remindersDelivered).isEqualTo(0)
        assertThat(sender.sent).hasSize(1)
    }

    @Test
    fun `a reminder whose trigger already passed before it was ever scheduled is never delivered`() = runTest {
        // Simulates EditEventViewModel.onSave() correctly applying withPastTriggerResolved
        // before the first persist — the contract the coordinator trusts (see the class KDoc).
        val e = event("a", "2026-08-12T09:00:00")
        val stale = reminder("r1", "a", ReminderType.THIRTY_DAYS)
        val alreadyResolved = stale.withPastTriggerResolved(e, now, zone)
        val repository = FakeReminderRepository(listOf(ActiveReminder(alreadyResolved, e)))
        val sender = FakeNotificationSender()

        val outcome = coordinator(repository, sender = sender).processDueAndReschedule()

        assertThat(outcome.remindersDelivered).isEqualTo(0)
        assertThat(sender.sent).isEmpty()
        assertThat(outcome.nextReminderAt).isNull()
    }

    // ---------------------------------------------------------------- fakes

    private class FakeReminderRepository(initial: List<ActiveReminder>) : ReminderRepository {
        private val active = MutableStateFlow(initial)

        fun setActive(items: List<ActiveReminder>) {
            active.value = items
        }

        override fun observeActiveReminders(): Flow<List<ActiveReminder>> = active

        override suspend fun upsertReminder(reminder: Reminder) {
            active.value = active.value.map {
                if (it.reminder.id == reminder.id) it.copy(reminder = reminder) else it
            }
        }

        override fun observeRemindersForEvent(eventId: EventId) =
            throw UnsupportedOperationException("not used by the coordinator")

        override suspend fun getRemindersForEvent(eventId: EventId) =
            throw UnsupportedOperationException("not used by the coordinator")

        override suspend fun getActiveReminders() =
            throw UnsupportedOperationException("not used by the coordinator")

        override suspend fun replaceRemindersForEvent(eventId: EventId, reminders: List<Reminder>) =
            throw UnsupportedOperationException("not used by the coordinator")

        override suspend fun deleteReminder(id: ReminderId) =
            throw UnsupportedOperationException("not used by the coordinator")
    }

    private class FakeNotificationSender : NotificationSender {
        val sent = mutableListOf<Pair<Reminder, Event>>()

        override suspend fun send(reminder: Reminder, event: Event) {
            sent += reminder to event
        }
    }

    private class FakeNotificationAlarmScheduler : NotificationAlarmScheduler {
        var scheduledAt: Instant? = null
            private set
        var scheduleCount = 0
            private set
        var cancelCount = 0
            private set

        override fun scheduleNextReminder(at: Instant) {
            scheduledAt = at
            scheduleCount++
        }

        override fun cancelScheduledReminder() {
            scheduledAt = null
            cancelCount++
        }
    }
}
