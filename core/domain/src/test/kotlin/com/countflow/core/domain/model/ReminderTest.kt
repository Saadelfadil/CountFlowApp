package com.countflow.core.domain.model

import com.countflow.core.domain.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Exhaustive coverage for [Reminder.scheduledTime] and the resolution helpers built on it
 * (Session 13). These are the pure calculations the notification scheduler's coalescing depends
 * on — every case here mirrors one the brief named explicitly.
 */
class ReminderTest {

    private val nineAm = LocalTime.of(9, 0)

    private fun reminder(
        type: ReminderType,
        timeOfDay: LocalTime = nineAm,
        deliveredForScheduledTime: Instant? = null,
    ) = Reminder(
        id = ReminderId.random(),
        eventId = EventId("test-event"),
        type = type,
        timeOfDay = timeOfDay,
        isEnabled = true,
        deliveredForScheduledTime = deliveredForScheduledTime,
    )

    // ---------------------------------------------------------------- offsets, timed event

    @Test
    fun `thirty days before a timed event steps back thirty calendar days at the reminder time`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.THIRTY_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 16))
        assertThat(scheduled.toLocalTime()).isEqualTo(nineAm)
        assertThat(scheduled.zone).isEqualTo(Fixtures.TOKYO)
    }

    @Test
    fun `seven days before a timed event steps back seven calendar days`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.SEVEN_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 8))
    }

    @Test
    fun `one day before a timed event steps back one calendar day`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.ONE_DAY).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 14))
    }

    @Test
    fun `day of a timed event fires at the event's own instant, not nine am`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T07:00:00"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.DAY_OF).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toInstant()).isEqualTo(event.target.instant)
    }

    // ---------------------------------------------------------------- all-day event

    @Test
    fun `day of an all-day event fires at nine am device time, not the event's own instant`() {
        val event = Fixtures.allDayEvent(Fixtures.date("2026-09-15"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.DAY_OF).scheduledTime(event, Fixtures.NEW_YORK)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 15))
        assertThat(scheduled.toLocalTime()).isEqualTo(nineAm)
        assertThat(scheduled.zone).isEqualTo(Fixtures.NEW_YORK)
    }

    @Test
    fun `seven days before an all-day event follows the device zone, not the authored zone`() {
        // D-014's own policy for all-day targets, applied here too: an all-day event resolves in
        // whatever zone the device is currently in, so a reminder about it should too.
        val event = Fixtures.allDayEvent(Fixtures.date("2026-09-15"), Fixtures.TOKYO)

        val scheduled = reminder(ReminderType.SEVEN_DAYS).scheduledTime(event, Fixtures.NEW_YORK)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 8))
        assertThat(scheduled.zone).isEqualTo(Fixtures.NEW_YORK)
    }

    // ---------------------------------------------------------------- timezone pinning (the fix)

    @Test
    fun `a timed event's reminder is pinned to the event's own zone, unaffected by device zone`() {
        // The regression this session fixed: a Tokyo-zoned flight's "seven days before" reminder
        // must mean the same instant whether the phone is in Tokyo, New York, or anywhere else —
        // exactly the same zone-pinning EventTarget itself already gives the event's own instant.
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.TOKYO)
        val theReminder = reminder(ReminderType.SEVEN_DAYS)

        val fromTokyo = theReminder.scheduledTime(event, Fixtures.TOKYO)
        val fromNewYork = theReminder.scheduledTime(event, Fixtures.NEW_YORK)
        val fromSydney = theReminder.scheduledTime(event, Fixtures.SYDNEY)

        assertThat(fromTokyo.toInstant()).isEqualTo(fromNewYork.toInstant())
        assertThat(fromTokyo.toInstant()).isEqualTo(fromSydney.toInstant())
        assertThat(fromNewYork.zone).isEqualTo(Fixtures.TOKYO)
    }

    @Test
    fun `an all-day event's reminder genuinely shifts with a real device timezone change`() {
        // The opposite of the case above, deliberately: all-day targets are supposed to follow
        // the device (D-014), so this is the one case where the instant SHOULD move.
        val event = Fixtures.allDayEvent(Fixtures.date("2026-09-15"), Fixtures.TOKYO)
        val theReminder = reminder(ReminderType.DAY_OF)

        val fromTokyo = theReminder.scheduledTime(event, Fixtures.TOKYO).toInstant()
        val fromNewYork = theReminder.scheduledTime(event, Fixtures.NEW_YORK).toInstant()

        assertThat(fromTokyo).isNotEqualTo(fromNewYork)
    }

    // ---------------------------------------------------------------- calendar edge cases

    @Test
    fun `seven days before crosses a month boundary correctly`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-10-03T10:00:00"), Fixtures.UTC)

        val scheduled = reminder(ReminderType.SEVEN_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 26))
    }

    @Test
    fun `thirty days before crosses a year boundary correctly`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2027-01-10T10:00:00"), Fixtures.UTC)

        val scheduled = reminder(ReminderType.THIRTY_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 11))
    }

    @Test
    fun `one day before a leap day is february twenty eighth, not an invalid date`() {
        // 2028 is a leap year.
        val event = Fixtures.timedEvent(Fixtures.dateTime("2028-02-29T10:00:00"), Fixtures.UTC)

        val scheduled = reminder(ReminderType.ONE_DAY).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2028, 2, 28))
    }

    @Test
    fun `thirty days before landing on a leap day resolves to february twenty ninth`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2028-03-30T10:00:00"), Fixtures.UTC)

        val scheduled = reminder(ReminderType.THIRTY_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `a reminder date crossing DST spring-forward still resolves to a real instant`() {
        // US spring-forward 2026 is March 8th; a target eight days later crosses it.
        Fixtures.assertCrossesDstTransition(
            Fixtures.NEW_YORK,
            Fixtures.instant("2026-03-07T12:00:00Z"),
            Fixtures.instant("2026-03-09T12:00:00Z"),
        )
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-03-16T09:00:00"), Fixtures.NEW_YORK)

        val scheduled = reminder(ReminderType.SEVEN_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 9))
        assertThat(scheduled.toLocalTime()).isEqualTo(nineAm)
    }

    @Test
    fun `a reminder date crossing DST fall-back still resolves to a real instant`() {
        // US fall-back 2026 is November 1st.
        Fixtures.assertCrossesDstTransition(
            Fixtures.NEW_YORK,
            Fixtures.instant("2026-10-31T12:00:00Z"),
            Fixtures.instant("2026-11-02T12:00:00Z"),
        )
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-11-08T09:00:00"), Fixtures.NEW_YORK)

        val scheduled = reminder(ReminderType.SEVEN_DAYS).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(scheduled.toLocalTime()).isEqualTo(nineAm)
    }

    // ---------------------------------------------------------------- resolution / idempotency

    @Test
    fun `a reminder is not resolved when it has never been delivered`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)

        assertThat(reminder(ReminderType.ONE_DAY).isResolvedFor(event, Fixtures.UTC)).isFalse()
    }

    @Test
    fun `markResolved stamps the currently-computed scheduled time`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
        val theReminder = reminder(ReminderType.ONE_DAY)

        val resolved = theReminder.markResolved(event, Fixtures.UTC)

        assertThat(resolved.isResolvedFor(event, Fixtures.UTC)).isTrue()
        assertThat(resolved.deliveredForScheduledTime)
            .isEqualTo(theReminder.scheduledTime(event, Fixtures.UTC).toInstant())
    }

    @Test
    fun `editing the event to a new date makes an old resolution stop matching, with no reset code needed`() {
        val original = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
        val resolved = reminder(ReminderType.ONE_DAY).markResolved(original, Fixtures.UTC)
        assertThat(resolved.isResolvedFor(original, Fixtures.UTC)).isTrue()

        val edited = original.copy(
            target = EventTarget.timed(Fixtures.dateTime("2026-12-01T14:05:00"), Fixtures.UTC),
        )

        assertThat(resolved.isResolvedFor(edited, Fixtures.UTC)).isFalse()
    }

    @Test
    fun `a reminder whose trigger has already passed is silently resolved, never fired`() {
        // The brief's own example: an event three days away with 30/7/1-day reminders selected —
        // only the 1-day reminder should remain schedulable.
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
        val now = Fixtures.instant("2026-09-12T00:00:00Z") // 3 days before the event

        val thirtyDays = reminder(ReminderType.THIRTY_DAYS).withPastTriggerResolved(event, now, Fixtures.UTC)
        val sevenDays = reminder(ReminderType.SEVEN_DAYS).withPastTriggerResolved(event, now, Fixtures.UTC)
        val oneDay = reminder(ReminderType.ONE_DAY).withPastTriggerResolved(event, now, Fixtures.UTC)

        assertThat(thirtyDays.isResolvedFor(event, Fixtures.UTC)).isTrue()
        assertThat(sevenDays.isResolvedFor(event, Fixtures.UTC)).isTrue()
        assertThat(oneDay.isResolvedFor(event, Fixtures.UTC)).isFalse()
    }

    @Test
    fun `a reminder whose trigger is still in the future is left untouched, ready to schedule`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
        val now = Fixtures.instant("2026-08-01T00:00:00Z")

        val prepared = reminder(ReminderType.SEVEN_DAYS).withPastTriggerResolved(event, now, Fixtures.UTC)

        assertThat(prepared.isResolvedFor(event, Fixtures.UTC)).isFalse()
        assertThat(prepared.deliveredForScheduledTime).isNull()
    }

    @Test
    fun `a reminder due at exactly now counts as already passed, not still pending`() {
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
        val theReminder = reminder(ReminderType.ONE_DAY)
        val exactTrigger = theReminder.scheduledTime(event, Fixtures.UTC).toInstant()

        val prepared = theReminder.withPastTriggerResolved(event, exactTrigger, Fixtures.UTC)

        assertThat(prepared.isResolvedFor(event, Fixtures.UTC)).isTrue()
    }

    // ---------------------------------------------------------------- completed / archived

    @Test
    fun `scheduledTime is unaffected by an event being completed or archived`() {
        // Filtering completed/archived events out of scheduling happens at the repository query
        // level (ReminderDao.ACTIVE_REMINDERS_QUERY), not here — this function has no opinion,
        // deliberately, so there is exactly one place that rule is expressed.
        val event = Fixtures.timedEvent(Fixtures.dateTime("2026-09-15T14:05:00"), Fixtures.UTC)
            .copy(isCompleted = true, isArchived = true)

        val scheduled = reminder(ReminderType.ONE_DAY).scheduledTime(event, Fixtures.UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 14))
    }
}
