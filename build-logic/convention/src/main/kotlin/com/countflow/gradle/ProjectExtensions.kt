package com.countflow.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The `libs` version catalog, resolved from a convention plugin.
 *
 * Convention plugins are compiled before the catalog's generated type-safe accessors exist,
 * so they have to look the catalog up by name instead.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads an integer version (for example an SDK level) from the version catalog. */
internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

/** Reads a string version from the version catalog. */
internal fun VersionCatalog.string(alias: String): String =
    findVersion(alias).get().requiredVersion
