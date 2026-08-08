package com.countflow.core.domain.validation

/**
 * Which part of the event form a problem belongs to.
 *
 * Named fields rather than a flat error list so the UI can attach each message to the control
 * that caused it. A form that says only "something is wrong" makes the user hunt.
 */
enum class EventField {
    TITLE,
    EMOJI,
    TARGET,
}

/**
 * A specific reason an event is not valid.
 *
 * A sealed type rather than a message string, for the same reason [com.countflow.core.domain.countdown.CountdownLabel]
 * is: the domain must not decide what English says. The UI maps each case to a string resource
 * and can interpolate the limits carried on the case itself.
 *
 * @property field the form control this belongs to.
 */
sealed class EventValidationError(val field: EventField) {

    /** The title is empty or only whitespace. */
    data object BlankTitle : EventValidationError(EventField.TITLE)

    /**
     * The title is longer than the limit.
     *
     * @property maxLength the limit, so the message can state it without duplicating the constant.
     * @property actualLength what the user typed.
     */
    data class TitleTooLong(
        val maxLength: Int,
        val actualLength: Int,
    ) : EventValidationError(EventField.TITLE)

    /**
     * The emoji field holds something that is not a single emoji.
     *
     * Users paste all sorts of things into an emoji field — a word, several emoji, an emoji plus
     * a space. Accepting any of them produces a widget that renders badly at 2×1.
     */
    data object InvalidEmoji : EventValidationError(EventField.EMOJI)

    /** The target zone is not a zone this device recognises. */
    data object UnknownTimeZone : EventValidationError(EventField.TARGET)

    /**
     * The target is further out than the app supports.
     *
     * @property maxYears the supported horizon.
     */
    data class TargetTooFarFuture(val maxYears: Int) : EventValidationError(EventField.TARGET)

    /**
     * The target is further back than the app supports.
     *
     * @property maxYears the supported horizon.
     */
    data class TargetTooFarPast(val maxYears: Int) : EventValidationError(EventField.TARGET)
}

/**
 * The outcome of validating an event.
 *
 * Modelled as a sealed result rather than a thrown exception because invalid input is an
 * expected state of a form being filled in, not an exceptional one — and because the form needs
 * *all* the problems at once, which an exception cannot carry without becoming a list anyway.
 */
sealed interface EventValidationResult {

    /** Every rule passed. */
    data object Valid : EventValidationResult

    /**
     * One or more rules failed.
     *
     * @property errors every problem found, not just the first. Validating lazily and stopping
     *   at the first error makes a user fix one thing, resubmit, and discover the next.
     */
    data class Invalid(val errors: List<EventValidationError>) : EventValidationResult
}

/** Whether validation passed. */
val EventValidationResult.isValid: Boolean
    get() = this is EventValidationResult.Valid

/** The problems found, empty when valid. */
val EventValidationResult.errors: List<EventValidationError>
    get() = when (this) {
        is EventValidationResult.Invalid -> errors
        EventValidationResult.Valid -> emptyList()
    }

/** The first problem affecting [field], or null. Used to decorate a single form control. */
fun EventValidationResult.errorFor(field: EventField): EventValidationError? =
    errors.firstOrNull { it.field == field }
