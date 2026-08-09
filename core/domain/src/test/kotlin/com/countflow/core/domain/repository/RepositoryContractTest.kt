package com.countflow.core.domain.repository

import com.countflow.core.domain.countdown.CountdownStatus
import com.countflow.core.domain.countdown.CountdownTotals
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.model.WidgetStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Defaults and derived properties on the domain's contract types.
 *
 * These look trivial, and they are the kind of thing that is usually left untested — but a
 * default is behaviour. If [EventFilter.Default] silently started including archived events,
 * every list in the app would change and nothing would fail. Pinning them here means such a
 * change has to be deliberate.
 */
class RepositoryContractTest {

    @Test
    fun `the default event filter shows only upcoming events`() {
        val filter = EventFilter.Default

        assertThat(filter.query).isEmpty()
        assertThat(filter.categories).isEmpty()
        assertThat(filter.lifecycle).isEqualTo(EventLifecycleFilter.UPCOMING)
    }

    @Test
    fun `a filter narrowed to categories keeps its other defaults`() {
        val filter = EventFilter.Default.copy(
            query = "birthday",
            categories = setOf(EventCategory.BIRTHDAY),
        )

        assertThat(filter.categories).containsExactly(EventCategory.BIRTHDAY)
        assertThat(filter.lifecycle).isEqualTo(EventLifecycleFilter.UPCOMING)
    }

    @Test
    fun `events sort by target date unless told otherwise`() {
        // A countdown list is about what is coming next; any other default would be surprising.
        assertThat(EventSort.Default).isEqualTo(EventSort.TARGET_DATE)
        assertThat(EventSort.entries).hasSize(4)
    }

    @Test
    fun `the theme follows the system by default`() {
        assertThat(ThemeMode.Default).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `a fresh install starts with dynamic colour on and premium off`() {
        val defaults = UserPreferences.Default

        assertThat(defaults.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(defaults.useDynamicColor).isTrue()
        assertThat(defaults.defaultWidgetStyle).isEqualTo(WidgetStyle.MINIMAL)
        assertThat(defaults.defaultProgressStyle).isEqualTo(ProgressStyle.LINEAR)
        assertThat(defaults.defaultEventSort).isEqualTo(EventSort.TARGET_DATE)
        assertThat(defaults.isPremium).isFalse()
        assertThat(defaults.lastWidgetUpdateEpochMillis).isNull()
    }

    @Test
    fun `the default widget style is one a non-premium user can actually use`() {
        // Shipping a premium default would gate every new event behind a paywall on install.
        assertThat(UserPreferences.Default.defaultWidgetStyle.isPremium).isFalse()
    }

    @Test
    fun `preferences can be copied field by field`() {
        val changed = UserPreferences.Default.copy(
            themeMode = ThemeMode.DARK,
            isPremium = true,
            lastWidgetUpdateEpochMillis = 1_000L,
        )

        assertThat(changed.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(changed.isPremium).isTrue()
        assertThat(changed.lastWidgetUpdateEpochMillis).isEqualTo(1_000L)
        assertThat(changed.useDynamicColor).isTrue()
    }

    // ------------------------------------------------------------ derived properties

    @Test
    fun `only expired and completed count as past`() {
        assertThat(CountdownStatus.EXPIRED.isPast).isTrue()
        assertThat(CountdownStatus.COMPLETED.isPast).isTrue()
        assertThat(CountdownStatus.UPCOMING.isPast).isFalse()
        assertThat(CountdownStatus.TODAY.isPast).isFalse()
        assertThat(CountdownStatus.IMMINENT.isPast).isFalse()
    }

    @Test
    fun `only imminent needs live ticking`() {
        // This drives whether a widget embeds a launcher-ticked chronometer, so a change here
        // has a direct battery cost.
        val ticking = CountdownStatus.entries.filter { it.needsLiveTicking }

        assertThat(ticking).containsExactly(CountdownStatus.IMMINENT)
    }

    @Test
    fun `a bound widget pairs a binding with its event`() {
        val event = com.countflow.core.domain.testing.Fixtures.timedEvent(
            at = com.countflow.core.domain.testing.Fixtures.dateTime("2026-06-15T12:00"),
        )
        val binding = com.countflow.core.domain.model.WidgetBinding.inheriting(
            appWidgetId = com.countflow.core.domain.model.AppWidgetId(1),
            eventId = event.id,
            createdAt = java.time.Instant.EPOCH,
        )

        val bound = BoundWidget(binding = binding, event = event)

        assertThat(bound.binding.eventId).isEqualTo(bound.event.id)
    }

    @Test
    fun `identifiers print as their bare value`() {
        // Value classes are logged and used as map keys; a wrapped toString would leak
        // "EventId(value=…)" into log lines and widget state keys.
        assertThat(com.countflow.core.domain.model.EventId("abc").toString()).isEqualTo("abc")
        assertThat(com.countflow.core.domain.model.ReminderId("xyz").toString()).isEqualTo("xyz")
    }

    @Test
    fun `zero totals and a fixed accent are constructible`() {
        assertThat(CountdownTotals.Zero.totalSeconds).isEqualTo(0)
        assertThat(AccentColor.Fixed(0x11223344).argb).isEqualTo(0x11223344)
        assertThat(ReminderType.suggested).containsExactly(
            ReminderType.DAY_OF,
            ReminderType.ONE_DAY,
            ReminderType.SEVEN_DAYS,
        ).inOrder()
    }
}
