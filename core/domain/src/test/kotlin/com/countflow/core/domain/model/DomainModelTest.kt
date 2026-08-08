package com.countflow.core.domain.model

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.LONDON
import com.countflow.core.domain.testing.Fixtures.NEW_YORK
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/** Invariants and behaviour of the domain model itself, independent of the countdown engine. */
class DomainModelTest {

    @Test
    fun `event rejects a blank title`() {
        val error = runCatching {
            Event.create(
                title = "   ",
                target = EventTarget.allDay(Fixtures.date("2026-06-15"), UTC),
                createdAt = Instant.EPOCH,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `identifiers reject blank values`() {
        assertThat(runCatching { EventId("") }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { ReminderId(" ") }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `random identifiers are unique`() {
        val ids = List(1_000) { EventId.random() }.toSet()

        assertThat(ids).hasSize(1_000)
    }

    @Test
    fun `a new event starts neither archived nor completed`() {
        val event = Event.create(
            title = "Holiday",
            target = EventTarget.allDay(Fixtures.date("2026-06-15"), UTC),
            createdAt = Instant.EPOCH,
        )

        assertThat(event.isArchived).isFalse()
        assertThat(event.isCompleted).isFalse()
        assertThat(event.defaultWidgetStyle).isEqualTo(WidgetStyle.Default)
        assertThat(event.accentColor).isEqualTo(AccentColor.Dynamic)
    }

    @Test
    fun `an all-day target stores midnight in its authored zone`() {
        val target = EventTarget.allDay(Fixtures.date("2026-06-15"), NEW_YORK)

        val authored = target.instant.atZone(NEW_YORK)

        assertThat(authored.hour).isEqualTo(0)
        assertThat(authored.toLocalDate()).isEqualTo(Fixtures.date("2026-06-15"))
    }

    @Test
    fun `an all-day target ends at the next midnight`() {
        val target = EventTarget.allDay(Fixtures.date("2026-06-15"), UTC)

        assertThat(target.endAt(UTC).toLocalDate()).isEqualTo(Fixtures.date("2026-06-16"))
        assertThat(target.endAt(UTC).hour).isEqualTo(0)
    }

    @Test
    fun `a timed target ends when it starts`() {
        val target = EventTarget.timed(Fixtures.dateTime("2026-06-15T14:05"), UTC)

        assertThat(target.endAt(UTC)).isEqualTo(target.startAt(UTC))
    }

    // ------------------------------------------------------------ widget binding

    @Test
    fun `a binding inherits style from its event when it has no override`() {
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T12:00"))
            .copy(defaultWidgetStyle = WidgetStyle.OLED, defaultProgressStyle = ProgressStyle.CIRCULAR)
        val binding = WidgetBinding.inheriting(AppWidgetId(1), event.id, Instant.EPOCH)

        assertThat(binding.resolveWidgetStyle(event)).isEqualTo(WidgetStyle.OLED)
        assertThat(binding.resolveProgressStyle(event)).isEqualTo(ProgressStyle.CIRCULAR)
    }

    @Test
    fun `a binding override beats the event default`() {
        // The reason style lives on the binding at all: one event, two widgets, two looks.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T12:00"))
            .copy(defaultWidgetStyle = WidgetStyle.OLED)
        val plain = WidgetBinding.inheriting(AppWidgetId(1), event.id, Instant.EPOCH)
        val overridden = plain.copy(
            appWidgetId = AppWidgetId(2),
            widgetStyleOverride = WidgetStyle.GLASS,
        )

        assertThat(plain.resolveWidgetStyle(event)).isEqualTo(WidgetStyle.OLED)
        assertThat(overridden.resolveWidgetStyle(event)).isEqualTo(WidgetStyle.GLASS)
    }

    // ------------------------------------------------------------ reminders

    @Test
    fun `a reminder fires the configured number of days before, at its time of day`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)
        val reminder = Reminder.of(event.id, ReminderType.SEVEN_DAYS)

        val scheduled = reminder.scheduledTime(event, UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(Fixtures.date("2026-06-08"))
        assertThat(scheduled.toLocalTime()).isEqualTo(LocalTime.of(9, 0))
    }

    @Test
    fun `a reminder keeps its wall clock across a daylight saving change`() {
        // Subtracting seven days as 604,800,000 milliseconds would drift this by an hour,
        // because 2026-03-29 is only 23 hours long in London.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-04-01"), zone = LONDON)
        val reminder = Reminder.of(event.id, ReminderType.SEVEN_DAYS)

        val scheduled = reminder.scheduledTime(event, LONDON)
        Fixtures.assertCrossesDstTransition(
            LONDON,
            scheduled.toInstant(),
            event.target.startAt(LONDON).toInstant(),
        )

        assertThat(scheduled.toLocalDate()).isEqualTo(Fixtures.date("2026-03-25"))
        assertThat(scheduled.toLocalTime()).isEqualTo(LocalTime.of(9, 0))
    }

    @Test
    fun `a day-of reminder for a timed event fires at the event time`() {
        // Notifying at 09:00 about an 06:30 flight would be useless.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T06:30"), zone = UTC)
        val reminder = Reminder.of(event.id, ReminderType.DAY_OF)

        val scheduled = reminder.scheduledTime(event, UTC)

        assertThat(scheduled.toLocalTime()).isEqualTo(LocalTime.of(6, 30))
    }

    @Test
    fun `a day-of reminder for an all-day event fires at the default time`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)
        val reminder = Reminder.of(event.id, ReminderType.DAY_OF)

        val scheduled = reminder.scheduledTime(event, UTC)

        assertThat(scheduled.toLocalDate()).isEqualTo(Fixtures.date("2026-06-15"))
        assertThat(scheduled.toLocalTime()).isEqualTo(Reminder.DEFAULT_TIME_OF_DAY)
    }

    // ------------------------------------------------------------ enums

    @Test
    fun `free widget styles exclude the premium ones`() {
        assertThat(WidgetStyle.free).doesNotContain(WidgetStyle.GLASS)
        assertThat(WidgetStyle.free).contains(WidgetStyle.MINIMAL)
        assertThat(WidgetStyle.Default.isPremium).isFalse()
    }

    @Test
    fun `countdown config rejects nonsensical thresholds`() {
        assertThat(
            runCatching { com.countflow.core.domain.countdown.CountdownConfig(recentPastDays = 0) }
                .exceptionOrNull(),
        ).isInstanceOf(IllegalArgumentException::class.java)
    }
}
