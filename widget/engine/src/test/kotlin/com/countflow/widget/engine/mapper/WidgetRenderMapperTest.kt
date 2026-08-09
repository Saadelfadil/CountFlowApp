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
        styleOverride: WidgetStyle? = null,
        progressOverride: ProgressStyle? = null,
        showTitle: Boolean = true,
        showEmoji: Boolean = true,
        showDate: Boolean = false,
        showPercentage: Boolean = false,
    ) = WidgetBinding.inheriting(AppWidgetId(1), eventId, Instant.EPOCH).copy(
        widgetStyleOverride = styleOverride,
        progressStyleOverride = progressOverride,
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

    @Test
    fun `percent text is shown only when the binding asks for it and progress is visible`() {
        val requested = map(event(), binding(showPercentage = true))
        val notRequested = map(event(), binding(showPercentage = false))

        assertThat(requested.showPercentageText).isTrue()
        assertThat(notRequested.showPercentageText).isFalse()
    }

    @Test
    fun `percent text is never shown when progress itself is off, even if requested`() {
        // Asking for the number next to a bar that will not be drawn at all is a state the
        // renderer should never have to reason about — the mapper conjoins the two so it cannot
        // arise from real data.
        val model = map(
            event(defaultProgress = ProgressStyle.NONE),
            binding(showPercentage = true),
        )

        assertThat(model.progress.isVisible).isFalse()
        assertThat(model.showPercentageText).isFalse()
    }
}
