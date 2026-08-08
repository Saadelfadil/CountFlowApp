package com.countflow.core.designsystem.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.countflow.core.designsystem.R
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.EventCategory

/**
 * Turns domain tokens into text.
 *
 * This is the boundary the domain deliberately stops short of. [CountdownLabel] carries *which*
 * label applies; this decides what that says in the user's language, using plural resources so
 * "in 1 day" and "in 2 days" are separate strings rather than a number glued to a fixed noun.
 *
 * Exposed twice on purpose. The [Resources] overload is the real implementation and is what the
 * widget layer will call, since Glance renders outside a Compose resource scope. The composable
 * wrapper reads through `LocalConfiguration` so text re-resolves when the user changes language
 * or font scale without the screen having to know it should.
 */
object CountdownLabelFormatter {

    /**
     * Formats [label] using [resources].
     *
     * @param resources the resources to read from, which determine the language.
     * @param label the token to render.
     */
    fun format(resources: Resources, label: CountdownLabel): String = when (label) {
        CountdownLabel.Today -> resources.getString(R.string.countdown_today)
        CountdownLabel.Tomorrow -> resources.getString(R.string.countdown_tomorrow)
        CountdownLabel.Yesterday -> resources.getString(R.string.countdown_yesterday)
        CountdownLabel.NextWeek -> resources.getString(R.string.countdown_next_week)
        CountdownLabel.StartingSoon -> resources.getString(R.string.countdown_starting_soon)
        CountdownLabel.Completed -> resources.getString(R.string.countdown_completed)
        CountdownLabel.Expired -> resources.getString(R.string.countdown_expired)

        is CountdownLabel.InDays ->
            resources.getQuantityString(R.plurals.countdown_in_days, label.days, label.days)

        is CountdownLabel.DaysAgo ->
            resources.getQuantityString(R.plurals.countdown_days_ago, label.days, label.days)
    }

    /** The display name of [category]. */
    fun format(resources: Resources, category: EventCategory): String =
        resources.getString(category.nameRes)
}

/**
 * The text for [label], re-resolved when the configuration changes.
 *
 * Reading [LocalConfiguration] is what makes that happen: without it Compose has no reason to
 * recompose this call when the user switches language in Settings, and the screen would keep
 * showing the old locale's text until it was rebuilt for some other reason.
 */
@Composable
@ReadOnlyComposable
fun CountdownLabel.asText(): String {
    LocalConfiguration.current
    return CountdownLabelFormatter.format(LocalContext.current.resources, this)
}

/** The display name of this category, re-resolved when the configuration changes. */
@Composable
@ReadOnlyComposable
fun EventCategory.asText(): String {
    LocalConfiguration.current
    return LocalContext.current.resources.getString(nameRes)
}

/** The string resource naming this category. */
internal val EventCategory.nameRes: Int
    get() = when (this) {
        EventCategory.GENERAL -> R.string.category_general
        EventCategory.BIRTHDAY -> R.string.category_birthday
        EventCategory.HOLIDAY -> R.string.category_holiday
        EventCategory.TRAVEL -> R.string.category_travel
        EventCategory.WORK -> R.string.category_work
        EventCategory.EDUCATION -> R.string.category_education
        EventCategory.HEALTH -> R.string.category_health
        EventCategory.FINANCE -> R.string.category_finance
        EventCategory.ENTERTAINMENT -> R.string.category_entertainment
        EventCategory.RELATIONSHIP -> R.string.category_relationship
    }
