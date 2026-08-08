package com.countflow.widget.engine.lifecycle

import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.widget.engine.testing.FakeWidgetBindingRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetLifecycleCoordinatorTest {

    private val zone = ZoneId.of("UTC")
    private val fake = FakeWidgetBindingRepository()
    private val coordinator = WidgetLifecycleCoordinator(fake.repository)

    private fun event(id: String) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15), zone),
        createdAt = Instant.EPOCH,
    )

    private fun bind(appWidgetId: Int, eventId: String) {
        val subject = event(eventId)
        fake.put(WidgetBinding.inheriting(AppWidgetId(appWidgetId), subject.id, Instant.EPOCH), subject)
    }

    @Test
    fun `removing widgets deletes exactly those bindings`() = runTest {
        bind(1, "a")
        bind(2, "b")

        coordinator.onWidgetsRemoved(listOf(AppWidgetId(1)))

        assertThat(fake.repository.getBinding(AppWidgetId(1))).isNull()
        assertThat(fake.repository.getBinding(AppWidgetId(2))).isNotNull()
    }

    @Test
    fun `pruning keeps only the widgets the launcher still reports`() = runTest {
        bind(1, "a")
        bind(2, "b")
        bind(3, "c")

        coordinator.pruneOrphans(setOf(AppWidgetId(1), AppWidgetId(3)))

        assertThat(fake.repository.getAllBoundWidgets().map { it.binding.appWidgetId })
            .containsExactly(AppWidgetId(1), AppWidgetId(3))
    }

    @Test
    fun `pruning against an empty live set removes every binding`() = runTest {
        bind(1, "a")
        bind(2, "b")

        coordinator.pruneOrphans(emptySet())

        assertThat(fake.repository.getAllBoundWidgets()).isEmpty()
    }

    @Test
    fun `removing an empty batch is a no-op`() = runTest {
        bind(1, "a")

        coordinator.onWidgetsRemoved(emptyList())

        assertThat(fake.repository.getAllBoundWidgets()).hasSize(1)
    }
}
