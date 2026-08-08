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
                versionCode = 1
                versionName = "0.1.0"
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
