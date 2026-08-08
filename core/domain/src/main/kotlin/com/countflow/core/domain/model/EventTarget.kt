package com.countflow.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When an event happens.
 *
 * Stored as an instant plus the zone it was authored in, never as a naive `LocalDateTime`
 * (DECISIONS.md D-014). The two kinds of target behave differently on purpose, and the
 * difference is the whole reason this type exists:
 *
 * - **Timed** ("my flight at 14:05") is an instant pinned to [zoneId]. It resolves against the
 *   stored zone, so a flight from Tokyo stays at Tokyo 14:05 no matter where the phone is.
 * - **All-day** ("New Year's Eve") is a *date*, not an instant. It resolves against whatever
 *   zone the device is in now, so midnight happens locally wherever the user has travelled to.
 *
 * For an all-day target, [epochMillis] is the start of [zoneId]'s day. Reading the date back
 * through the stored zone recovers the authored calendar date exactly, and that date is then
 * re-resolved in the device's current zone.
 *
 * @property epochMillis the instant, in milliseconds since the Unix epoch.
 * @property zoneId IANA identifier of the zone the event was authored in, for example
 *   `Europe/London`. Not a fixed offset — offsets change across DST boundaries, zones do not.
 * @property isAllDay whether this target denotes a whole calendar day rather than an instant.
 */
data class EventTarget(
    val epochMillis: Long,
    val zoneId: String,
    val isAllDay: Boolean,
) {

    /** The authored zone. */
    val zone: ZoneId get() = ZoneId.of(zoneId)

    /** The instant as authored. For all-day targets this is midnight in the authored zone. */
    val instant: Instant get() = Instant.ofEpochMilli(epochMillis)

    /**
     * The calendar date this target falls on, as the user authored it.
     *
     * Always read through the authored zone, because that is the only zone in which the stored
     * instant means the date the user picked.
     */
    fun authoredDate(): LocalDate = instant.atZone(zone).toLocalDate()

    /**
     * When the event begins, resolved for a device currently in [deviceZone].
     *
     * All-day targets re-resolve to local midnight; timed targets keep their authored instant
     * and are merely *expressed* in the device zone.
     */
    fun startAt(deviceZone: ZoneId): ZonedDateTime = when {
        // atStartOfDay(zone) is DST-safe: on a day where midnight does not exist because the
        // clocks sprang forward, it returns the first instant that does.
        isAllDay -> authoredDate().atStartOfDay(deviceZone)
        else -> instant.atZone(deviceZone)
    }

    /**
     * The exclusive instant at which the event is over, for a device in [deviceZone].
     *
     * An all-day event is not finished the moment its day starts — it runs until the next
     * midnight. A timed event ends when it begins. Getting this wrong would mark "Christmas Day"
     * as expired at one minute past midnight on Christmas morning.
     */
    fun endAt(deviceZone: ZoneId): ZonedDateTime = when {
        isAllDay -> authoredDate().plusDays(1).atStartOfDay(deviceZone)
        else -> instant.atZone(deviceZone)
    }

    companion object {

        /**
         * Creates an all-day target on [date], authored in [zone].
         *
         * @param date the calendar date of the event.
         * @param zone the zone the user is authoring in; recorded but not used to resolve the
         *   date later.
         */
        fun allDay(date: LocalDate, zone: ZoneId): EventTarget = EventTarget(
            epochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
            isAllDay = true,
        )

        /**
         * Creates a target at a specific local time in [zone].
         *
         * If [dateTime] falls in a DST gap — a wall-clock time that never occurs, such as 02:30
         * on a spring-forward morning — `java.time` moves it forward by the length of the gap.
         * If it falls in a DST overlap, the earlier of the two occurrences is used.
         */
        fun timed(dateTime: LocalDateTime, zone: ZoneId): EventTarget = EventTarget(
            epochMillis = dateTime.atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
            isAllDay = false,
        )
    }
}
