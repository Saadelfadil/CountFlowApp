plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.compose)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.widget.glance"
}

// Everything :widget:engine already made public — Event, WidgetBinding, EventId, AppWidgetId,
// the repository interfaces — is visible here transitively through :widget:engine's own
// `api(projects.core.domain)`; this module does not redeclare that dependency.
//
// Glance is pinned to 1.1.1 stable rather than 1.3.0-alpha02; everything CountFlow needs
// shipped in 1.1.0. See DECISIONS.md (D-007). Verified against the actual 1.1.1 AAR this
// session, not assumed: LinearProgressIndicator takes a plain Float, not a lambda, and the
// default SizeMode is Single, which is exactly the "one basic widget, not responsive" scope
// this milestone calls for.
dependencies {
    implementation(projects.widget.engine)
    implementation(projects.core.designsystem)
    implementation(projects.core.common)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Session 12: the periodic safety net behind the alarm-based refresh scheduler
    // (docs/WIDGET_REFRESH_ARCHITECTURE.md). Wired through Hilt the same way :app already does,
    // so WidgetRefreshSafetyNetWorker can take constructor dependencies.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.androidx.glance.preview)
    debugImplementation(libs.androidx.glance.appwidget.preview)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
