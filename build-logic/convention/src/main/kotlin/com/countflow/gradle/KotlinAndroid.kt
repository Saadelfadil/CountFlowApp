package com.countflow.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
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

    extension.lint.abortOnError = true
    // Lint a module's dependencies alongside the module itself, so an issue introduced in
    // :core:designsystem is reported when :app is checked rather than being missed.
    extension.lint.checkDependencies = true

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
