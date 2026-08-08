plugins {
    alias(libs.plugins.countflow.android.library)
}

android {
    namespace = "com.countflow.core.database"
}

// Intentionally empty scaffold. Room entities, DAOs, and the database class arrive in
// Milestone 2 — Milestone 1 establishes the module boundary only.
dependencies {
    implementation(projects.core.common)
}
