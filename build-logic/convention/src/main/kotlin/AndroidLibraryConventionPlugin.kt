import com.android.build.api.dsl.LibraryExtension
import com.countflow.gradle.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for every Android library module.
 *
 * Modules applying this get the shared SDK levels, Java version, and compiler options.
 * They declare only their own namespace and dependencies.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            // Libraries deliberately do not declare targetSdk — it is an application-level
            // concern, and setting it per-library produces conflicting manifest merges.
            configureKotlinAndroid(this)
        }
    }
}
