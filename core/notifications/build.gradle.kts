plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.core.notifications"
}

// Deliberately not a dependency on :core:designsystem: the only thing here that would reuse is
// CountdownLabelFormatter, and that module also carries Compose Material3 as an `api` dependency
// (D-028) — real weight for a background module with no UI of its own. Session 13 instead calls
// the real CountdownEngine directly for the label *decision* (reusing the actual business logic,
// not duplicating it) and keeps its own small, notification-specific text for that label, the
// same "reuse the fact, not the renderer" precedent EventWidgetPreview already set (D-059). See
// DECISIONS.md D-068.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    // WorkManager, wired through Hilt, for the periodic safety-net worker — the same reasoning
    // and shape as the widget refresh scheduler's own backstop (D-063).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
