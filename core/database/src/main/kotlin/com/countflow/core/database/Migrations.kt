package com.countflow.core.database

import androidx.room.migration.Migration

/**
 * Every schema migration, in order.
 *
 * Empty at version 1 — there is nothing to migrate from yet. The list exists now so that adding
 * the first migration is a one-line change to an established pattern rather than a decision
 * taken under pressure during a release.
 *
 * Each entry must be paired with a test that inserts real rows at the old version and asserts
 * they survive. A migration that compiles is not a migration that works.
 */
internal val CountFlowMigrations: Array<Migration> = emptyArray()
