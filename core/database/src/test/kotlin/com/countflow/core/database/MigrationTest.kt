package com.countflow.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Walks the schema forward with real data in it, per this module's own migration rule
 * (`CountFlowDatabase`'s KDoc): a migration that compiles is not a migration that works.
 */
@RunWith(RobolectricTestRunner::class)
internal class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CountFlowDatabase::class.java,
    )

    @Test
    fun `migrating 1 to 2 preserves existing reminders and adds the new column as null`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO events (
                    id, title, emoji, icon_key, category, target_epoch_millis, target_zone_id,
                    is_all_day, created_at, accent_argb, default_widget_style,
                    default_progress_style, reminders_enabled, is_archived, is_completed
                ) VALUES (
                    'event-a', 'Trip', NULL, NULL, 'GENERAL', 1000, 'UTC', 0, 0, NULL, 'MINIMAL',
                    'LINEAR', 1, 0, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reminders (id, event_id, type, time_of_day, is_enabled)
                VALUES ('reminder-a', 'event-a', 'SEVEN_DAYS', 32400, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
            TEST_DB,
        ).allowMainThreadQueries().addMigrations(MIGRATION_1_2).build()

        val cursor = db.query("SELECT * FROM reminders WHERE id = 'reminder-a'", null)
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("event_id"))).isEqualTo("event-a")
            assertThat(it.getString(it.getColumnIndexOrThrow("type"))).isEqualTo("SEVEN_DAYS")
            assertThat(it.getInt(it.getColumnIndexOrThrow("time_of_day"))).isEqualTo(32400)
            assertThat(it.getInt(it.getColumnIndexOrThrow("is_enabled"))).isEqualTo(1)
            assertThat(
                it.isNull(it.getColumnIndexOrThrow("delivered_for_scheduled_time")),
            ).isTrue()
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
