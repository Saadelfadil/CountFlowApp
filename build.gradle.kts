// Root build script.
//
// Plugins are declared with `apply false` so their versions resolve once, here, and modules
// apply them (directly or through a countflow.* convention plugin) without repeating versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // Declared here so the Room Gradle plugin lands on the shared buildscript classpath. The
    // convention plugin depends on it `compileOnly`, so without this its RoomExtension type is
    // absent at execution time and applying the convention fails.
    alias(libs.plugins.room) apply false
}
