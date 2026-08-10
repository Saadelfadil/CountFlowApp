import com.android.build.api.dsl.ApplicationExtension
import com.countflow.gradle.configureKotlinAndroid
import com.countflow.gradle.int
import com.countflow.gradle.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the single application module (`:app`).
 *
 * Applies the Android application plugin, the shared Kotlin/Android configuration, and the
 * release build setup. Everything specific to CountFlow's identity — application id, version —
 * lives here so the module's own build script stays declarative.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)

            defaultConfig {
                targetSdk = libs.int("targetSdk")
                // Bumped alongside CHANGELOG.md — see its version history for what shipped in
                // each increment. Kept in sync manually since there is no release-automation
                // pipeline yet; the About screen (Session 14) reads these two values back from
                // the installed package, not from a hardcoded string, so this is the one place
                // that needs updating per release.
                versionCode = 14
                versionName = "0.4.9"
            }

            buildTypes {
                release {
                    // Left off for Milestone 1 so the first release build is verifiable without
                    // maintaining keep rules for a codebase that does not exist yet.
                    // Turned on with the R8 rules pass in Milestone 8.
                    isMinifyEnabled = false
                    isShrinkResources = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }
    }
}
