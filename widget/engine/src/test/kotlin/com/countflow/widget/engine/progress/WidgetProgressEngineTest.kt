package com.countflow.widget.engine.progress

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.ProgressStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.countflow.core.domain.countdown.CountdownEngine

class WidgetProgressEngineTest {

    private val zone = ZoneId.of("UTC")

    private fun countdownAt(percent: Instant, createdAt: String, target: String) =
        CountdownEngine(Clock.fixed(percent, zone), CountdownConfig.Default).countdownAt(
            event = Event.create(
                id = EventId("e"),
                title = "E",
                target = EventTarget.timed(java.time.LocalDateTime.parse(target), zone),
                createdAt = Instant.parse(createdAt),
            ),
            now = percent,
            deviceZone = zone,
        )

    @Test
    fun `none is never visible regardless of progress`() {
        val countdown = countdownAt(
            Instant.parse("2026-06-05T00:00:00Z"),
            "2026-06-01T00:00:00Z",
            "2026-06-11T00:00",
        )

        val progress = WidgetProgressEngine.calculate(countdown, ProgressStyle.NONE)

        assertThat(progress.isVisible).isFalse()
    }

    @Test
    fun `linear and circular are both visible and carry identical numbers`() {
        // The whole point of separating calculation from drawing: both styles agree on the
        // numbers, they only differ in how a renderer draws them.
        val countdown = countdownAt(
            Instant.parse("2026-06-05T00:00:00Z"),
            "2026-06-01T00:00:00Z",
            "2026-06-11T00:00",
        )

        val linear = WidgetProgressEngine.calculate(countdown, ProgressStyle.LINEAR)
        val circular = WidgetProgressEngine.calculate(countdown, ProgressStyle.CIRCULAR)

        assertThat(linear.isVisible).isTrue()
        assertThat(circular.isVisible).isTrue()
        assertThat(linear.fraction).isEqualTo(circular.fraction)
        assertThat(linear.percent).isEqualTo(circular.percent)
    }

    @Test
    fun `percent text is a plain whole-number percentage`() {
        val countdown = countdownAt(
            Instant.parse("2026-06-06T00:00:00Z"),
            "2026-06-01T00:00:00Z",
            "2026-06-11T00:00",
        )

        val progress = WidgetProgressEngine.calculate(countdown, ProgressStyle.LINEAR)

        assertThat(progress.percent).isEqualTo(50)
        assertThat(progress.percentText).isEqualTo("50%")
    }

    @Test
    fun `a completed or expired countdown reports full progress`() {
        val countdown = countdownAt(
            Instant.parse("2026-06-20T00:00:00Z"),
            "2026-06-01T00:00:00Z",
            "2026-06-11T00:00",
        )

        val progress = WidgetProgressEngine.calculate(countdown, ProgressStyle.LINEAR)

        assertThat(progress.percent).isEqualTo(100)
        assertThat(progress.fraction).isEqualTo(1f)
    }
}
