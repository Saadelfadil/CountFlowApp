package com.countflow.core.domain.countdown

/**
 * The friendly description of a countdown, as a token rather than a string.
 *
 * **The domain deliberately does not produce display text.** Returning `"Tomorrow"` from here
 * would hard-code English and bypass Android's resource system, breaking both localisation and
 * plural rules — "in 1 day" versus "in 2 days" is not a concatenation problem in every language.
 * The UI and widget layers map these tokens to string resources.
 *
 * Which token applies is decided by [CountdownEngine] using the thresholds in [CountdownConfig].
 */
sealed interface CountdownLabel {

    /** The user marked the event done. */
    data object Completed : CountdownLabel

    /** The target has passed — either earlier today, or longer ago than [DaysAgo] covers. */
    data object Expired : CountdownLabel

    /** Within the imminent threshold; render a live ticking value. */
    data object StartingSoon : CountdownLabel

    /** Falls on today's date. */
    data object Today : CountdownLabel

    /** Falls on tomorrow's date. */
    data object Tomorrow : CountdownLabel

    /** Fell on yesterday's date. */
    data object Yesterday : CountdownLabel

    /** Falls somewhere in the next calendar week, as defined by [CountdownConfig.weekStartsOn]. */
    data object NextWeek : CountdownLabel

    /**
     * A plain count of days ahead.
     *
     * @property days whole calendar days until the target. Always two or more; nearer values
     *   have their own tokens.
     */
    data class InDays(val days: Int) : CountdownLabel

    /**
     * A plain count of days behind.
     *
     * @property days whole calendar days since the target. Always two or more; [Yesterday]
     *   covers one and [Expired] covers anything older than
     *   [CountdownConfig.recentPastDays].
     */
    data class DaysAgo(val days: Int) : CountdownLabel
}
