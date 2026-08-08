plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.compose)
}

android {
    namespace = "com.countflow.widget.glance"
}

// Intentionally empty scaffold. Glance layouts, GlanceAppWidgets, receivers, and the widget
// configuration activity arrive in Milestone 4.
//
// Glance is pinned to 1.1.1 stable rather than 1.3.0-alpha02; everything CountFlow needs
// shipped in 1.1.0. See DECISIONS.md (D-007).
dependencies {
    implementation(projects.widget.engine)
    implementation(projects.core.designsystem)
    implementation(projects.core.common)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.glance.preview)
    debugImplementation(libs.androidx.glance.appwidget.preview)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
