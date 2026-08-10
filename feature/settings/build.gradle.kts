plugins {
    alias(libs.plugins.countflow.android.feature)
}

android {
    namespace = "com.countflow.feature.settings"
}

dependencies {
    // NotificationManagerCompat.areNotificationsEnabled() and PackageManager.getPackageInfo(),
    // for the notification-status and app-version rows (Session 14). Both platform reads sit
    // behind NotificationStatusProvider/AppVersionProvider so SettingsViewModel itself is tested
    // with plain fakes, not Robolectric.
    implementation(libs.androidx.core.ktx)
}
