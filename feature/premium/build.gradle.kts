plugins {
    alias(libs.plugins.countflow.android.feature)
}

android {
    namespace = "com.countflow.feature.premium"
}

dependencies {
    implementation(projects.core.billing)
}
