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

        // The real CountFlowDatabase class now declares VERSION = 3, so opening it against a
        // version-2 file needs the rest of the path even though this test only cares about the
        // 1->2 step's own effect.
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
            TEST_DB,
        ).allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

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

    @Test
    fun `migrating 2 to 3 preserves existing widget bindings and adds the accent override columns as not-overridden`() {
        helper.createDatabase(TEST_DB, 2).apply {
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
                INSERT INTO widget_bindings (
                    app_widget_id, event_id, widget_style_override, progress_style_override,
                    show_title, show_emoji, show_target_date, show_percentage, created_at
                ) VALUES (
                    1, 'event-a', 'OLED', NULL, 1, 1, 0, 0, 0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
            TEST_DB,
        ).allowMainThreadQueries().addMigrations(MIGRATION_2_3).build()

        val cursor = db.query("SELECT * FROM widget_bindings WHERE app_widget_id = 1", null)
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("event_id"))).isEqualTo("event-a")
            assertThat(it.getString(it.getColumnIndexOrThrow("widget_style_override"))).isEqualTo("OLED")
            // The pre-existing row never had an accent override — the new columns must land in
            // exactly the state WidgetBindingMapper.toDomain() reads as "no override" (see
            // WidgetBindingEntity's own KDoc: has_accent_override=0, not merely
            // accent_argb_override=NULL, which alone is ambiguous with "override is Dynamic").
            assertThat(it.getInt(it.getColumnIndexOrThrow("has_accent_override"))).isEqualTo(0)
            assertThat(it.isNull(it.getColumnIndexOrThrow("accent_argb_override"))).isTrue()
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
