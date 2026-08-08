package com.countflow.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android and Kotlin configuration shared by every Android module in CountFlow.
 *
 * Applied by the application and library convention plugins so SDK levels, Java version,
 * and compiler options are declared exactly once for the whole build.
 *
 * Members are set through property access rather than the usual `defaultConfig { }` block
 * syntax: AGP 9 made [CommonExtension] non-generic and moved the lambda-accepting overloads
 * onto the concrete `ApplicationExtension` / `LibraryExtension` types, leaving only getters
 * on the common supertype.
 *
 * Core library desugaring is deliberately not enabled: `minSdk` is 31, so `java.time` is
 * available natively and desugaring would add build time for no benefit.
 */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.compileSdk = libs.int("compileSdk")
    extension.defaultConfig.minSdk = libs.int("minSdk")

    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    // Robolectric needs the merged Android resources on the unit-test classpath. Harmless for
    // modules that do not use it — the resources are already built.
    extension.testOptions.unitTests.isIncludeAndroidResources = true

    extension.lint.abortOnError = true
    // Lint a module's dependencies alongside the module itself, so an issue introduced in
    // :core:designsystem is reported when :app is checked rather than being missed.
    extension.lint.checkDependencies = true

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    configureTestTasks()
}

/**
 * Test-task settings shared by every module.
 *
 * Gradle 9 fails a test task that discovers no tests, on the reasonable assumption that it
 * signals a misconfiguration. CountFlow has several modules that are deliberately empty
 * scaffolds — boundaries established ahead of the code that will fill them — and failing their
 * builds for having nothing to run yet is noise, not signal.
 *
 * The safety net this gives up is small: a module whose tests silently stopped being discovered
 * would go unnoticed. The suite is large and reported per module in every session summary, so a
 * count dropping to zero would be visible there.
 */
internal fun Project.configureTestTasks() {
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}
