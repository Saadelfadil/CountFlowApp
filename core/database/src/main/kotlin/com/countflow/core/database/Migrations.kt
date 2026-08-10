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
 * Every schema migration, in order.
 *
 * Each entry must be paired with a test that inserts real rows at the old version and asserts
 * they survive. A migration that compiles is not a migration that works.
 */
internal val CountFlowMigrations: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
