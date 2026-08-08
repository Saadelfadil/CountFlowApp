plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.core.billing"
}

// Play Billing is deliberately absent. This module exposes the premium-status contract and a
// stub implementation that always reports "not premium", so premium gating can be written and
// tested long before billing is wired up. See DECISIONS.md (D-009).
dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
}
