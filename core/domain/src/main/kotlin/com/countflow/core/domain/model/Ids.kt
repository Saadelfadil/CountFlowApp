package com.countflow.core.domain.model

import java.util.UUID

/**
 * Identifier for an [Event].
 *
 * A value class rather than a bare `String` so an event id can never be passed where a reminder
 * id or an arbitrary string is expected. It costs nothing at runtime — the compiler erases it
 * to the underlying `String`.
 */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        /** Generates a new random identifier. */
        fun random(): EventId = EventId(UUID.randomUUID().toString())
    }
}

/** Identifier for a [Reminder]. */
@JvmInline
value class ReminderId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReminderId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        /** Generates a new random identifier. */
        fun random(): ReminderId = ReminderId(UUID.randomUUID().toString())
    }
}

/**
 * The system-assigned identifier for a placed home-screen widget.
 *
 * Allocated by the launcher, so it is only meaningful on the device that created it. This is why
 * widget bindings must be excluded from cloud backup and device transfer.
 */
@JvmInline
value class AppWidgetId(val value: Int)
