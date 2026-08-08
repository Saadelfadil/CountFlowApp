plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.core.data"
}

// The data layer: it binds the domain's repository interfaces to Room and DataStore, and owns
// the mapping between storage shapes and domain models. Nothing above it knows Room exists.
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.database)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
