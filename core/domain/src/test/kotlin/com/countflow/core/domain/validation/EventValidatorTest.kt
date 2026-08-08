package com.countflow.core.domain.validation

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.LONDON
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Event validation.
 *
 * The emoji cases carry most of the weight: "one emoji" is not "one character", and getting it
 * wrong in either direction is user-visible — rejecting a valid family emoji, or accepting a
 * pasted word that renders as broken text on a 2×1 widget.
 */
class EventValidatorTest {

    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")
    private val validator = EventValidator(Clock.fixed(now, UTC))

    private fun validate(
        title: String = "Holiday",
        emoji: String? = null,
        target: EventTarget = EventTarget.allDay(LocalDate.of(2026, 12, 25), UTC),
    ) = validator.validateAt(title, emoji, target, now)

    // ---------------------------------------------------------------- happy path

    @Test
    fun `a well-formed event is valid`() {
        val result = validate(title = "Trip to Kyoto", emoji = "🌸")

        assertThat(result).isEqualTo(EventValidationResult.Valid)
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `an event with no emoji is valid`() {
        assertThat(validate(emoji = null).isValid).isTrue()
        assertThat(validate(emoji = "").isValid).isTrue()
    }

    // ---------------------------------------------------------------- title

    @Test
    fun `a blank title is rejected`() {
        listOf("", "   ", "\t", "\n").forEach { blank ->
            val result = validate(title = blank)

            assertThat(result.errors).contains(EventValidationError.BlankTitle)
            assertThat(result.errorFor(EventField.TITLE)).isNotNull()
        }
    }

    @Test
    fun `a title at the limit is accepted and one past it is not`() {
        val atLimit = "a".repeat(Event.MAX_TITLE_LENGTH)
        val overLimit = "a".repeat(Event.MAX_TITLE_LENGTH + 1)

        assertThat(validate(title = atLimit).isValid).isTrue()
        assertThat(validate(title = overLimit).errors)
            .contains(
                EventValidationError.TitleTooLong(
                    maxLength = Event.MAX_TITLE_LENGTH,
                    actualLength = Event.MAX_TITLE_LENGTH + 1,
                ),
            )
    }

    @Test
    fun `title length counts code points, not UTF-16 units`() {
        // Each of these emoji is two UTF-16 chars. Measuring with `length` would cut the
        // allowance in half for anyone writing a title in emoji.
        val title = "🎉".repeat(Event.MAX_TITLE_LENGTH)

        assertThat(title.length).isEqualTo(Event.MAX_TITLE_LENGTH * 2)
        assertThat(validate(title = title).isValid).isTrue()
    }

    // ---------------------------------------------------------------- emoji

    @Test
    fun `single emoji of every shape are accepted`() {
        // Each of these is one grapheme cluster and one glyph, but they have wildly different
        // code-point counts: a plain emoji, a skin-tone modifier sequence, a zero-width-joiner
        // family, a regional-indicator flag, and a keycap sequence.
        listOf("🎉", "👍🏽", "👨‍👩‍👧‍👦", "🇬🇧", "❤️").forEach { emoji ->
            assertThat(validate(emoji = emoji).isValid).isTrue()
        }
    }

    @Test
    fun `more than one emoji is rejected`() {
        listOf("🎉🎊", "👍🏽👍🏽", "🇬🇧🇫🇷").forEach { emoji ->
            assertThat(validate(emoji = emoji).errors)
                .contains(EventValidationError.InvalidEmoji)
        }
    }

    @Test
    fun `pasted text is rejected`() {
        // The common real-world mistake: typing a word into the emoji field.
        listOf("Birthday", "a", "1", ":)").forEach { text ->
            assertThat(validate(emoji = text).errors)
                .contains(EventValidationError.InvalidEmoji)
        }
    }

    @Test
    fun `surrounding whitespace is rejected rather than silently trimmed`() {
        // Trimming for the user would be friendlier but hides the paste mistake; the form can
        // trim before it calls, and then the value it saves is the value it validated.
        listOf(" 🎉", "🎉 ", " 🎉 ").forEach { padded ->
            assertThat(validate(emoji = padded).errors)
                .contains(EventValidationError.InvalidEmoji)
        }
    }

    @Test
    fun `an emoji error is attached to the emoji field`() {
        val result = validate(emoji = "not an emoji")

        assertThat(result.errorFor(EventField.EMOJI)).isEqualTo(EventValidationError.InvalidEmoji)
        assertThat(result.errorFor(EventField.TITLE)).isNull()
    }

    // ---------------------------------------------------------------- target

    @Test
    fun `an unknown time zone is rejected`() {
        // Reachable after restoring a backup written on a device with a newer tz database.
        val target = EventTarget(epochMillis = 0, zoneId = "Mars/Olympus_Mons", isAllDay = true)

        assertThat(validate(target = target).errors)
            .contains(EventValidationError.UnknownTimeZone)
    }

    @Test
    fun `an extreme epoch value is reported rather than thrown`() {
        // Long.MAX_VALUE milliseconds is about 292 million years, which java.time resolves
        // happily — so this lands on the horizon check rather than the unresolvable-target one.
        // What matters is that a corrupt or absurd stored value produces an error the form can
        // show, never an exception escaping into a query or a widget render.
        listOf(Long.MAX_VALUE, Long.MIN_VALUE).forEach { extreme ->
            val target = EventTarget(epochMillis = extreme, zoneId = "UTC", isAllDay = false)

            val result = validate(target = target)

            assertThat(result.isValid).isFalse()
            assertThat(result.errorFor(EventField.TARGET)).isNotNull()
        }
    }

    @Test
    fun `a target inside the horizon is accepted`() {
        listOf(
            LocalDate.of(2026, 6, 16),
            LocalDate.of(2100, 1, 1),
            LocalDate.of(1950, 1, 1),
        ).forEach { date ->
            assertThat(validate(target = EventTarget.allDay(date, UTC)).isValid).isTrue()
        }
    }

    @Test
    fun `a target beyond the future horizon is rejected`() {
        val tooFar = EventTarget.allDay(LocalDate.of(2026 + 101, 1, 1), UTC)

        assertThat(validate(target = tooFar).errors)
            .contains(EventValidationError.TargetTooFarFuture(EventValidator.MAX_YEARS_AHEAD))
    }

    @Test
    fun `a target beyond the past horizon is rejected`() {
        val tooFar = EventTarget.allDay(LocalDate.of(2026 - 151, 1, 1), UTC)

        assertThat(validate(target = tooFar).errors)
            .contains(EventValidationError.TargetTooFarPast(EventValidator.MAX_YEARS_BEHIND))
    }

    @Test
    fun `a target in another zone validates against that zone`() {
        val target = EventTarget.timed(Fixtures.dateTime("2026-12-25T09:00"), LONDON)

        assertThat(validate(target = target).isValid).isTrue()
    }

    // ---------------------------------------------------------------- reporting

    @Test
    fun `every problem is reported at once`() {
        // A form that surfaces one error at a time makes the user fix, resubmit, and discover
        // the next. All three of these are wrong; all three must come back together.
        val result = validate(
            title = "",
            emoji = "nope",
            target = EventTarget(epochMillis = 0, zoneId = "Nowhere/Nothing", isAllDay = true),
        )

        assertThat(result.errors).hasSize(3)
        assertThat(result.errors.map { it.field })
            .containsExactly(EventField.TITLE, EventField.EMOJI, EventField.TARGET)
    }

    @Test
    fun `validating an existing event uses the injected clock`() {
        val event = Event.create(
            title = "Valid",
            target = EventTarget.allDay(LocalDate.of(2026, 12, 25), UTC),
            createdAt = now,
        )

        assertThat(validator.validate(event).isValid).isTrue()
    }

    @Test
    fun `validating fields uses the injected clock`() {
        val result = validator.validate(
            title = "Valid",
            emoji = "🎂",
            target = EventTarget.allDay(LocalDate.of(2026, 12, 25), UTC),
        )

        assertThat(result.isValid).isTrue()
    }
}
