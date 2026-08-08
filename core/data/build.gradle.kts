plugins {
    alias(libs.plugins.countflow.android.library)
}

android {
    namespace = "com.countflow.core.data"
}

// Intentionally empty scaffold. Repository implementations, DataStore, and mappers arrive
// in Milestone 2. The dependency direction is wired now so violations surface immediately:
// data may depend on domain and database, never the reverse.
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.database)
}
