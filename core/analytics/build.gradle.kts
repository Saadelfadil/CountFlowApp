plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.core.analytics"
}

// Firebase is deliberately absent until Milestone 9. This module exposes an interface and a
// no-op implementation so the rest of the build never carries the Firebase SDK — which keeps
// cold start measurable and keeps every other module unit-testable. See DECISIONS.md (D-009).
dependencies {
    implementation(projects.core.common)
}
