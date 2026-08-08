package com.countflow.core.domain.countdown

import java.time.Duration

/**
 * A countdown broken into calendar units: "1 year, 2 months, 3 days, 4 hours…".
 *
 * These are *remainders*, not totals — a countdown of 400 days is one year, one month, and some
 * days, so [days] is small even though the countdown is long. For totals, use
 * [CountdownTotals]. Confusing the two is the classic countdown bug, which is why they are
 * separate types with separate names rather than fifteen loose fields.
 *
 * All values are non-negative; direction is carried by [CountdownResult.isPast].
 */
data class CountdownBreakdown(
    val years: Int,
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    companion object {
        /** Everything zero, used when the target is exactly now. */
        val Zero: CountdownBreakdown = CountdownBreakdown(0, 0, 0, 0, 0, 0)
    }
}

/**
 * The same countdown expressed as a total in each unit.
 *
 * Each field is the whole count of that unit in the entire span, so they are not additive:
 * a two-day countdown is `totalDays = 2` *and* `totalHours = 48`.
 *
 * These are elapsed-time totals derived from a [Duration]. For the number a countdown widget
 * should display — how many midnights away the target is — use
 * [CountdownResult.calendarDaysRemaining] instead, which is a calendar comparison and can
 * differ from [totalDays] by one.
 */
data class CountdownTotals(
    val totalSeconds: Long,
    val totalMinutes: Long,
    val totalHours: Long,
    val totalDays: Long,
    val totalWeeks: Long,
) {
    companion object {
        /** Everything zero. */
        val Zero: CountdownTotals = CountdownTotals(0, 0, 0, 0, 0)
    }
}

/**
 * Everything the app knows about where an event sits in time, computed at one instant.
 *
 * Immutable and self-contained: a widget can be handed one of these and render without touching
 * the clock, the database, or the event itself.
 *
 * @property status the coarse state, which drives behaviour.
 * @property label the display token, which drives text.
 * @property isPast whether the target is behind us.
 * @property breakdown calendar-unit remainders for the gap between now and the target, in
 *   whichever direction that is.
 * @property totals whole-unit totals for that same gap.
 * @property calendarDaysRemaining midnights between today and the target date, signed —
 *   negative once the target has passed. **This is the number a countdown should display**,
 *   because "5 days away" means five sleeps, not 120 hours.
 * @property percentComplete progress from the event's creation to its target, clamped to
 *   `0f..1f`. An event created after its own target reads as fully complete.
 * @property elapsed time since the event was created. Never negative.
 * @property remaining time until the event starts. Zero once it has started — an all-day event
 *   happening right now reports zero rather than the hours since midnight. For how far a past
 *   event is behind us, read [breakdown] or [calendarDaysRemaining], which are signed or
 *   direction-agnostic as appropriate.
 */
data class CountdownResult(
    val status: CountdownStatus,
    val label: CountdownLabel,
    val isPast: Boolean,
    val breakdown: CountdownBreakdown,
    val totals: CountdownTotals,
    val calendarDaysRemaining: Long,
    val percentComplete: Float,
    val elapsed: Duration,
    val remaining: Duration,
) {
    /** Progress as a whole percentage, `0..100`. Convenient for display and for cache keys. */
    val percentCompleteWhole: Int get() = (percentComplete * 100).toInt().coerceIn(0, 100)
}
