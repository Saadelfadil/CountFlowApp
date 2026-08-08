package com.countflow.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.countflow.core.database.entity.EventEntity
import com.countflow.core.database.entity.ReminderEntity
import com.countflow.core.database.entity.WidgetBindingEntity
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.model.WidgetStyle
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime

/**
 * Base class for tests that need a real database.
 *
 * Runs against in-memory SQLite through Robolectric rather than a fake DAO. That distinction is
 * the whole point: cascading deletes, `COLLATE NOCASE` ordering, and the behaviour of `IN ()`
 * over an empty set are SQLite semantics. A hand-written fake would implement whatever the
 * author assumed, which is exactly the assumption under test.
 *
 * `allowMainThreadQueries` is safe here and only here — the tests are synchronous, and the
 * production code always goes through the IO dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
internal abstract class DatabaseTestCase {

    protected lateinit var database: CountFlowDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // ---------------------------------------------------------------- fixtures

    protected fun event(
        id: String,
        title: String = "Event $id",
        targetEpochMillis: Long = 1_000L,
        category: EventCategory = EventCategory.GENERAL,
        isArchived: Boolean = false,
        isCompleted: Boolean = false,
        remindersEnabled: Boolean = true,
        createdAt: Instant = Instant.EPOCH,
    ) = EventEntity(
        id = id,
        title = title,
        emoji = null,
        iconKey = null,
        category = category,
        targetEpochMillis = targetEpochMillis,
        targetZoneId = "UTC",
        isAllDay = false,
        createdAt = createdAt,
        accentArgb = null,
        defaultWidgetStyle = WidgetStyle.MINIMAL,
        defaultProgressStyle = ProgressStyle.LINEAR,
        remindersEnabled = remindersEnabled,
        isArchived = isArchived,
        isCompleted = isCompleted,
    )

    protected fun binding(appWidgetId: Int, eventId: String) = WidgetBindingEntity(
        appWidgetId = appWidgetId,
        eventId = eventId,
        widgetStyleOverride = null,
        progressStyleOverride = null,
        showTitle = true,
        showEmoji = true,
        showTargetDate = false,
        showPercentage = false,
        createdAt = Instant.EPOCH,
    )

    protected fun reminder(
        id: String,
        eventId: String,
        type: ReminderType = ReminderType.DAY_OF,
        isEnabled: Boolean = true,
    ) = ReminderEntity(
        id = id,
        eventId = eventId,
        type = type,
        timeOfDay = LocalTime.of(9, 0),
        isEnabled = isEnabled,
    )
}
