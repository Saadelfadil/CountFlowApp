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
 * The rewarded-style entitlement repository — no AdMob involved anywhere in this file. Every test
 * grants an entitlement by calling [WidgetStyleEntitlementRepositoryImpl.grantRewardedStyle]
 * directly, exactly the one operation a future rewarded-ad completion callback will call; nothing
 * here simulates an ad, a reward dialog, or a network call.
 */
@RunWith(RobolectricTestRunner::class)
internal class WidgetStyleEntitlementRepositoryImplTest {

    private lateinit var database: CountFlowDatabase
    private lateinit var repository: WidgetStyleEntitlementRepositoryImpl
    private lateinit var bindings: WidgetBindingRepositoryImpl
    private lateinit var events: EventRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = WidgetStyleEntitlementRepositoryImpl(database.widgetStyleEntitlementDao(), Dispatchers.Unconfined)
        bindings = WidgetBindingRepositoryImpl(database.widgetBindingDao(), Dispatchers.Unconfined)
        events = EventRepositoryImpl(database.eventDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun givenWidget(appWidgetId: Int): AppWidgetId {
        val eventId = EventId("event-$appWidgetId")
        events.upsertEvent(
            Event.create(
                id = eventId,
                title = "Event $appWidgetId",
                target = EventTarget.allDay(LocalDate.of(2026, 6, 15), ZoneId.of("UTC")),
                createdAt = Instant.EPOCH,
            ),
        )
        val id = AppWidgetId(appWidgetId)
        bindings.upsertBinding(WidgetBinding.inheriting(id, eventId, Instant.EPOCH))
        return id
    }

    // ── 1–3: free styles are always unlocked ──

    @Test
    fun `minimal is always unlocked`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.MINIMAL)).isTrue()
    }

    @Test
    fun `material is always unlocked`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.MATERIAL)).isTrue()
    }

    @Test
    fun `oled is always unlocked`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.OLED)).isTrue()
    }

    // ── 4–6: rewarded styles are locked with no entitlement ──

    @Test
    fun `glass is locked without an entitlement`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.GLASS)).isFalse()
    }

    @Test
    fun `rounded is locked without an entitlement`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.ROUNDED)).isFalse()
    }

    @Test
    fun `modern is locked without an entitlement`() = runTest {
        val widget = givenWidget(1)
        assertThat(repository.isStyleUnlocked(widget, WidgetStyle.MODERN)).isFalse()
    }

    // ── 7–9: granting is per widget, not global ──

    @Test
    fun `granting glass to widget A unlocks it for widget A`() = runTest {
        val widgetA = givenWidget(1)

        repository.grantRewardedStyle(widgetA, WidgetStyle.GLASS)

        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.GLASS)).isTrue()
    }

    @Test
    fun `granting glass to widget A leaves it locked for widget B`() = runTest {
        val widgetA = givenWidget(1)
        val widgetB = givenWidget(2)

        repository.grantRewardedStyle(widgetA, WidgetStyle.GLASS)

        assertThat(repository.isStyleUnlocked(widgetB, WidgetStyle.GLASS)).isFalse()
    }

    @Test
    fun `widget A can independently unlock a second rewarded style without affecting the first`() = runTest {
        val widgetA = givenWidget(1)

        repository.grantRewardedStyle(widgetA, WidgetStyle.GLASS)
        repository.grantRewardedStyle(widgetA, WidgetStyle.ROUNDED)

        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.GLASS)).isTrue()
        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.ROUNDED)).isTrue()
        // Granting two of the three never implicitly grants the one never asked for.
        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.MODERN)).isFalse()
    }

    // ── 10: survives a real restart, not just a re-read within the same process ──

    @Test
    fun `an entitlement survives closing and reopening the database`() = runTest {
        val dbName = "entitlement-restart-test"
        val first = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
            dbName,
        ).allowMainThreadQueries().build()
        try {
            val eventId = EventId("event-1")
            EventRepositoryImpl(first.eventDao(), Dispatchers.Unconfined).upsertEvent(
                Event.create(
                    id = eventId,
                    title = "Trip",
                    target = EventTarget.allDay(LocalDate.of(2026, 6, 15), ZoneId.of("UTC")),
                    createdAt = Instant.EPOCH,
                ),
            )
            WidgetBindingRepositoryImpl(first.widgetBindingDao(), Dispatchers.Unconfined)
                .upsertBinding(WidgetBinding.inheriting(AppWidgetId(1), eventId, Instant.EPOCH))
            WidgetStyleEntitlementRepositoryImpl(first.widgetStyleEntitlementDao(), Dispatchers.Unconfined)
                .grantRewardedStyle(AppWidgetId(1), WidgetStyle.MODERN)
        } finally {
            first.close()
        }

        val second = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
            dbName,
        ).allowMainThreadQueries().build()
        try {
            val reloaded = WidgetStyleEntitlementRepositoryImpl(second.widgetStyleEntitlementDao(), Dispatchers.Unconfined)
            assertThat(reloaded.isStyleUnlocked(AppWidgetId(1), WidgetStyle.MODERN)).isTrue()
        } finally {
            second.close()
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
        }
    }

    // ── 11: deleting a widget leaves no orphaned entitlement state ──

    @Test
    fun `deleting a widget's binding removes its entitlements via the foreign key cascade`() = runTest {
        val widgetA = givenWidget(1)
        repository.grantRewardedStyle(widgetA, WidgetStyle.GLASS)
        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.GLASS)).isTrue()

        bindings.deleteBindings(listOf(widgetA))

        // No row survives to be orphaned: hasEntitlement (and therefore isStyleUnlocked, since
        // GLASS is rewarded) reads false again, the same as a widget that never had one.
        assertThat(database.widgetStyleEntitlementDao().hasEntitlement(widgetA.value, WidgetStyle.GLASS))
            .isFalse()
        assertThat(repository.isStyleUnlocked(widgetA, WidgetStyle.GLASS)).isFalse()
    }

    // ── security/correctness: only rewarded styles may ever be granted ──

    @Test
    fun `granting a free style is rejected rather than silently creating an unreachable entitlement`() = runTest {
        val widgetA = givenWidget(1)

        val error = runCatching { repository.grantRewardedStyle(widgetA, WidgetStyle.MINIMAL) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
