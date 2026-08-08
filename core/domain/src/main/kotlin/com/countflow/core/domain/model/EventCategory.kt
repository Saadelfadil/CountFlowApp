package com.countflow.core.domain.model

/**
 * The kind of occasion an [Event] represents.
 *
 * A closed enum rather than a free-text field: categories drive filtering, sorting, and default
 * emoji, all of which need a known finite set. User-defined categories, if they are ever wanted,
 * would be an additive change — a `Custom(name)` case on a sealed type — and would not
 * invalidate stored data, because persistence writes the enum name rather than its ordinal.
 */
enum class EventCategory {
    GENERAL,
    BIRTHDAY,
    HOLIDAY,
    TRAVEL,
    WORK,
    EDUCATION,
    HEALTH,
    FINANCE,
    ENTERTAINMENT,
    RELATIONSHIP,
    ;

    companion object {
        /** The category assigned when the user does not choose one. */
        val Default: EventCategory = GENERAL
    }
}

/**
 * The emoji shown when an event has none of its own.
 *
 * A per-category default rather than a single generic glyph: a list of identical placeholder
 * icons is harder to scan than one where holidays and birthdays look different at a glance.
 *
 * Lives in the domain, not a UI mapper, because both the app's event list and the widget
 * renderer need the same fallback. An emoji is a fixed Unicode glyph, not translated text, so
 * unlike [CountdownLabel] it carries no localisation concern that would argue for resolving it
 * only at composition — it is safe, plain data.
 */
val EventCategory.defaultEmoji: String
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
