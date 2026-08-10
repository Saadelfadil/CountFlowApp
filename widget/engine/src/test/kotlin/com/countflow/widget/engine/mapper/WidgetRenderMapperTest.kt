package com.countflow.widget.engine.mapper

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetRenderMapperTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-15T08:00:00Z")
    private val engine = CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default)

    private fun event(
        emoji: String? = null,
        category: EventCategory = EventCategory.TRAVEL,
        defaultStyle: WidgetStyle = WidgetStyle.MINIMAL,
        defaultProgress: ProgressStyle = ProgressStyle.LINEAR,
        accent: AccentColor = AccentColor.Dynamic,
        isCompleted: Boolean = false,
        daysAhead: Long = 12,
    ) = Event.create(
        id = EventId("event-1"),
        title = "Trip to Kyoto",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15).plusDays(daysAhead), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        emoji = emoji,
        category = category,
        defaultWidgetStyle = defaultStyle,
        defaultProgressStyle = defaultProgress,
        accentColor = accent,
    ).copy(isCompleted = isCompleted)

    private fun binding(
        eventId: EventId = EventId("event-1"),
        appWidgetId: AppWidgetId = AppWidgetId(1),
        styleOverride: WidgetStyle? = null,
        progressOverride: ProgressStyle? = null,
        accentOverride: AccentColor? = null,
        showTitle: Boolean = true,
        showEmoji: Boolean = true,
        showDate: Boolean = false,
        showPercentage: Boolean = false,
    ) = WidgetBinding.inheriting(appWidgetId, eventId, Instant.EPOCH).copy(
        widgetStyleOverride = styleOverride,
        progressStyleOverride = progressOverride,
        accentColorOverride = accentOverride,
        showTitle = showTitle,
        showEmoji = showEmoji,
        showTargetDate = showDate,
        showPercentage = showPercentage,
    )

    private fun map(event: Event, binding: WidgetBinding) =
        WidgetRenderMapper.map(event, binding, engine.countdown(event), zone)

    @Test
    fun `carries identity fields through unchanged`() {
        val model = map(event(), binding())

        assertThat(model.eventId).isEqualTo(EventId("event-1"))
        assertThat(model.appWidgetId).isEqualTo(AppWidgetId(1))
        assertThat(model.title).isEqualTo("Trip to Kyoto")
    }

    @Test
    fun `falls back to the category default emoji`() {
        val model = map(event(emoji = null, category = EventCategory.BIRTHDAY), binding())

        assertThat(model.emoji).isEqualTo("🎂")
    }

    @Test
    fun `an event's own emoji wins over the category default`() {
        val model = map(event(emoji = "🚀"), binding())

        assertThat(model.emoji).isEqualTo("🚀")
    }

    @Test
    fun `day count and label match the countdown`() {
        // 2026-06-15 is a Monday; 20 days out clears both the current and next calendar week,
        // landing on a plain InDays count rather than NextWeek.
        val model = map(event(daysAhead = 20), binding())

        assertThat(model.daysRemaining).isEqualTo(20)
        assertThat(model.label).isEqualTo(CountdownLabel.InDays(20))
        assertThat(model.showDaysValue).isTrue()
    }

    @Test
    fun `a binding override beats the event default style`() {
        // The reason style lives on the binding at all: one event, two widgets, two looks.
        val plain = map(event(defaultStyle = WidgetStyle.OLED), binding())
        val overridden = map(event(defaultStyle = WidgetStyle.OLED), binding(styleOverride = WidgetStyle.GLASS))

        assertThat(plain.theme.style).isEqualTo(WidgetStyle.OLED)
        assertThat(overridden.theme.style).isEqualTo(WidgetStyle.GLASS)
    }

    @Test
    fun `a binding override beats the event default progress style`() {
        val plain = map(event(defaultProgress = ProgressStyle.LINEAR), binding())
        val overridden = map(
            event(defaultProgress = ProgressStyle.LINEAR),
            binding(progressOverride = ProgressStyle.NONE),
        )

        assertThat(plain.progress.isVisible).isTrue()
        assertThat(overridden.progress.isVisible).isFalse()
    }

    @Test
    fun `visibility toggles come from the binding, not the event`() {
        val model = map(event(), binding(showTitle = false, showEmoji = false, showDate = true))

        assertThat(model.showTitle).isFalse()
        assertThat(model.showEmoji).isFalse()
        assertThat(model.showDate).isTrue()
    }

    @Test
    fun `completed and expired flags reflect the countdown, not just the event`() {
        val completed = map(event(isCompleted = true, daysAhead = -30), binding())
        val expired = map(event(daysAhead = -30), binding())
        val upcoming = map(event(daysAhead = 5), binding())

        assertThat(completed.isCompleted).isTrue()
        assertThat(completed.isExpired).isFalse() // completed takes precedence over expired

        assertThat(expired.isCompleted).isFalse()
        assertThat(expired.isExpired).isTrue()

        assertThat(upcoming.isExpired).isFalse()
    }

    @Test
    fun `the target and zone are carried through for the renderer to format`() {
        val subject = event(daysAhead = 12)
        val model = map(subject, binding())

        assertThat(model.target).isEqualTo(subject.target)
        assertThat(model.targetZone).isEqualTo(zone)
    }

    @Test
    fun `a fixed accent flows through to the resolved theme`() {
        val model = map(event(accent = AccentColor.Fixed(0xFF112233.toInt())), binding())

        assertThat(model.theme.accentColorArgb).isEqualTo(0xFF112233.toInt())
    }

    // ── Widget accent override (Samsung Galaxy A55 physical-device finding): Customize Widget
    // presents Accent as a per-widget setting, exactly like Style and Progress, but no field ever
    // carried a per-widget accent choice to the renderer — the picker's own selection was applied
    // to the live preview by faking a copy of the event, then dropped entirely on Save. ──

    @Test
    fun `a binding's accent override beats the event's own accent`() {
        val plain = map(event(accent = AccentColor.Fixed(0xFF00897B.toInt())), binding())
        val overridden = map(
            event(accent = AccentColor.Fixed(0xFF00897B.toInt())),
            binding(accentOverride = AccentColor.Fixed(0xFF1E88E5.toInt())),
        )

        assertThat(plain.theme.accentColorArgb).isEqualTo(0xFF00897B.toInt())
        assertThat(overridden.theme.accentColorArgb).isEqualTo(0xFF1E88E5.toInt())
    }

    @Test
    fun `a binding can override accent back to Dynamic even when the event default is a fixed color`() {
        val model = map(
            event(accent = AccentColor.Fixed(0xFF00897B.toInt())),
            binding(accentOverride = AccentColor.Dynamic),
        )

        assertThat(model.theme.accentColorArgb).isNull()
    }

    @Test
    fun `no accent override inherits the event's accent, dynamic or fixed`() {
        val dynamicEvent = map(event(accent = AccentColor.Dynamic), binding())
        val fixedEvent = map(event(accent = AccentColor.Fixed(0xFFE53935.toInt())), binding())

        assertThat(dynamicEvent.theme.accentColorArgb).isNull()
        assertThat(fixedEvent.theme.accentColorArgb).isEqualTo(0xFFE53935.toInt())
    }

    @Test
    fun `changing accent does not change the resolved style or progress`() {
        val model = map(
            event(defaultStyle = WidgetStyle.MODERN, defaultProgress = ProgressStyle.CIRCULAR),
            binding(accentOverride = AccentColor.Fixed(0xFFD81B60.toInt())),
        )

        assertThat(model.theme.style).isEqualTo(WidgetStyle.MODERN)
        assertThat(model.progress.style).isEqualTo(ProgressStyle.CIRCULAR)
    }

    @Test
    fun `two widgets on the same event can have independently different accent overrides`() {
        // "One event, Widget A = Red, Widget B = Blue" — both must stay independently
        // customizable, and neither widget's own override should leak into the other's.
        val sharedEvent = event(accent = AccentColor.Fixed(0xFF00897B.toInt()))
        val widgetA = map(
            sharedEvent,
            binding(appWidgetId = AppWidgetId(101), accentOverride = AccentColor.Fixed(0xFFE53935.toInt())),
        )
        val widgetB = map(
            sharedEvent,
            binding(appWidgetId = AppWidgetId(102), accentOverride = AccentColor.Fixed(0xFF1E88E5.toInt())),
        )
        val widgetC = map(sharedEvent, binding(appWidgetId = AppWidgetId(103)))

        assertThat(widgetA.theme.accentColorArgb).isEqualTo(0xFFE53935.toInt())
        assertThat(widgetB.theme.accentColorArgb).isEqualTo(0xFF1E88E5.toInt())
        // Widget C never overrode anything — it still inherits the event's own teal, unaffected
        // by A or B's independent choices.
        assertThat(widgetC.theme.accentColorArgb).isEqualTo(0xFF00897B.toInt())
    }

    @Test
    fun `percent text follows the binding's own toggle`() {
        val requested = map(event(), binding(showPercentage = true))
        val notRequested = map(event(), binding(showPercentage = false))

        assertThat(requested.showPercentageText).isTrue()
        assertThat(notRequested.showPercentageText).isFalse()
    }

    @Test
    fun `percent text still shows when progress itself is off, if the binding asks for it`() {
        // Percentage and the progress graphic are two independent choices, not one conjoined
        // toggle: a user can ask to see "42%" as plain text with no bar or ring drawn at all.
        val model = map(
            event(defaultProgress = ProgressStyle.NONE),
            binding(showPercentage = true),
        )

        assertThat(model.progress.isVisible).isFalse()
        assertThat(model.showPercentageText).isTrue()
    }

    // ── Style × Progress independence (Samsung Galaxy A55 physical-device finding) ──

    @Test
    fun `changing style does not change the resolved progress style`() {
        val minimal = map(
            event(defaultProgress = ProgressStyle.CIRCULAR),
            binding(styleOverride = WidgetStyle.MINIMAL),
        )
        val oled = map(
            event(defaultProgress = ProgressStyle.CIRCULAR),
            binding(styleOverride = WidgetStyle.OLED),
        )

        assertThat(minimal.progress.style).isEqualTo(ProgressStyle.CIRCULAR)
        assertThat(oled.progress.style).isEqualTo(ProgressStyle.CIRCULAR)
    }

    @Test
    fun `changing progress style does not change the resolved theme style`() {
        val none = map(
            event(defaultStyle = WidgetStyle.MODERN),
            binding(progressOverride = ProgressStyle.NONE),
        )
        val circular = map(
            event(defaultStyle = WidgetStyle.MODERN),
            binding(progressOverride = ProgressStyle.CIRCULAR),
        )

        assertThat(none.theme.style).isEqualTo(WidgetStyle.MODERN)
        assertThat(circular.theme.style).isEqualTo(WidgetStyle.MODERN)
    }

    // ── Legacy WidgetStyle.PROGRESS: no longer selectable, but a binding written before this
    // change must still render safely and look the same as it always did — see WidgetStyle's own
    // KDoc and WidgetRenderMapper.map's normalization. ──

    @Test
    fun `a legacy PROGRESS style binding resolves to the default theme style`() {
        val model = map(
            event(defaultStyle = WidgetStyle.PROGRESS, defaultProgress = ProgressStyle.LINEAR),
            binding(),
        )

        assertThat(model.theme.style).isEqualTo(WidgetStyle.Default)
    }

    @Test
    fun `a legacy PROGRESS style binding forces a ring, matching what it always rendered`() {
        // Every ProgressLayout* composable always drew a ring whenever progress was visible at
        // all, regardless of whether the binding's own progress field said Linear or Circular.
        val linear = map(
            event(defaultStyle = WidgetStyle.PROGRESS, defaultProgress = ProgressStyle.LINEAR),
            binding(),
        )
        val circular = map(
            event(defaultStyle = WidgetStyle.PROGRESS, defaultProgress = ProgressStyle.CIRCULAR),
            binding(),
        )

        assertThat(linear.progress.style).isEqualTo(ProgressStyle.CIRCULAR)
        assertThat(circular.progress.style).isEqualTo(ProgressStyle.CIRCULAR)
    }

    @Test
    fun `a legacy PROGRESS style binding with progress none stays none, never crashing`() {
        // The one part of the old rendering that already respected the progress field.
        val model = map(
            event(defaultStyle = WidgetStyle.PROGRESS, defaultProgress = ProgressStyle.NONE),
            binding(),
        )

        assertThat(model.theme.style).isEqualTo(WidgetStyle.Default)
        assertThat(model.progress.style).isEqualTo(ProgressStyle.NONE)
        assertThat(model.progress.isVisible).isFalse()
    }
}
