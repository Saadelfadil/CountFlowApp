plugins {
    alias(libs.plugins.countflow.android.library)
}

android {
    namespace = "com.countflow.core.notifications"
}

// Intentionally empty scaffold. Reminder scheduling and notification channels arrive in
// Milestone 7.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
}
