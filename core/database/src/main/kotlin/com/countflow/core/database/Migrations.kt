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
 * Every schema migration, in order.
 *
 * Each entry must be paired with a test that inserts real rows at the old version and asserts
 * they survive. A migration that compiles is not a migration that works.
 */
internal val CountFlowMigrations: Array<Migration> = arrayOf(MIGRATION_1_2)
