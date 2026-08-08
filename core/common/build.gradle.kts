plugins {
    alias(libs.plugins.countflow.android.library)
    alias(libs.plugins.countflow.android.hilt)
}

android {
    namespace = "com.countflow.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
}
