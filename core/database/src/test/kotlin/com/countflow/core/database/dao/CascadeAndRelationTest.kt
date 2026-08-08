package com.countflow.core.database.dao

import com.countflow.core.database.DatabaseTestCase
import com.countflow.core.domain.model.ReminderType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Referential integrity, exercised rather than assumed.
 *
 * The repositories are written on the premise that a binding or reminder cannot outlive its
 * event. That premise rests entirely on the foreign-key cascade actually running — and SQLite
 * enforces foreign keys only when the connection has them enabled. If Room ever stopped doing
 * that, nothing else in the codebase would notice until a widget rendered from a dangling row.
 */
internal class CascadeAndRelationTest : DatabaseTestCase() {

    private val events get() = database.eventDao()
    private val bindings get() = database.widgetBindingDao()
    private val reminders get() = database.reminderDao()

    @Test
    fun `deleting an event deletes its widget bindings`() = runTest {
        events.upsertEvent(event("a"))
        bindings.upsertBinding(binding(appWidgetId = 1, eventId = "a"))
        bindings.upsertBinding(binding(appWidgetId = 2, eventId = "a"))

        events.deleteEvent("a")

        assertThat(bindings.getAllBoundWidgets()).isEmpty()
        assertThat(bindings.getBinding(1)).isNull()
    }

    @Test
    fun `deleting an event deletes its reminders`() = runTest {
        events.upsertEvent(event("a"))
        reminders.upsertReminder(reminder("r1", "a", ReminderType.DAY_OF))
        reminders.upsertReminder(reminder("r2", "a", ReminderType.SEVEN_DAYS))

        events.deleteEvent("a")

        assertThat(reminders.getRemindersForEvent("a")).isEmpty()
    }

    @Test
    fun `deleting one event leaves another event's children alone`() = runTest {
        events.upsertEvents(listOf(event("a"), event("b")))
        bindings.upsertBinding(binding(appWidgetId = 1, eventId = "a"))
        bindings.upsertBinding(binding(appWidgetId = 2, eventId = "b"))
        reminders.upsertReminder(reminder("r1", "a"))
        reminders.upsertReminder(reminder("r2", "b"))

        events.deleteEvent("a")

        assertThat(bindings.getBinding(2)).isNotNull()
        assertThat(reminders.getRemindersForEvent("b")).hasSize(1)
    }

    @Test
    fun `a binding cannot be created for an event that does not exist`() = runTest {
        // The foreign key rejects it. Without the constraint this would insert happily and
        // produce a widget bound to nothing.
        val error = runCatching {
            bindings.upsertBinding(binding(appWidgetId = 1, eventId = "ghost"))
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `a reminder cannot be created for an event that does not exist`() = runTest {
        val error = runCatching {
            reminders.upsertReminder(reminder("r1", "ghost"))
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `an event cannot have two reminders of the same type`() = runTest {
        // Enforced by a unique index rather than by application code, so a double tap on the
        // reminder toggle or a replayed restore cannot produce duplicate notifications.
        events.upsertEvent(event("a"))
        reminders.upsertReminder(reminder("r1", "a", ReminderType.SEVEN_DAYS))

        reminders.upsertReminder(reminder("r2", "a", ReminderType.SEVEN_DAYS))

        val stored = reminders.getRemindersForEvent("a")
        assertThat(stored).hasSize(1)
    }

    @Test
    fun `the same reminder type on different events is allowed`() = runTest {
        events.upsertEvents(listOf(event("a"), event("b")))

        reminders.upsertReminder(reminder("r1", "a", ReminderType.SEVEN_DAYS))
        reminders.upsertReminder(reminder("r2", "b", ReminderType.SEVEN_DAYS))

        assertThat(reminders.getRemindersForEvent("a")).hasSize(1)
        assertThat(reminders.getRemindersForEvent("b")).hasSize(1)
    }

    // ---------------------------------------------------------------- relation

    @Test
    fun `a bound widget resolves its event through the relation`() = runTest {
        events.upsertEvent(event("a", title = "Kyoto"))
        bindings.upsertBinding(binding(appWidgetId = 7, eventId = "a"))

        val bound = bindings.getBoundWidget(7)

        assertThat(bound?.binding?.appWidgetId).isEqualTo(7)
        assertThat(bound?.event?.title).isEqualTo("Kyoto")
    }

    @Test
    fun `the bound widget flow re-emits when the underlying event changes`() = runTest {
        // A widget observes its binding; renaming the event must reach it without the widget
        // knowing the event table exists.
        events.upsertEvent(event("a", title = "Before"))
        bindings.upsertBinding(binding(appWidgetId = 1, eventId = "a"))

        assertThat(bindings.observeBoundWidget(1).first()?.event?.title).isEqualTo("Before")

        events.upsertEvent(event("a", title = "After"))

        assertThat(bindings.observeBoundWidget(1).first()?.event?.title).isEqualTo("After")
    }

    // ---------------------------------------------------------------- pruning

    @Test
    fun `pruning keeps bindings the launcher still knows about`() = runTest {
        events.upsertEvent(event("a"))
        listOf(1, 2, 3).forEach { bindings.upsertBinding(binding(it, "a")) }

        bindings.pruneOrphanedBindings(setOf(1, 3))

        assertThat(bindings.getAllBoundWidgets().map { it.binding.appWidgetId })
            .containsExactly(1, 3)
    }

    @Test
    fun `deleting a batch of bindings removes exactly those`() = runTest {
        events.upsertEvent(event("a"))
        listOf(1, 2, 3).forEach { bindings.upsertBinding(binding(it, "a")) }

        bindings.deleteBindings(listOf(1, 3))

        assertThat(bindings.getAllBoundWidgets().map { it.binding.appWidgetId })
            .containsExactly(2)
    }

    @Test
    fun `deleting bindings for an event leaves the event intact`() = runTest {
        // Unbinding is not deleting: the user removed a widget, not the countdown.
        events.upsertEvent(event("a"))
        bindings.upsertBinding(binding(appWidgetId = 1, eventId = "a"))

        bindings.deleteBindingsForEvent("a")

        assertThat(bindings.getAllBoundWidgets()).isEmpty()
        assertThat(events.getEvent("a")).isNotNull()
    }

    // ---------------------------------------------------------------- active reminders

    @Test
    fun `active reminders require both the reminder and its event to be enabled`() = runTest {
        events.upsertEvents(
            listOf(
                event("on", remindersEnabled = true),
                event("off", remindersEnabled = false),
            ),
        )
        reminders.upsertReminder(reminder("keep", "on", isEnabled = true))
        reminders.upsertReminder(reminder("reminder-off", "on", ReminderType.ONE_DAY, isEnabled = false))
        reminders.upsertReminder(reminder("event-off", "off", isEnabled = true))

        val active = reminders.getActiveReminders().map { it.id }

        assertThat(active).containsExactly("keep")
    }

    @Test
    fun `archived and completed events produce no active reminders`() = runTest {
        events.upsertEvents(
            listOf(
                event("archived", isArchived = true),
                event("completed", isCompleted = true),
            ),
        )
        reminders.upsertReminder(reminder("r1", "archived"))
        reminders.upsertReminder(reminder("r2", "completed"))

        assertThat(reminders.getActiveReminders()).isEmpty()
    }

    @Test
    fun `replacing an event's reminders is atomic`() = runTest {
        events.upsertEvent(event("a"))
        reminders.upsertReminder(reminder("old1", "a", ReminderType.DAY_OF))
        reminders.upsertReminder(reminder("old2", "a", ReminderType.ONE_DAY))

        reminders.replaceRemindersForEvent(
            eventId = "a",
            reminders = listOf(reminder("new", "a", ReminderType.THIRTY_DAYS)),
        )

        val stored = reminders.getRemindersForEvent("a")
        assertThat(stored.map { it.id }).containsExactly("new")
    }
}
