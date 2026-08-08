package com.countflow.core.domain.countdown

import com.countflow.core.domain.model.Event
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes where an event sits in time.
 *
 * This is the piece everything else depends on, so it is deliberately pure: no Android, no I/O,
 * no hidden clock reads inside the calculation. [countdownAt] takes the instant and zone as
 * parameters, which is what makes exhaustive testing of DST boundaries, leap days, and year
 * rollovers possible without touching the system clock.
 *
 * ### Two rules that drive the whole design
 *
 * **Day counts are calendar comparisons, never duration division.** Dividing a duration by
 * 86,400,000 is wrong roughly half the time: 23 hours can span two calendar days and 25 hours
 * can span one. Every day count here comes from comparing `LocalDate`s in the device's zone
 * (DECISIONS.md D-015).
 *
 * **All-day and timed events resolve in different zones.** An all-day event is a date and
 * follows the device wherever it travels; a timed event is an instant pinned to the zone it was
 * authored in. [com.countflow.core.domain.model.EventTarget] owns that distinction
 * (DECISIONS.md D-014).
 *
 * @property clock supplies the current instant and the device zone. Injected rather than read
 *   from `Instant.now()` so tests can pin time with `Clock.fixed`.
 * @property config the label and status thresholds.
 */
@Singleton
class CountdownEngine @Inject constructor(
    private val clock: Clock,
    private val config: CountdownConfig,
) {

    /** Computes the countdown for [event] as of now, in the device's current zone. */
    fun countdown(event: Event): CountdownResult =
        countdownAt(event = event, now = clock.instant(), deviceZone = clock.zone)

    /**
     * Computes the countdown for [event] at an explicit instant and zone.
     *
     * The testable entry point, and the one widgets should use when rendering a batch of events
     * against a single consistent "now" rather than letting each read drift.
     *
     * @param event the event to evaluate.
     * @param now the instant to evaluate at.
     * @param deviceZone the zone the device is currently in.
     */
    fun countdownAt(event: Event, now: Instant, deviceZone: ZoneId): CountdownResult {
        val nowZoned = now.atZone(deviceZone)
        val start = event.target.startAt(deviceZone)
        val end = event.target.endAt(deviceZone)

        // An all-day event runs until the following midnight, so it is not past merely because
        // its first instant has gone by. A timed event ends when it begins.
        val isPast = !nowZoned.isBefore(end)

        // Three distinct quantities that are easy to conflate:
        //   untilStart — signed; negative once the event has begun.
        //   remaining  — forward-looking only, so an all-day event that is currently happening
        //                reports zero rather than "twelve hours since midnight".
        //   gap        — unsigned distance, which is what the breakdown and totals describe.
        val untilStart = Duration.between(nowZoned, start)
        val remaining = if (untilStart.isNegative) Duration.ZERO else untilStart
        val gap = untilStart.abs()

        val elapsed = maxOfZero(Duration.between(event.createdAt.atZone(deviceZone), nowZoned))

        val calendarDaysRemaining = ChronoUnit.DAYS.between(
            nowZoned.toLocalDate(),
            start.toLocalDate(),
        )

        val status = resolveStatus(
            event = event,
            isPast = isPast,
            untilStart = untilStart,
            calendarDaysRemaining = calendarDaysRemaining,
        )

        return CountdownResult(
            status = status,
            label = resolveLabel(
                status = status,
                calendarDaysRemaining = calendarDaysRemaining,
                today = nowZoned.toLocalDate(),
                targetDate = start.toLocalDate(),
            ),
            isPast = isPast,
            breakdown = breakdownBetween(earlier(nowZoned, start), later(nowZoned, start)),
            totals = totalsOf(gap),
            calendarDaysRemaining = calendarDaysRemaining,
            percentComplete = percentComplete(event, start, nowZoned, deviceZone),
            elapsed = elapsed,
            remaining = remaining,
        )
    }

    // ---------------------------------------------------------------- status

    private fun resolveStatus(
        event: Event,
        isPast: Boolean,
        untilStart: Duration,
        calendarDaysRemaining: Long,
    ): CountdownStatus = when {
        event.isCompleted -> CountdownStatus.COMPLETED
        isPast -> CountdownStatus.EXPIRED
        // All-day events are never imminent. IMMINENT exists to drive second-level ticking,
        // and there is nothing to tick towards when the "event" is a whole day — at one minute
        // past midnight the honest answer is "today", not "starting soon".
        //
        // The negative check matters too: an all-day event already under way has a negative
        // untilStart, which would otherwise satisfy the threshold for its entire day.
        !event.target.isAllDay &&
            !untilStart.isNegative &&
            untilStart <= config.imminentThreshold -> CountdownStatus.IMMINENT

        calendarDaysRemaining == 0L -> CountdownStatus.TODAY
        else -> CountdownStatus.UPCOMING
    }

    // ---------------------------------------------------------------- label

    private fun resolveLabel(
        status: CountdownStatus,
        calendarDaysRemaining: Long,
        today: LocalDate,
        targetDate: LocalDate,
    ): CountdownLabel {
        if (status == CountdownStatus.COMPLETED) return CountdownLabel.Completed
        if (status == CountdownStatus.IMMINENT) return CountdownLabel.StartingSoon

        if (status == CountdownStatus.EXPIRED) {
            val daysAgo = -calendarDaysRemaining
            return when {
                // Passed earlier today, or an all-day event whose day has just ended.
                daysAgo <= 0L -> CountdownLabel.Expired
                daysAgo == 1L -> CountdownLabel.Yesterday
                daysAgo <= config.recentPastDays -> CountdownLabel.DaysAgo(daysAgo.toInt())
                else -> CountdownLabel.Expired
            }
        }

        return when {
            calendarDaysRemaining == 0L -> CountdownLabel.Today
            calendarDaysRemaining == 1L -> CountdownLabel.Tomorrow
            calendarDaysRemaining <= config.nearFutureDays ->
                CountdownLabel.InDays(calendarDaysRemaining.toInt())

            fallsInNextWeek(today = today, targetDate = targetDate) -> CountdownLabel.NextWeek
            else -> CountdownLabel.InDays(calendarDaysRemaining.toInt())
        }
    }

    /**
     * Whether [targetDate] lands in the calendar week after the one containing [today].
     *
     * Compares against week *boundaries* rather than week-of-year numbers, which avoids the
     * wraparound bug at the turn of the year where week 52 is followed by week 1 and a
     * numeric comparison silently fails.
     */
    private fun fallsInNextWeek(today: LocalDate, targetDate: LocalDate): Boolean {
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(config.weekStartsOn))
        val nextWeekStart = thisWeekStart.plusWeeks(1)
        val weekAfterStart = thisWeekStart.plusWeeks(2)
        return !targetDate.isBefore(nextWeekStart) && targetDate.isBefore(weekAfterStart)
    }

    // ---------------------------------------------------------------- arithmetic

    /**
     * Decomposes the span between two moments into calendar units.
     *
     * Steps unit by unit, re-anchoring after each step, rather than dividing a duration. That is
     * what makes "one month" mean a real month — 28, 29, 30, or 31 days depending on where you
     * are standing — and what keeps a span across a DST boundary from gaining or losing an hour.
     *
     * @param from the earlier moment.
     * @param to the later moment.
     */
    private fun breakdownBetween(from: ZonedDateTime, to: ZonedDateTime): CountdownBreakdown {
        if (!from.isBefore(to)) return CountdownBreakdown.Zero

        var cursor = from

        val years = ChronoUnit.YEARS.between(cursor, to)
        cursor = cursor.plusYears(years)

        val months = ChronoUnit.MONTHS.between(cursor, to)
        cursor = cursor.plusMonths(months)

        val days = ChronoUnit.DAYS.between(cursor, to)
        cursor = cursor.plusDays(days)

        val hours = ChronoUnit.HOURS.between(cursor, to)
        cursor = cursor.plusHours(hours)

        val minutes = ChronoUnit.MINUTES.between(cursor, to)
        cursor = cursor.plusMinutes(minutes)

        val seconds = ChronoUnit.SECONDS.between(cursor, to)

        return CountdownBreakdown(
            years = years.toInt(),
            months = months.toInt(),
            days = days.toInt(),
            hours = hours.toInt(),
            minutes = minutes.toInt(),
            seconds = seconds.toInt(),
        )
    }

    private fun totalsOf(duration: Duration): CountdownTotals = CountdownTotals(
        totalSeconds = duration.seconds,
        totalMinutes = duration.toMinutes(),
        totalHours = duration.toHours(),
        totalDays = duration.toDays(),
        totalWeeks = duration.toDays() / DAYS_PER_WEEK,
    )

    /**
     * Progress from the event's creation to its target.
     *
     * Measured against the same [start] the countdown uses, so an all-day event that has been
     * re-resolved into a new device zone stays internally consistent.
     *
     * Two degenerate cases collapse to fully complete: an event created at or after its own
     * target (a zero or negative span, which would divide by zero) and any target already
     * behind us.
     */
    private fun percentComplete(
        event: Event,
        start: ZonedDateTime,
        nowZoned: ZonedDateTime,
        deviceZone: ZoneId,
    ): Float {
        val createdZoned = event.createdAt.atZone(deviceZone)
        val totalSpan = Duration.between(createdZoned, start)
        if (totalSpan.isZero || totalSpan.isNegative) return 1f

        val done = Duration.between(createdZoned, nowZoned)
        if (done.isNegative) return 0f

        val ratio = done.toMillis().toDouble() / totalSpan.toMillis().toDouble()
        return ratio.toFloat().coerceIn(0f, 1f)
    }

    private fun maxOfZero(duration: Duration): Duration =
        if (duration.isNegative) Duration.ZERO else duration

    private fun earlier(a: ZonedDateTime, b: ZonedDateTime): ZonedDateTime =
        if (a.isBefore(b)) a else b

    private fun later(a: ZonedDateTime, b: ZonedDateTime): ZonedDateTime =
        if (a.isBefore(b)) b else a

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
