plugins {
    alias(libs.plugins.countflow.android.library)
}

android {
    namespace = "com.countflow.widget.engine"
}

// Intentionally empty scaffold. The update scheduler, render-model mapping, and progress-ring
// renderer arrive in Milestones 4, 5, and 8.
//
// This module must stay free of CountFlow-specific concepts so it can be reused by a future
// widget app — it schedules and renders, it does not know what a countdown is.
// See ARCHITECTURE.md section 4.1 and DECISIONS.md (D-004).
dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.work.runtime.ktx)
}
