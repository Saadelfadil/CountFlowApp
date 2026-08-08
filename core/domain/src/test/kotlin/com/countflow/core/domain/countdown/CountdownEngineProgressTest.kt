package com.countflow.core.domain.countdown

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Percentage complete, elapsed time, and the degenerate cases that would divide by zero. */
class CountdownEngineProgressTest {

    private val engine = Fixtures.engine()

    private fun event(created: String, target: String): Event = Event.create(
        id = EventId("progress"),
        title = "Progress",
        target = EventTarget.timed(Fixtures.dateTime(target), UTC),
        createdAt = Instant.parse(created),
    )

    private fun progressAt(created: String, target: String, now: String): CountdownResult =
        engine.countdownAt(
            event = event(created, target),
            now = Instant.parse(now),
            deviceZone = UTC,
        )

    @Test
    fun `progress runs from creation to target`() {
        val cases = listOf(
            "2026-01-01T00:00:00Z" to 0f,
            "2026-01-03T12:00:00Z" to 0.25f,
            "2026-01-06T00:00:00Z" to 0.5f,
            "2026-01-08T12:00:00Z" to 0.75f,
            "2026-01-11T00:00:00Z" to 1f,
        )

        cases.forEach { (now, expected) ->
            val result = progressAt(
                created = "2026-01-01T00:00:00Z",
                target = "2026-01-11T00:00",
                now = now,
            )

            assertThat(result.percentComplete).isWithin(0.0001f).of(expected)
        }
    }

    @Test
    fun `progress clamps once the target has passed`() {
        val result = progressAt(
            created = "2026-01-01T00:00:00Z",
            target = "2026-01-11T00:00",
            now = "2026-06-01T00:00:00Z",
        )

        assertThat(result.percentComplete).isEqualTo(1f)
        assertThat(result.percentCompleteWhole).isEqualTo(100)
    }

    @Test
    fun `progress is zero before the event was created`() {
        // Possible after a restore from backup on a device whose clock is behind.
        val result = progressAt(
            created = "2026-01-10T00:00:00Z",
            target = "2026-01-20T00:00",
            now = "2026-01-05T00:00:00Z",
        )

        assertThat(result.percentComplete).isEqualTo(0f)
    }

    @Test
    fun `an event created at its own target is fully complete rather than dividing by zero`() {
        val result = progressAt(
            created = "2026-01-11T00:00:00Z",
            target = "2026-01-11T00:00",
            now = "2026-01-11T00:00:00Z",
        )

        assertThat(result.percentComplete).isEqualTo(1f)
    }

    @Test
    fun `an event created after its own target is fully complete`() {
        // Backdating an event — "my birthday last month" — is legitimate and must not produce
        // a negative or infinite percentage.
        val result = progressAt(
            created = "2026-02-01T00:00:00Z",
            target = "2026-01-11T00:00",
            now = "2026-02-02T00:00:00Z",
        )

        assertThat(result.percentComplete).isEqualTo(1f)
        assertThat(result.percentCompleteWhole).isEqualTo(100)
    }

    @Test
    fun `elapsed measures from creation and never goes negative`() {
        val forward = progressAt(
            created = "2026-01-01T00:00:00Z",
            target = "2026-01-11T00:00",
            now = "2026-01-04T00:00:00Z",
        )
        assertThat(forward.elapsed).isEqualTo(Duration.ofDays(3))

        val backward = progressAt(
            created = "2026-01-10T00:00:00Z",
            target = "2026-01-20T00:00",
            now = "2026-01-05T00:00:00Z",
        )
        assertThat(backward.elapsed).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `whole percentage is floored for cache friendliness`() {
        // The progress-ring renderer caches on whole percent, so this must not round up and
        // produce two cache entries for one visual state.
        val result = progressAt(
            created = "2026-01-01T00:00:00Z",
            target = "2026-01-11T00:00",
            now = "2026-01-01T23:59:00Z",
        )

        assertThat(result.percentComplete).isGreaterThan(0.099f)
        assertThat(result.percentCompleteWhole).isEqualTo(9)
    }

    @Test
    fun `the clock-backed entry point agrees with the explicit one`() {
        val now = Instant.parse("2026-06-15T08:00:00Z")
        val target = Fixtures.dateTime("2026-06-20T08:00")
        val subject = Fixtures.timedEvent(at = target, zone = UTC)

        val viaClock = Fixtures.engineAt(now, UTC).countdown(subject)
        val viaExplicit = engine.countdownAt(subject, now, UTC)

        assertThat(viaClock).isEqualTo(viaExplicit)
    }
}
