plugins {
    alias(libs.plugins.countflow.jvm.library)
    alias(libs.plugins.kover)
}

// The countdown engine is the piece everything else depends on and the hardest to reason about,
// so its coverage is enforced rather than merely reported: the build fails below the threshold.
kover {
    reports {
        verify {
            rule("Countdown engine and domain model coverage") {
                minBound(95)
            }
        }
        filters {
            excludes {
                // Test fixtures are exercised by the tests themselves; counting them would
                // inflate the number without saying anything about the code under test.
                classes("com.countflow.core.domain.testing.*")
            }
        }
    }
}

// :core:domain is a pure Kotlin/JVM module by design. It holds models, the countdown engine,
// and repository interfaces, and depends on nothing that touches Android — so an accidental
// `import android.*` here is a compile error. See DECISIONS.md (D-003).
//
// Date and time come from java.time rather than kotlinx-datetime: it has the strongest DST and
// calendar semantics available, and at minSdk 31 it is native on device with no desugaring.
// See DECISIONS.md (D-018).
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
