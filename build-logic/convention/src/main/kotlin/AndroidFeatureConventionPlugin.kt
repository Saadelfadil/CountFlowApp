import com.countflow.gradle.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Convention plugin for `:feature:*` modules.
 *
 * A feature is a self-contained slice of UI: Compose screens, their ViewModels, and their
 * navigation entry points. This plugin gives every feature the same baseline — Compose, Hilt,
 * navigation, lifecycle, and the shared design system — so feature build scripts only ever
 * declare a namespace and the extra core modules that particular feature needs.
 *
 * Features must not depend on each other. Cross-feature navigation goes through `:app`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("countflow.android.library")
        pluginManager.apply("countflow.android.compose")
        pluginManager.apply("countflow.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:common"))

            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("kotlinx-serialization-json").get())

            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("truth").get())
        }
    }
}
