package com.countflow.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards the exported schema.
 *
 * Room only verifies a migration if the schema for the previous version was exported and
 * committed. The failure mode is quiet and late: someone bumps the version, forgets the export,
 * and the gap is discovered when a user's upgrade crashes. This test turns that into a red
 * build at the moment the version changes.
 *
 * It is a plain file assertion rather than a Room test on purpose — no Android runtime, so it
 * runs in the fast unit-test source set alongside everything else.
 */
class DatabaseSchemaTest {

    private val schemaDirectory =
        File("schemas/${CountFlowDatabase::class.qualifiedName}")

    @Test
    fun `the current schema version has been exported`() {
        val exported = File(schemaDirectory, "${CountFlowDatabase.VERSION}.json")

        assertThat(exported.exists()).isTrue()
    }

    @Test
    fun `every version up to the current one has an exported schema`() {
        // A missing intermediate version means there is no way to test the migration path
        // through it.
        val missing = (1..CountFlowDatabase.VERSION)
            .filterNot { File(schemaDirectory, "$it.json").exists() }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `the exported schema declares the version it claims to`() {
        val json = File(schemaDirectory, "${CountFlowDatabase.VERSION}.json").readText()

        assertThat(json).contains("\"version\": ${CountFlowDatabase.VERSION}")
    }

    @Test
    fun `the exported schema contains every table`() {
        val json = File(schemaDirectory, "${CountFlowDatabase.VERSION}.json").readText()

        listOf("events", "widget_bindings", "reminders", "widget_style_entitlements").forEach { table ->
            assertThat(json).contains("\"tableName\": \"$table\"")
        }
    }

    @Test
    fun `cascade deletes are declared on every child table`() {
        // Losing a cascade would let bindings, reminders, or entitlements outlive the row they
        // depend on, which the repositories are written to assume cannot happen. Three, not two,
        // since widget_style_entitlements cascades off widget_bindings.app_widget_id.
        val json = File(schemaDirectory, "${CountFlowDatabase.VERSION}.json").readText()
        val cascades = Regex("\"onDelete\":\\s*\"CASCADE\"").findAll(json).count()

        assertThat(cascades).isEqualTo(3)
    }

    @Test
    fun `the number of migrations matches the version history`() {
        // Version 1 needs no migrations; version N needs N-1 of them. Bumping the version
        // without adding a migration fails here rather than on a user's device.
        assertThat(CountFlowMigrations).hasLength(CountFlowDatabase.VERSION - 1)
    }
}
