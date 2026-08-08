package com.countflow.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.countflow.core.database.CountFlowDatabase
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The widget binding repository, with particular attention to the two SQL edge cases the
 * implementation handles in Kotlin rather than trusting SQLite to get right.
 */
@RunWith(RobolectricTestRunner::class)
internal class WidgetBindingRepositoryImplTest {

    private lateinit var database: CountFlowDatabase
    private lateinit var repository: WidgetBindingRepositoryImpl
    private lateinit var events: EventRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
        ).allowMainThreadQueries().build()

        // See EventRepositoryImplTest for why this is Unconfined rather than a TestDispatcher.
        repository = WidgetBindingRepositoryImpl(database.widgetBindingDao(), Dispatchers.Unconfined)
        events = EventRepositoryImpl(database.eventDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun givenEvent(id: String): EventId {
        val event = Event.create(
            id = EventId(id),
            title = "Event $id",
            target = EventTarget.allDay(LocalDate.of(2026, 6, 15), ZoneId.of("UTC")),
            createdAt = Instant.EPOCH,
        )
        events.upsertEvent(event)
        return event.id
    }

    private fun binding(id: Int, eventId: EventId, style: WidgetStyle? = null) =
        WidgetBinding.inheriting(AppWidgetId(id), eventId, Instant.EPOCH)
            .copy(widgetStyleOverride = style)

    @Test
    fun `a binding round trips with its overrides intact`() = runTest {
        val eventId = givenEvent("a")
        val original = binding(1, eventId, WidgetStyle.OLED)

        repository.upsertBinding(original)

        assertThat(repository.getBinding(AppWidgetId(1))).isEqualTo(original)
    }

    @Test
    fun `a bound widget resolves its event`() = runTest {
        val eventId = givenEvent("a")
        repository.upsertBinding(binding(1, eventId))

        val bound = repository.getBoundWidget(AppWidgetId(1))

        assertThat(bound?.event?.id).isEqualTo(eventId)
        assertThat(bound?.binding?.appWidgetId).isEqualTo(AppWidgetId(1))
    }

    @Test
    fun `an unbound widget resolves to null rather than throwing`() = runTest {
        assertThat(repository.getBoundWidget(AppWidgetId(99))).isNull()
        assertThat(repository.observeBoundWidget(AppWidgetId(99)).first()).isNull()
    }

    @Test
    fun `two widgets on one event can carry different styles`() = runTest {
        // The reason style lives on the binding at all. If this ever regressed, the feature
        // would silently collapse to one look per event.
        val eventId = givenEvent("a")
        repository.upsertBinding(binding(1, eventId, WidgetStyle.OLED))
        repository.upsertBinding(binding(2, eventId, WidgetStyle.GLASS))

        val styles = repository.getAllBoundWidgets()
            .associate { it.binding.appWidgetId.value to it.binding.resolveWidgetStyle(it.event) }

        assertThat(styles[1]).isEqualTo(WidgetStyle.OLED)
        assertThat(styles[2]).isEqualTo(WidgetStyle.GLASS)
    }

    @Test
    fun `an inheriting binding follows the event's default`() = runTest {
        val event = Event.create(
            id = EventId("a"),
            title = "A",
            target = EventTarget.allDay(LocalDate.of(2026, 6, 15), ZoneId.of("UTC")),
            createdAt = Instant.EPOCH,
            defaultWidgetStyle = WidgetStyle.MODERN,
        )
        events.upsertEvent(event)
        repository.upsertBinding(binding(1, event.id))

        val bound = repository.getBoundWidget(AppWidgetId(1))!!

        assertThat(bound.binding.resolveWidgetStyle(bound.event)).isEqualTo(WidgetStyle.MODERN)
    }

    @Test
    fun `pruning with an empty live set removes every binding`() = runTest {
        // The edge case the implementation special-cases: SQL `NOT IN ()` is never true, so the
        // generated query would delete nothing at exactly the moment everything should go.
        val eventId = givenEvent("a")
        repository.upsertBinding(binding(1, eventId))
        repository.upsertBinding(binding(2, eventId))

        repository.pruneOrphanedBindings(emptySet())

        assertThat(repository.getAllBoundWidgets()).isEmpty()
    }

    @Test
    fun `pruning keeps the widgets the launcher still reports`() = runTest {
        val eventId = givenEvent("a")
        listOf(1, 2, 3).forEach { repository.upsertBinding(binding(it, eventId)) }

        repository.pruneOrphanedBindings(setOf(AppWidgetId(1), AppWidgetId(3)))

        assertThat(repository.getAllBoundWidgets().map { it.binding.appWidgetId.value })
            .containsExactly(1, 3)
    }

    @Test
    fun `deleting an empty batch of bindings is a no-op`() = runTest {
        // `IN ()` matches nothing, so this happens to be safe — but the implementation guards it
        // explicitly, and the guard is worth pinning.
        val eventId = givenEvent("a")
        repository.upsertBinding(binding(1, eventId))

        repository.deleteBindings(emptyList())

        assertThat(repository.getAllBoundWidgets()).hasSize(1)
    }

    @Test
    fun `deleting bindings for an event leaves the event alone`() = runTest {
        val eventId = givenEvent("a")
        repository.upsertBinding(binding(1, eventId))

        repository.deleteBindingsForEvent(eventId)

        assertThat(repository.getAllBoundWidgets()).isEmpty()
        assertThat(events.getEvent(eventId)).isNotNull()
    }
}
