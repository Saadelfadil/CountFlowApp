package com.countflow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.countflow.core.database.converter.Converters
import com.countflow.core.database.dao.EventDao
import com.countflow.core.database.dao.ReminderDao
import com.countflow.core.database.dao.WidgetBindingDao
import com.countflow.core.database.entity.EventEntity
import com.countflow.core.database.entity.ReminderEntity
import com.countflow.core.database.entity.WidgetBindingEntity

/**
 * The CountFlow database.
 *
 * ### Migration strategy
 *
 * Schema JSON is exported to `core/database/schemas` and committed, which is what allows Room to
 * verify a migration and what makes a schema change visible in review as a diff.
 *
 * Destructive migration is **never** enabled, in debug or release. It is tempting during
 * development and it silently deletes user data — for this app that means every countdown the
 * user created and every widget on their home screen going blank after an update. Every version
 * bump gets a real [androidx.room.migration.Migration] and a test that walks the old schema
 * forward with real data in it.
 *
 * The rules for changing the schema:
 *
 * 1. Bump [VERSION].
 * 2. Add a `Migration(n, n+1)` to `Migrations.kt`.
 * 3. Add a migration test that inserts data at version `n` and asserts it survives.
 * 4. Commit the newly exported schema JSON alongside the change.
 *
 * Additive changes — a new nullable column, a new table — are cheap. Renaming or retyping a
 * column requires the create-copy-drop-rename dance, and the test is what proves it worked.
 */
@Database(
    entities = [
        EventEntity::class,
        WidgetBindingEntity::class,
        ReminderEntity::class,
    ],
    version = CountFlowDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CountFlowDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun widgetBindingDao(): WidgetBindingDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        /** Current schema version. Bump this and add a migration; never reset it. */
        const val VERSION: Int = 2

        /** On-disk filename. */
        const val NAME: String = "countflow.db"
    }
}
