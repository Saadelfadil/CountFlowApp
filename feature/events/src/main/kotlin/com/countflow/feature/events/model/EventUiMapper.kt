package com.countflow.feature.events.model

import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.countdown.CountdownResult
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.ProgressStyle
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Builds [EventCardUiModel]s from domain events.
 *
 * The one place a domain object becomes something Compose can draw. Keeping it here — injectable
 * and pure — means the list's presentation rules are unit-testable without a device, and means a
 * screen cannot quietly grow its own copy of them.
 *
 * [mapAll] exists separately from [map] because a list must be rendered against a single "now".
 * Mapping each row against its own `clock.instant()` would let the first and last rows in a long
 * list disagree about what day it is — rare, but it produces a list where two identical events
 * show different day counts, which looks like data corruption.
 */
@Singleton
class EventUiMapper @Inject constructor(
    private val countdownEngine: CountdownEngine,
) {

    /** Maps [events] against one shared instant. */
    fun mapAll(
        events: List<Event>,
        now: Instant,
        zone: ZoneId,
    ): List<EventCardUiModel> = events.map { map(it, now, zone) }

    /** Maps a single event. */
    fun map(event: Event, now: Instant, zone: ZoneId): EventCardUiModel {
        val countdown = countdownEngine.countdownAt(event, now, zone)
        return EventCardUiModel(
            id = event.id.value,
            title = event.title,
            emoji = event.emoji ?: event.category.defaultEmoji,
            category = event.category,
            label = countdown.label,
            daysValue = countdown.calendarDaysRemaining.absoluteValue.toInt(),
            showDaysValue = countdown.showsDayCount(),
            progress = countdown.percentComplete,
            progressPercent = countdown.percentCompleteWhole,
            showProgress = event.defaultProgressStyle != ProgressStyle.NONE,
            accentArgb = (event.accentColor as? AccentColor.Fixed)?.argb,
            isPast = countdown.isPast,
            isCompleted = event.isCompleted,
            isArchived = event.isArchived,
        )
    }

    /**
     * Whether the headline number adds anything beyond the label.
     *
     * "1" next to "Tomorrow" is noise, and "0" next to "Today" is worse — it reads as an error.
     * Only counts of two or more earn the large numeral.
     */
    private fun CountdownResult.showsDayCount(): Boolean = when (label) {
        CountdownLabel.Today,
        CountdownLabel.Tomorrow,
        CountdownLabel.Yesterday,
        CountdownLabel.StartingSoon,
        CountdownLabel.Completed,
        CountdownLabel.Expired,
        -> false

        else -> calendarDaysRemaining.absoluteValue >= MIN_DAYS_WORTH_SHOWING
    }

    private companion object {
        const val MIN_DAYS_WORTH_SHOWING = 2
    }
}

/**
 * The emoji shown when an event has none of its own.
 *
 * A per-category default rather than a single generic glyph: a list of identical placeholder
 * icons is harder to scan than one where holidays and birthdays look different at a glance.
 */
internal val EventCategory.defaultEmoji: String
    get() = when (this) {
        EventCategory.GENERAL -> "📅"
        EventCategory.BIRTHDAY -> "🎂"
        EventCategory.HOLIDAY -> "🎄"
        EventCategory.TRAVEL -> "✈️"
        EventCategory.WORK -> "💼"
        EventCategory.EDUCATION -> "🎓"
        EventCategory.HEALTH -> "🩺"
        EventCategory.FINANCE -> "💰"
        EventCategory.ENTERTAINMENT -> "🎮"
        EventCategory.RELATIONSHIP -> "💛"
    }
