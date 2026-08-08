pluginManagement {
    // Composite build supplying the countflow.* convention plugins.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CountFlow"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core — shared infrastructure. Depended on by features and widgets, never the reverse.
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:notifications")
include(":core:analytics")
include(":core:billing")

// Features — user-facing slices. Must not depend on each other.
include(":feature:events")
include(":feature:settings")
include(":feature:premium")

// Widgets — the product surface. :widget:engine is deliberately domain-agnostic so it can
// be reused by a future widget app; see ARCHITECTURE.md section 4.1.
include(":widget:engine")
include(":widget:glance")
