package com.countflow.core.domain.validation

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventTarget
import java.text.BreakIterator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks that an event is safe to persist.
 *
 * Lives in the domain, not in a ViewModel, for two reasons. The form is not the only writer —
 * restore from backup and, later, widget configuration also create events, and a rule enforced
 * in one screen is a rule the other paths skip. And validation is pure logic over the model,
 * which makes it exhaustively testable without Android.
 *
 * Every rule reports through [EventValidationResult] rather than throwing. Invalid input is the
 * normal state of a form being filled in, and the caller needs every problem at once so the user
 * is not made to fix one, resubmit, and discover the next.
 *
 * `Event`'s own `require(title.isNotBlank())` remains as a last-resort invariant. This validator
 * is what stops a caller ever reaching it.
 *
 * @property clock supplies "now" for the horizon checks. Injected so the bounds are testable.
 */
@Singleton
class EventValidator @Inject constructor(
    private val clock: Clock,
) {

    /** Validates the fields of an event being composed, before an [Event] instance exists. */
    fun validate(
        title: String,
        emoji: String?,
        target: EventTarget,
    ): EventValidationResult = validateAt(title, emoji, target, clock.instant())

    /** Validates an existing event, for example one arriving from a backup file. */
    fun validate(event: Event): EventValidationResult =
        validateAt(event.title, event.emoji, event.target, clock.instant())

    /**
     * Validates against an explicit instant.
     *
     * The testable entry point, and the one a bulk import should use so every row in a file is
     * judged against the same moment rather than against a clock that advances mid-import.
     */
    fun validateAt(
        title: String,
        emoji: String?,
        target: EventTarget,
        now: Instant,
    ): EventValidationResult {
        val errors = buildList {
            validateTitle(title)?.let(::add)
            validateEmoji(emoji)?.let(::add)
            addAll(validateTarget(target, now))
        }

        return if (errors.isEmpty()) {
            EventValidationResult.Valid
        } else {
            EventValidationResult.Invalid(errors)
        }
    }

    // ---------------------------------------------------------------- title

    private fun validateTitle(title: String): EventValidationError? = when {
        title.isBlank() -> EventValidationError.BlankTitle
        // Length is measured in code points, not UTF-16 units: a title of emoji would otherwise
        // hit the limit at half the visible characters, since each emoji is a surrogate pair.
        title.codePointCount(0, title.length) > Event.MAX_TITLE_LENGTH ->
            EventValidationError.TitleTooLong(
                maxLength = Event.MAX_TITLE_LENGTH,
                actualLength = title.codePointCount(0, title.length),
            )

        else -> null
    }

    // ---------------------------------------------------------------- emoji

    /**
     * The emoji field is optional, but if present it must be exactly one emoji.
     *
     * "One emoji" is not "one character". A family emoji is several code points joined by
     * zero-width joiners, a flag is two regional indicators, and a thumbs-up with a skin tone is
     * a base plus a modifier — all of which are one *grapheme cluster* and render as one glyph.
     * [BreakIterator] is what counts those correctly; `length` and `codePointCount` both get it
     * wrong in opposite directions.
     *
     * The second check rejects ordinary text. Users paste words into emoji fields, and a widget
     * rendering "Birthday" where a glyph belongs looks broken at 2×1.
     */
    private fun validateEmoji(emoji: String?): EventValidationError? {
        if (emoji == null || emoji.isEmpty()) return null
        if (emoji != emoji.trim()) return EventValidationError.InvalidEmoji
        if (graphemeCount(emoji) != 1) return EventValidationError.InvalidEmoji

        val looksLikeText = emoji.codePoints().toArray().all { codePoint ->
            Character.isLetterOrDigit(codePoint) ||
                Character.isWhitespace(codePoint) ||
                codePoint < FIRST_NON_ASCII
        }
        return if (looksLikeText) EventValidationError.InvalidEmoji else null
    }

    private fun graphemeCount(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    // ---------------------------------------------------------------- target

    /**
     * Bounds the target and checks the zone is real.
     *
     * The horizon limits exist because a date picker mis-entry — a year typed as 20260 — would
     * otherwise produce a countdown of eighteen thousand years, and because arithmetic near the
     * extremes of epoch milliseconds stops being meaningful. They are generous enough that no
     * real countdown hits them.
     *
     * The zone check matters after a restore: a backup written on a device with a newer tz
     * database can name a zone this device has never heard of, and every later call to
     * `ZoneId.of` would throw from inside a query or a widget render.
     */
    private fun validateTarget(target: EventTarget, now: Instant): List<EventValidationError> {
        // A single catch covers both failure modes: an unrecognised zone id, and an epoch value
        // so extreme that resolving it overflows. Both are the same problem from the user's side
        // — this target cannot be interpreted — and both must be caught here rather than thrown
        // later from inside a query or a widget render.
        val resolved = runCatching {
            val zone = ZoneId.of(target.zoneId)
            zone to target.instant.atZone(zone)
        }.getOrNull() ?: return listOf(EventValidationError.UnknownTimeZone)

        val (zone, targetZoned) = resolved
        val nowZoned = now.atZone(zone)

        return when {
            targetZoned.year > nowZoned.year + MAX_YEARS_AHEAD ->
                listOf(EventValidationError.TargetTooFarFuture(MAX_YEARS_AHEAD))

            targetZoned.year < nowZoned.year - MAX_YEARS_BEHIND ->
                listOf(EventValidationError.TargetTooFarPast(MAX_YEARS_BEHIND))

            else -> emptyList()
        }
    }

    companion object {
        /** How far ahead a target may be set. Generous; no real countdown reaches it. */
        const val MAX_YEARS_AHEAD: Int = 100

        /** How far back a target may be set. Covers any plausible anniversary. */
        const val MAX_YEARS_BEHIND: Int = 150

        /** Below this, a code point is plain ASCII and cannot be an emoji. */
        private const val FIRST_NON_ASCII = 0x80
    }
}
