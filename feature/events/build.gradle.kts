plugins {
    alias(libs.plugins.countflow.android.feature)
}

android {
    namespace = "com.countflow.feature.events"
}

dependencies {
    // Reuses WidgetRenderModelProvider.preview() for the create/edit form's live widget preview
    // (Session 11) — the same pure, no-I/O render path the widget configuration screen uses
    // (D-048). Pure Kotlin/JVM (D-033), so this adds no Android or Glance dependency weight.
    implementation(project(":widget:engine"))

    // rememberLauncherForActivityResult, for the reminder section's contextual
    // POST_NOTIFICATIONS request (Session 13).
    implementation(libs.androidx.activity.compose)
}
