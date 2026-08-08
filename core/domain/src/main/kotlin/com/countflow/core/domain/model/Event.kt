package com.countflow.core.domain.model

import java.time.Instant

/**
 * A countdown the user has created.
 *
 * Immutable. Edits produce a new instance through [copy], which keeps the repository's `Flow`
 * emissions distinguishable and makes the model safe to hand to a widget rendering on another
 * thread.
 *
 * Note what this class does *not* carry: the widget style used by any particular widget. An
 * event holds only [defaultWidgetStyle] and [defaultProgressStyle], which a newly placed widget
 * inherits. The style actually rendered lives on [WidgetBinding], because one event can appear
 * as several widgets and the point of that is showing it different ways (DECISIONS.md D-013).
 *
 * @property id stable identifier, generated once at creation.
 * @property title what the user is counting down to. Never blank.
 * @property emoji a single emoji shown alongside the title, or null.
 * @property iconKey identifier for a built-in vector icon, or null. Resolved to a drawable in
 *   the UI layer; the domain never references resources.
 * @property category used for filtering and for choosing a default emoji.
 * @property target when the event happens. See [EventTarget] for the timed/all-day distinction.
 * @property createdAt when the event was created. This is the origin for progress: percentage
 *   complete is measured from here to the target.
 * @property accentColor the event's colour, or [AccentColor.Dynamic] to follow the wallpaper.
 * @property defaultWidgetStyle inherited by widgets newly bound to this event.
 * @property defaultProgressStyle inherited by widgets newly bound to this event.
 * @property remindersEnabled master switch for this event's reminders. Individual [Reminder]
 *   rows carry their own enabled state; both must be true for a reminder to fire.
 * @property isArchived hidden from the main list but not deleted, and its widgets keep working.
 * @property isCompleted the user has marked this done. Takes precedence over the target date
 *   when deriving a countdown status, so a finished event does not read as "expired".
 */
data class Event(
    val id: EventId,
    val title: String,
    val emoji: String?,
    val iconKey: String?,
    val category: EventCategory,
    val target: EventTarget,
    val createdAt: Instant,
    val accentColor: AccentColor,
    val defaultWidgetStyle: WidgetStyle,
    val defaultProgressStyle: ProgressStyle,
    val remindersEnabled: Boolean,
    val isArchived: Boolean,
    val isCompleted: Boolean,
) {
    init {
        require(title.isNotBlank()) { "Event title must not be blank" }
    }

    companion object {

        /** Longest title accepted, enforced by the UI before construction. */
        const val MAX_TITLE_LENGTH: Int = 120

        /**
         * Creates an event with the defaults a new one should have.
         *
         * Keeps callers from having to restate seven default values, and keeps those defaults in
         * one place rather than spread across the create screen and the tests.
         */
        fun create(
            title: String,
            target: EventTarget,
            createdAt: Instant,
            id: EventId = EventId.random(),
            emoji: String? = null,
            iconKey: String? = null,
            category: EventCategory = EventCategory.Default,
            accentColor: AccentColor = AccentColor.Default,
            defaultWidgetStyle: WidgetStyle = WidgetStyle.Default,
            defaultProgressStyle: ProgressStyle = ProgressStyle.Default,
            remindersEnabled: Boolean = false,
        ): Event = Event(
            id = id,
            title = title,
            emoji = emoji,
            iconKey = iconKey,
            category = category,
            target = target,
            createdAt = createdAt,
            accentColor = accentColor,
            defaultWidgetStyle = defaultWidgetStyle,
            defaultProgressStyle = defaultProgressStyle,
            remindersEnabled = remindersEnabled,
            isArchived = false,
            isCompleted = false,
        )
    }
}
