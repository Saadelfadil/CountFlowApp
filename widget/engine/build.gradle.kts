plugins {
    alias(libs.plugins.countflow.jvm.library)
}

// Pure Kotlin/JVM, not an Android library — the same structural argument as :core:domain
// (D-003): "no Android dependency" should be a compile error, not a rule someone has to
// remember. WidgetRenderModel, the theme resolver, and the progress engine need nothing an
// Android SDK would provide; the one thing that does — reading Context, AppWidgetManager,
// GlanceId — belongs in :widget:glance, which is exactly the boundary this module exists to
// enforce. See DECISIONS.md (D-033), which also explains why this reverses the android.library
// scaffold from Milestone 1.
//
// api, not implementation: :widget:glance needs Event, WidgetBinding, and the repository
// interfaces directly (for the configuration activity and the receiver), and should not have to
// redeclare a dependency this module already carries.
dependencies {
    api(projects.core.domain)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
