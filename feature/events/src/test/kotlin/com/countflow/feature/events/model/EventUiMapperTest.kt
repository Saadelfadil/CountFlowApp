package com.countflow.feature.events.model

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.ProgressStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Presentation rules for the event list.
 *
 * These are the decisions that would otherwise drift into composables, where they are invisible
 * to tests: which emoji to fall back to, and when the headline number is worth showing at all.
 */
class EventUiMapperTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    private val mapper = EventUiMapper(
        CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default),
    )

    private fun event(
        daysAhead: Long,
        emoji: String? = null,
        category: EventCategory = EventCategory.GENERAL,
        progressStyle: ProgressStyle = ProgressStyle.LINEAR,
        accent: AccentColor = AccentColor.Dynamic,
    ) = Event.create(
        id = EventId("e"),
        title = "Event",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15).plusDays(daysAhead), zone),
        createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        emoji = emoji,
        category = category,
        defaultProgressStyle = progressStyle,
        accentColor = accent,
    )

    private fun map(event: Event) = mapper.map(event, now, zone)

    @Test
    fun `an event without an emoji falls back to its category default`() {
        // A list of identical placeholder glyphs is harder to scan than one where a birthday
        // and a flight look different.
        assertThat(map(event(daysAhead = 5, category = EventCategory.BIRTHDAY)).emoji)
            .isEqualTo("🎂")
        assertThat(map(event(daysAhead = 5, category = EventCategory.TRAVEL)).emoji)
            .isEqualTo("✈️")
    }

    @Test
    fun `an event's own emoji wins over the category default`() {
        assertThat(map(event(daysAhead = 5, emoji = "🚀", category = EventCategory.WORK)).emoji)
            .isEqualTo("🚀")
    }

    @Test
    fun `the headline number is hidden when the label already says it`() {
        // "1" beside "Tomorrow" is noise; "0" beside "Today" reads as an error.
        assertThat(map(event(daysAhead = 0)).showDaysValue).isFalse()
        assertThat(map(event(daysAhead = 1)).showDaysValue).isFalse()
        assertThat(map(event(daysAhead = -1)).showDaysValue).isFalse()
    }

    @Test
    fun `the headline number is shown from two days out`() {
        val card = map(event(daysAhead = 12))

        assertThat(card.showDaysValue).isTrue()
        assertThat(card.daysValue).isEqualTo(12)
    }

    @Test
    fun `the headline number is unsigned for past events`() {
        // The label carries the direction; a minus sign in the big numeral looks like a bug.
        val card = map(event(daysAhead = -5))

        assertThat(card.daysValue).isEqualTo(5)
        assertThat(card.isPast).isTrue()
    }

    @Test
    fun `the label token is carried through rather than resolved to text`() {
        // Resolving here would freeze the row in whatever locale was active when the flow
        // last emitted.
        assertThat(map(event(daysAhead = 1)).label).isEqualTo(CountdownLabel.Tomorrow)
        assertThat(map(event(daysAhead = 0)).label).isEqualTo(CountdownLabel.Today)
        assertThat(map(event(daysAhead = 30)).label).isEqualTo(CountdownLabel.InDays(30))
    }

    @Test
    fun `progress is hidden when the event's style asks for none`() {
        assertThat(map(event(daysAhead = 5, progressStyle = ProgressStyle.NONE)).showProgress)
            .isFalse()
        assertThat(map(event(daysAhead = 5, progressStyle = ProgressStyle.CIRCULAR)).showProgress)
            .isTrue()
    }

    @Test
    fun `a dynamic accent maps to null so the theme decides`() {
        assertThat(map(event(daysAhead = 5, accent = AccentColor.Dynamic)).accentArgb).isNull()
        assertThat(map(event(daysAhead = 5, accent = AccentColor.Fixed(0xFF00695C.toInt()))).accentArgb)
            .isEqualTo(0xFF00695C.toInt())
    }

    @Test
    fun `mapping a list uses one shared instant`() {
        // Every row must agree about what day it is. Mapping each against its own clock read
        // could give two identical events different day counts, which looks like corruption.
        val events = List(50) { event(daysAhead = 10) }

        val cards = mapper.mapAll(events, now, zone)

        assertThat(cards.map { it.daysValue }.distinct()).containsExactly(10)
    }

    @Test
    fun `completed and archived flags survive mapping`() {
        val source = event(daysAhead = 5).copy(isCompleted = true, isArchived = true)

        val card = map(source)

        assertThat(card.isCompleted).isTrue()
        assertThat(card.isArchived).isTrue()
        assertThat(card.label).isEqualTo(CountdownLabel.Completed)
    }
}
