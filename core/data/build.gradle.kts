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

    // Repositories are tested against real SQLite rather than a mocked DAO. A mock would only
    // confirm that the repository calls the method the test author expected it to call.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    // Test-only, deliberately. :core:database stays an `implementation` dependency so Room's
    // types cannot leak upward into features or widgets; the tests need them to build an
    // in-memory database, and only the tests get them.
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
}
