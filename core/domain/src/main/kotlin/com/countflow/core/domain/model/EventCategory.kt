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
