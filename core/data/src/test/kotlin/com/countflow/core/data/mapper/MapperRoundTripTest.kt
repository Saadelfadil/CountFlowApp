package com.countflow.core.data.mapper

import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Round-trip coverage for entity/domain mapping.
 *
 * These catch the quietest class of bug in the project. A field dropped in one direction does
 * not fail a build or throw at runtime — the user simply finds their emoji gone, or their event
 * back at the default colour, after restarting the app. Asserting `domain == domain.toEntity().toDomain()`
 * makes any asymmetry fail immediately.
 */
class MapperRoundTripTest {

    private val zone = ZoneId.of("Europe/London")

    private fun fullyPopulatedEvent(accent: AccentColor) = Event(
        id = EventId("event-1"),
        title = "Trip to Kyoto",
        emoji = "🌸",
        iconKey = "plane",
        category = EventCategory.TRAVEL,
        target = EventTarget.timed(LocalDate.of(2026, 11, 3).atTime(14, 5), zone),
        createdAt = Instant.parse("2026-01-15T09:30:00Z"),
        accentColor = accent,
        defaultWidgetStyle = WidgetStyle.GLASS,
        defaultProgressStyle = ProgressStyle.CIRCULAR,
        remindersEnabled = true,
        isArchived = true,
        isCompleted = true,
    )

    @Test
    fun `an event with every field set survives a round trip`() {
        val original = fullyPopulatedEvent(AccentColor.Fixed(0xFF00695C.toInt()))

        val restored = original.toEntity().toDomain()

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `an event with every optional field null survives a round trip`() {
        val original = Event.create(
            id = EventId("event-2"),
            title = "Minimal",
            target = EventTarget.allDay(LocalDate.of(2026, 12, 25), zone),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        val restored = original.toEntity().toDomain()

        assertThat(restored).isEqualTo(original)
        assertThat(restored.emoji).isNull()
        assertThat(restored.iconKey).isNull()
    }

    @Test
    fun `dynamic accent survives as dynamic rather than becoming a colour`() {
        // The column is a nullable Int and null means dynamic. If that convention is ever
        // mapped the wrong way round, every event silently gains a black accent.
        val original = fullyPopulatedEvent(AccentColor.Dynamic)

        val entity = original.toEntity()
        val restored = entity.toDomain()

        assertThat(entity.accentArgb).isNull()
        assertThat(restored.accentColor).isEqualTo(AccentColor.Dynamic)
    }

    @Test
    fun `a fixed accent preserves its exact alpha channel`() {
        // Fully opaque black is 0xFF000000, which is a negative Int. Storing it through a
        // nullable column is exactly where a sign or boxing mistake would surface.
        val original = fullyPopulatedEvent(AccentColor.Fixed(0xFF000000.toInt()))

        val restored = original.toEntity().toDomain()

        assertThat(restored.accentColor).isEqualTo(AccentColor.Fixed(0xFF000000.toInt()))
    }

    @Test
    fun `an all-day target keeps its zone and flag`() {
        // If isAllDay were dropped, every all-day event would silently become a midnight
        // appointment that expires one day early.
        val original = Event.create(
            title = "New Year",
            target = EventTarget.allDay(LocalDate.of(2026, 12, 31), ZoneId.of("Asia/Tokyo")),
            createdAt = Instant.EPOCH,
        )

        val restored = original.toEntity().toDomain()

        assertThat(restored.target.isAllDay).isTrue()
        assertThat(restored.target.zoneId).isEqualTo("Asia/Tokyo")
        assertThat(restored.target.authoredDate()).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `every event category round trips`() {
        EventCategory.entries.forEach { category ->
            val original = Event.create(
                title = "Category test",
                target = EventTarget.allDay(LocalDate.of(2026, 6, 15), zone),
                createdAt = Instant.EPOCH,
                category = category,
            )

            assertThat(original.toEntity().toDomain().category).isEqualTo(category)
        }
    }

    @Test
    fun `every widget and progress style round trips`() {
        WidgetStyle.entries.forEach { style ->
            ProgressStyle.entries.forEach { progress ->
                val original = Event.create(
                    title = "Style test",
                    target = EventTarget.allDay(LocalDate.of(2026, 6, 15), zone),
                    createdAt = Instant.EPOCH,
                    defaultWidgetStyle = style,
                    defaultProgressStyle = progress,
                )

                val restored = original.toEntity().toDomain()

                assertThat(restored.defaultWidgetStyle).isEqualTo(style)
                assertThat(restored.defaultProgressStyle).isEqualTo(progress)
            }
        }
    }

    // ---------------------------------------------------------------- bindings

    @Test
    fun `a widget binding with overrides including a fixed accent survives a round trip`() {
        val original = WidgetBinding(
            appWidgetId = AppWidgetId(42),
            eventId = EventId("event-1"),
            widgetStyleOverride = WidgetStyle.OLED,
            progressStyleOverride = ProgressStyle.NONE,
            accentColorOverride = AccentColor.Fixed(0xFF00897B.toInt()),
            showTitle = false,
            showEmoji = true,
            showTargetDate = true,
            showPercentage = false,
            createdAt = Instant.parse("2026-02-02T10:00:00Z"),
        )

        assertThat(original.toEntity().toDomain()).isEqualTo(original)
    }

    @Test
    fun `a widget binding overriding accent to Dynamic survives a round trip distinctly from no override`() {
        // The trap this asymmetric encoding creates: has_accent_override=true with a null
        // accent_argb_override means "the override itself is Dynamic," not "no override" — those
        // two states share the same accent_argb_override value (null) and are only told apart by
        // has_accent_override. A binding whose event default is a Fixed color, explicitly
        // overridden back to Dynamic per-widget, is exactly the case that would silently collapse
        // into "no override" if that flag were dropped or ignored.
        val overriddenToDynamic = WidgetBinding(
            appWidgetId = AppWidgetId(43),
            eventId = EventId("event-1"),
            widgetStyleOverride = null,
            progressStyleOverride = null,
            accentColorOverride = AccentColor.Dynamic,
            showTitle = true,
            showEmoji = true,
            showTargetDate = false,
            showPercentage = false,
            createdAt = Instant.parse("2026-02-02T10:00:00Z"),
        )
        val noOverride = overriddenToDynamic.copy(appWidgetId = AppWidgetId(44), accentColorOverride = null)

        val restoredOverridden = overriddenToDynamic.toEntity().toDomain()
        val restoredNoOverride = noOverride.toEntity().toDomain()

        assertThat(restoredOverridden.accentColorOverride).isEqualTo(AccentColor.Dynamic)
        assertThat(restoredNoOverride.accentColorOverride).isNull()
        assertThat(restoredOverridden).isEqualTo(overriddenToDynamic)
        assertThat(restoredNoOverride).isEqualTo(noOverride)
    }

    @Test
    fun `an inheriting binding keeps its nulls rather than materialising defaults`() {
        // Null means "inherit". Mapping it to a concrete style here would freeze the widget's
        // appearance at creation time, so changing the event's style would stop affecting it.
        val original = WidgetBinding.inheriting(
            appWidgetId = AppWidgetId(7),
            eventId = EventId("event-1"),
            createdAt = Instant.EPOCH,
        )

        val restored = original.toEntity().toDomain()

        assertThat(restored).isEqualTo(original)
        assertThat(restored.widgetStyleOverride).isNull()
        assertThat(restored.progressStyleOverride).isNull()
        assertThat(restored.accentColorOverride).isNull()
    }

    // ---------------------------------------------------------------- reminders

    @Test
    fun `a reminder survives a round trip including its time of day`() {
        val original = Reminder(
            id = ReminderId("reminder-1"),
            eventId = EventId("event-1"),
            type = ReminderType.SEVEN_DAYS,
            timeOfDay = LocalTime.of(18, 45),
            isEnabled = false,
        )

        assertThat(original.toEntity().toDomain()).isEqualTo(original)
    }

    @Test
    fun `every reminder type round trips`() {
        ReminderType.entries.forEach { type ->
            val original = Reminder.of(EventId("event-1"), type)

            assertThat(original.toEntity().toDomain().type).isEqualTo(type)
        }
    }
}
