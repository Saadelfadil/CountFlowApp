package com.countflow.core.designsystem.format

import android.content.res.Resources
import com.countflow.core.domain.model.EventTarget
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Turns an [EventTarget] into the calendar date or clock time a user actually reads — never
 * hard-coded English, the same principle [CountdownLabelFormatter] applies to countdown tokens.
 *
 * `java.time`'s `DateTimeFormatter.ofLocalizedDate`/`ofLocalizedTime` are already locale-aware
 * once given a real [java.util.Locale] — unlike [CountdownLabelFormatter], this needs no Android
 * string resources of its own, only [Resources] to read the active locale from.
 *
 * @see EventTarget.startAt for why the device's current zone, not the event's authored zone, is
 *   the correct one to resolve display text in — the same distinction that makes an all-day
 *   target's *date* stable while a timed target's *zone* is not.
 */
object TargetDateFormatter {

    /** The clock time [target] resolves to in [deviceZone], for example "8:00 AM". */
    fun formatTime(resources: Resources, target: EventTarget, deviceZone: ZoneId): String {
        val locale = resources.configuration.locales[0]
        val time = target.startAt(deviceZone).toLocalTime()
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)
    }

    /** The calendar date [target] resolves to in [deviceZone], for example "Mar 15, 2027". */
    fun formatDate(resources: Resources, target: EventTarget, deviceZone: ZoneId): String {
        val locale = resources.configuration.locales[0]
        val date = target.startAt(deviceZone).toLocalDate()
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    }
}
