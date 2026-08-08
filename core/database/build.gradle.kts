plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
    alias(libs.plugins.countflow.android.room)
}

android {
    namespace = "com.countflow.core.database"
}

// Depending on :core:domain is the correct direction — the database is part of the data layer,
// and the data layer depends on the domain, never the reverse.
//
// It buys type safety in the schema: columns hold real enums rather than bare strings, so a
// typo is a compile error and Room's converters own the single definition of how each enum is
// serialised. See DECISIONS.md (D-019).
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Room DAOs run against real SQLite under Robolectric. Cascades, collations, and the
    // empty-set behaviour of IN/NOT IN are SQLite semantics — asserting them requires the
    // real engine, not a fake.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
