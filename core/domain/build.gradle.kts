plugins {
    alias(libs.plugins.countflow.jvm.library)
}

// :core:domain is a pure Kotlin/JVM module by design. It holds models, use cases, and
// repository interfaces, and depends on nothing — not even :core:common — so that an
// accidental Android import is a compile error. See DECISIONS.md (D-003).
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
}
