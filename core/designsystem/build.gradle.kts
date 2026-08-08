plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.compose)
}

android {
    namespace = "com.countflow.core.designsystem"
}

dependencies {
    // api, not implementation: every feature that applies the theme also needs Material 3
    // types (ColorScheme, Typography) on its own compile classpath.
    api(libs.androidx.compose.material3)
    // Explicit rather than relying on material3's transitive graph, which has been narrowing
    // the icon set it pulls in. material-icons-extended is deliberately avoided — it is large
    // and would need shrinking rules for a handful of glyphs.
    api(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.core.ktx)
}
