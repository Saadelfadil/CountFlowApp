plugins {
    alias(libs.plugins.countflow.android.application)
    alias(libs.plugins.countflow.android.compose)
    alias(libs.plugins.countflow.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.countflow"

    defaultConfig {
        applicationId = "com.countflow"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // Features
    implementation(projects.feature.events)
    implementation(projects.feature.settings)
    implementation(projects.feature.premium)

    // Core
    implementation(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.analytics)
    implementation(projects.core.billing)
    implementation(projects.core.notifications)

    // Widgets
    implementation(projects.widget.glance)
    implementation(projects.widget.engine)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // WorkManager, wired through Hilt so workers can take constructor dependencies.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
