package com.countflow.core.domain.countdown

/**
 * The coarse state of a countdown.
 *
 * Distinct from [CountdownLabel], which is what gets shown. Status drives behaviour — how often
 * a widget refreshes, whether reminders still matter, which section of the list an event sorts
 * into — while the label drives text. Keeping them separate means changing the wording of
 * "Tomorrow" cannot accidentally change refresh scheduling.
 *
 * Exactly one status applies at a time, resolved in the order the constants are declared.
 */
enum class CountdownStatus {

    /** The user marked this done. Takes precedence over everything, including a passed target. */
    COMPLETED,

    /** The target has passed and the user has not marked it complete. */
    EXPIRED,

    /** Close enough to warrant second-level precision. See [CountdownConfig.imminentThreshold]. */
    IMMINENT,

    /** Falls on today's date but is not yet imminent. */
    TODAY,

    /** Still ahead. */
    UPCOMING,
    ;

    /** Whether the target instant is behind us. */
    val isPast: Boolean get() = this == EXPIRED || this == COMPLETED

    /**
     * Whether a widget showing this needs sub-minute refresh.
     *
     * Only [IMMINENT] does, and that case is served by a launcher-ticked `Chronometer` rather
     * than by waking the app — see DECISIONS.md (D-008).
     */
    val needsLiveTicking: Boolean get() = this == IMMINENT
}
