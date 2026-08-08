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

/**
 * Whether a headline day count adds anything beyond [CountdownResult.label] on its own.
 *
 * "1" next to "Tomorrow" is noise, and "0" next to "Today" reads as an error. Every label other
 * than the six near-term tokens already implies a count of two or more by construction — see
 * [CountdownLabel.InDays] and [CountdownLabel.DaysAgo]'s own documentation — so this is a pure
 * function of which label applies, not a second threshold check that could drift from the one
 * [CountdownEngine] already used to choose the label.
 *
 * Shared by the app's event list and the widget renderer, which is why it lives beside the label
 * type itself rather than inside either consumer's mapper.
 */
val CountdownResult.showsMeaningfulDayCount: Boolean
    get() = when (label) {
        CountdownLabel.Today,
        CountdownLabel.Tomorrow,
        CountdownLabel.Yesterday,
        CountdownLabel.StartingSoon,
        CountdownLabel.Completed,
        CountdownLabel.Expired,
        -> false

        CountdownLabel.NextWeek,
        is CountdownLabel.InDays,
        is CountdownLabel.DaysAgo,
        -> true
    }
