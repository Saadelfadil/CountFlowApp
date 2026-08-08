# Changelog

All notable changes to CountFlow are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The app is pre-release until
Milestone 9, so the version stays at `0.x`.

---

## [Unreleased]

Nothing yet. Milestone 2 begins the database, repositories, and countdown engine.

---

## [0.1.0] — 2026-08-08 — Milestone 1: project foundation

First buildable, runnable application. Infrastructure only: no business logic, no persistence,
and no widgets by design.

### Added

**Build**
- Gradle 9.6.1 wrapper with AGP 9.2.1, Kotlin 2.3.21, and KSP 2.3.11.
- Version catalog at `gradle/libs.versions.toml` as the single source of dependency versions.
- Six convention plugins in a `build-logic` composite build: `countflow.android.application`,
  `countflow.android.library`, `countflow.android.compose`, `countflow.android.feature`,
  `countflow.android.hilt`, and `countflow.jvm.library`.
- Fourteen modules with a downward-only dependency graph.
- Type-safe project accessors and Gradle configuration cache enabled.
- SDK levels: compileSdk 37, targetSdk 36, minSdk 31.

**Application**
- `CountFlowApplication` with `@HiltAndroidApp` and a `Configuration.Provider` supplying a
  `HiltWorkerFactory`, plus the manifest change removing WorkManager's default initializer.
- `MainActivity` as the single activity, edge-to-edge, with the system splash screen.
- Adaptive launcher icon with a monochrome layer, and app backup rules.

**Design system**
- Material 3 theme with dynamic colour on by default, plus brand light and dark fallback schemes
  built on a deep teal seed.
- Typography and shape scales, and a shared `PlaceholderScreen` component.

**Core**
- Injectable coroutine dispatcher qualifiers and an application-scoped `CoroutineScope` backed
  by a `SupervisorJob`.
- A `Logger` facade with a Logcat implementation, giving Crashlytics a single hook point later.

**Navigation**
- Navigation Compose with type-safe `@Serializable` routes across five destinations: Home,
  Create Event, Settings, Premium, and About.
- Each feature contributes destinations through a `NavGraphBuilder` extension and exposes
  navigation only as callbacks, so features never depend on one another.

**Documentation**
- `PROJECT_STATUS.md`, `DECISIONS.md`, `KNOWN_ISSUES.md`, `ROADMAP.md`, `TODO.md`, and this
  changelog. `ARCHITECTURE.md` and `SESSION_SUMMARY.md` carried over from Milestone 0.

### Changed
- Refresh strategy: the originally specified per-widget tiers are replaced by a launcher-ticked
  `Chronometer` for the final 24 hours plus one coalesced alarm for the whole app (D-008).
  `updatePeriodMillis` will not be used.
- Widget style moves from the event to the widget binding (D-013).
- Target time is stored as epoch millis plus zone id plus an all-day flag rather than a naive
  local date-time (D-014).
- The per-widget snapshot layer proposed in `ARCHITECTURE.md` is dropped for the MVP; Room is
  the single source of truth (D-002).

### Fixed
- Added `android:dataExtractionRules`, which lint flagged as missing.

### Notes
- `android.builtInKotlin=false` and `android.newDsl=false` are set in `gradle.properties`.
  Both are removed in AGP 10; migration is tracked as TD-001.
- Lint reports 0 errors and 11 warnings, all expected and documented in `KNOWN_ISSUES.md`.
- R8 is off for release builds until Milestone 8 (D-016).
