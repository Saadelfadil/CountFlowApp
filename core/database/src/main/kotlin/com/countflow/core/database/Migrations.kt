package com.countflow.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `reminders.delivered_for_scheduled_time` (Session 13, D-065) — nullable, so every
 * existing row (nothing has ever been delivered yet, since notification delivery did not exist
 * before this version) needs no backfill beyond SQLite's own default `NULL`.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE reminders ADD COLUMN delivered_for_scheduled_time INTEGER DEFAULT NULL",
        )
    }
}

/**
 * Adds `widget_bindings.has_accent_override`/`accent_argb_override` — a Samsung Galaxy A55
 * physical-device test found Accent presented as a per-widget Customize Widget setting with no
 * per-widget column to actually hold it, so a changed accent was silently dropped on Save. Two
 * columns, not one: `has_accent_override` distinguishes "no override" from "override is
 * Dynamic" (`accent_argb_override IS NULL` alone cannot, since Dynamic is itself represented by a
 * null ARGB — the same encoding `events.accent_argb` already uses one level up, without an
 * "override or not" state to also carry). Both default to `0`/`NULL`, matching every existing
 * binding's real behavior before this column existed: every widget inherited the event's accent
 * unconditionally.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE widget_bindings ADD COLUMN has_accent_override INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE widget_bindings ADD COLUMN accent_argb_override INTEGER DEFAULT NULL",
        )
    }
}

/**
 * Adds the `widget_style_entitlements` table — the rewarded-style foundation (no AdMob yet; see
 * `WidgetStyleEntitlementRepository`'s own KDoc). A row's existence for `(app_widget_id, style)`
 * *is* the entitlement; `ON DELETE CASCADE` off `widget_bindings.app_widget_id` means removing a
 * widget's binding — directly, or transitively through its event being deleted — cleans up its
 * entitlements too, with no extra application code (see `WidgetStyleEntitlementEntity`'s own
 * KDoc).
 *
 * Backfills one grandfathered entitlement per widget already effectively rendering with a
 * rewarded style (Glass/Rounded/Modern) before this table existed — "effectively" resolved the
 * same way [com.countflow.core.domain.model.WidgetBinding.resolveWidgetStyle] already does
 * (override, else the event's own default), expressed in SQL via `COALESCE` since this migration
 * has no access to that Kotlin function. Without this, every widget that happened to already be
 * using one of these three styles would find itself suddenly "locked" the first time anything
 * asks — not because it did anything wrong, but because the entitlement table simply did not
 * exist yet when it was configured. Free styles need no such backfill: nothing ever locks them,
 * migrated or not.
 */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS widget_style_entitlements (
                app_widget_id INTEGER NOT NULL,
                style TEXT NOT NULL,
                PRIMARY KEY(app_widget_id, style),
                FOREIGN KEY(app_widget_id) REFERENCES widget_bindings(app_widget_id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO widget_style_entitlements (app_widget_id, style)
            SELECT wb.app_widget_id, COALESCE(wb.widget_style_override, e.default_widget_style)
            FROM widget_bindings wb
            JOIN events e ON e.id = wb.event_id
            WHERE COALESCE(wb.widget_style_override, e.default_widget_style) IN ('GLASS', 'ROUNDED', 'MODERN')
            """.trimIndent(),
        )
    }
}

/**
 * Every schema migration, in order.
 *
 * Each entry must be paired with a test that inserts real rows at the old version and asserts
 * they survive. A migration that compiles is not a migration that works.
 */
internal val CountFlowMigrations: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
