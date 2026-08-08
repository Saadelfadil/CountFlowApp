package com.countflow.core.domain.countdown

import java.time.DayOfWeek
import java.time.Duration

/**
 * The thresholds that decide which [CountdownLabel] and [CountdownStatus] apply.
 *
 * These are product policy, not arithmetic, so they are data rather than constants buried in
 * [CountdownEngine]. That makes each boundary testable by construction — a test can shrink
 * [imminentThreshold] to a second instead of manipulating the clock — and lets the app localise
 * [weekStartsOn] without the engine knowing about `Locale`.
 *
 * @property imminentThreshold how close a target must be to count as
 *   [CountdownStatus.IMMINENT]. One hour by default: near enough that minutes matter, far
 *   enough that it does not trigger during a normal day's browsing.
 * @property weekStartsOn which day begins a week, used to decide [CountdownLabel.NextWeek].
 *   Defaults to the ISO-8601 Monday; the app should pass the device locale's first day instead.
 * @property recentPastDays how many days back still get a [CountdownLabel.DaysAgo] count before
 *   falling through to [CountdownLabel.Expired].
 * @property nearFutureDays the largest day count that gets a plain [CountdownLabel.InDays]
 *   before "next week" is considered.
 */
data class CountdownConfig(
    val imminentThreshold: Duration = Duration.ofHours(1),
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val recentPastDays: Int = 7,
    val nearFutureDays: Int = 6,
) {
    init {
        require(!imminentThreshold.isNegative) { "imminentThreshold must not be negative" }
        require(recentPastDays >= 1) { "recentPastDays must be at least 1" }
        require(nearFutureDays >= 1) { "nearFutureDays must be at least 1" }
    }

    companion object {
        /** The default policy. */
        val Default: CountdownConfig = CountdownConfig()
    }
}
