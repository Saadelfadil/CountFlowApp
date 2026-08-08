package com.countflow.widget.engine.provider

import app.cash.turbine.test
import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.widget.engine.testing.FakeWidgetBindingRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetRenderModelProviderTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-15T08:00:00Z")

    private val fake = FakeWidgetBindingRepository()
    private val provider = WidgetRenderModelProvider(
        widgetBindingRepository = fake.repository,
        countdownEngine = CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default),
        clock = Clock.fixed(now, zone),
    )

    private fun event(id: String, daysAhead: Long = 5) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15).plusDays(daysAhead), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `an unbound widget produces no render model`() = runTest {
        assertThat(provider.get(AppWidgetId(99))).isNull()
    }

    @Test
    fun `a bound widget produces a render model matching its event`() = runTest {
        val subject = event("a")
        fake.put(WidgetBinding.inheriting(AppWidgetId(1), subject.id, Instant.EPOCH), subject)

        val model = provider.get(AppWidgetId(1))

        assertThat(model).isNotNull()
        assertThat(model?.eventId).isEqualTo(subject.id)
        assertThat(model?.title).isEqualTo("Event a")
    }

    @Test
    fun `observing re-emits when the binding changes`() = runTest {
        val subject = event("a", daysAhead = 5)
        val binding = WidgetBinding.inheriting(AppWidgetId(1), subject.id, Instant.EPOCH)
        fake.put(binding, subject)

        provider.observe(AppWidgetId(1)).test {
            assertThat(awaitItem()?.showTitle).isTrue()

            fake.repository.upsertBinding(binding.copy(showTitle = false))

            assertThat(awaitItem()?.showTitle).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing an unbound widget emits null and stays that way`() = runTest {
        provider.observe(AppWidgetId(42)).test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
