# CountFlow

## Session 2

Date: 2026-08-08
Current Milestone: **Milestone 1 — Project Foundation (COMPLETE)**

> **READ THIS FIRST:** Milestone 1 is done and verified — the app builds, installs, launches,
> and navigates on a real emulator. Do **not** start Milestone 2 without explicit approval.
> One P0 question is outstanding (see Pending Work): the brief said "Use Kotlin Native", which
> was interpreted as native Android Kotlin, not Kotlin Multiplatform.
>
> Authoritative documents, in reading order: `ARCHITECTURE.md` (design, wins on conflict),
> `PROJECT_STATUS.md` (permanent overview), then this file.

----------------------------------

## Objective

Build the complete project foundation: git, Gradle, version catalog, convention plugins, the
14-module structure, Hilt with WorkManager, the Material 3 theme, and Navigation Compose with
placeholder destinations. Infrastructure only — explicitly no Room entities, no domain model,
no widgets, no business logic.

----------------------------------

## Completed

**Build infrastructure**
- Initialized the git repository with a Kotlin/Android `.gitignore`; 7 commits made.
- Gradle 9.6.1 wrapper, `gradle.properties` (4 GB JVM, parallel, build cache, configuration cache).
- `gradle/libs.versions.toml` — every version verified to resolve against Maven Central and
  Google's Maven before use (20 artifacts checked; all 200 OK).
- Six convention plugins in a `build-logic` composite build.
- `settings.gradle.kts` with 14 modules, type-safe project accessors, and repository filtering.
- Installed `platforms;android-37.0` and `build-tools;37.0.0`, which were missing locally.

**Application**
- `CountFlowApplication` with `@HiltAndroidApp` and `Configuration.Provider` supplying a
  `HiltWorkerFactory`; manifest removes WorkManager's default initializer.
- `MainActivity` — single activity, edge-to-edge, system splash screen.
- Adaptive launcher icon with monochrome layer; `data_extraction_rules.xml`.

**Design system**
- Material 3 theme: dynamic colour on by default plus brand light/dark fallback schemes,
  typography, shapes, and a shared `PlaceholderScreen` with navigable actions.

**Core**
- Injectable dispatcher qualifiers and a `SupervisorJob`-backed application scope.
- `Logger` facade with a Logcat implementation, bound through Hilt.

**Navigation**
- Type-safe `@Serializable` routes; five destinations across three feature modules; each feature
  contributes a `NavGraphBuilder` extension and exposes navigation only as callbacks.

**Verification (all performed, not assumed)**
- `./gradlew assembleDebug` — BUILD SUCCESSFUL from clean.
- `./gradlew :app:lintDebug` with `abortOnError=true`, `checkDependencies=true` — 0 errors.
- `./gradlew test` — passes (no test classes yet).
- Installed on a booted API 36 emulator; cold launch 1506 ms, warm 1172 ms (debug build,
  software GPU — not representative of release).
- Drove the full navigation graph through `uiautomator`: Home → Settings → Premium → back →
  About → back → back → Home → New event → back. **All 9 transitions passed.**
- Captured light and dark screenshots; both render correctly. Dynamic colour confirmed active
  (the emulator wallpaper produced a blue scheme, not the teal brand fallback).
- Crash buffer empty; no `FATAL` or app `AndroidRuntime` entries.

**Documentation** — created `PROJECT_STATUS.md`, `DECISIONS.md` (17 entries), `KNOWN_ISSUES.md`,
`ROADMAP.md`, `CHANGELOG.md`, `TODO.md`; updated this file.

----------------------------------

## Files Created

29 Kotlin files, 1,097 lines. Full tree in "Current Project Structure" below. New this session:

```
.gitignore  gradle.properties  local.properties (git-ignored)
gradlew  gradlew.bat  gradle/wrapper/*  gradle/libs.versions.toml
settings.gradle.kts  build.gradle.kts

build-logic/settings.gradle.kts
build-logic/convention/build.gradle.kts
build-logic/convention/src/main/kotlin/
    AndroidApplicationConventionPlugin.kt   AndroidLibraryConventionPlugin.kt
    AndroidComposeConventionPlugin.kt       AndroidFeatureConventionPlugin.kt
    AndroidHiltConventionPlugin.kt          JvmLibraryConventionPlugin.kt
    com/countflow/gradle/KotlinAndroid.kt   com/countflow/gradle/ProjectExtensions.kt

app/build.gradle.kts  app/proguard-rules.pro  app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/countflow/app/{CountFlowApplication,MainActivity}.kt
app/src/main/kotlin/com/countflow/app/navigation/CountFlowNavHost.kt
app/src/main/res/{values/{strings,colors,themes}.xml, drawable/ic_launcher_foreground.xml,
                  mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml,
                  xml/data_extraction_rules.xml}

core/common/src/main/kotlin/com/countflow/core/common/
    di/{Dispatcher,CoroutinesModule,LoggingModule}.kt   log/{Logger,AndroidLogger}.kt
core/designsystem/src/main/kotlin/com/countflow/core/designsystem/
    theme/{Color,Type,Shape,Theme}.kt   component/PlaceholderScreen.kt

feature/events/src/.../{navigation/EventsNavigation,home/HomeScreen,create/CreateEventScreen}.kt
feature/settings/src/.../{navigation/SettingsNavigation,SettingsScreen,about/AboutScreen}.kt
feature/premium/src/.../{navigation/PremiumNavigation,PremiumScreen}.kt

build.gradle.kts in all 14 modules

PROJECT_STATUS.md  DECISIONS.md  KNOWN_ISSUES.md  ROADMAP.md  CHANGELOG.md  TODO.md
```

----------------------------------

## Files Modified

- `SESSION_SUMMARY.md` — rewritten from Session 1 to Session 2.
- `ARCHITECTURE.md` — unchanged. Still authoritative; note that D-002 supersedes its §4.3
  snapshot recommendation.

----------------------------------

## Architecture Decisions

Full detail with alternatives and tradeoffs is in `DECISIONS.md` (D-001 … D-017). The four that
changed how this session was built:

### Owner decisions applied
- **D-002 — no snapshot layer.** Room is the single source of truth; widgets will read it
  directly. This reverses the Session 1 recommendation and simplifies the MVP. The cost is that
  `:widget:glance` will depend on the data layer rather than a narrow contract, which weakens
  D-004's reusability goal. Recoverable later by inserting a mapper.
- **D-007 — Glance 1.1.1 stable**, not the 1.3.0-alpha02 Google's own sample uses.
- **D-008 — the replacement refresh strategy** is approved and scheduled for Milestone 8.
- **D-012 to D-015 — SDK levels and the three spec corrections** are locked in.

### Decisions forced by the toolchain during this session
- **D-005 — `android.builtInKotlin=false` and `android.newDsl=false`.** AGP 9 enables built-in
  Kotlin by default and then refuses to run `org.jetbrains.kotlin.android`. KSP, Hilt, and the
  Compose compiler are all documented against that plugin, and both Hilt and Room need KSP — so
  the built-in path would have put DI and persistence on unproven ground. Google's own
  `platform-samples` sets the same two flags at the same AGP version. **Both flags are removed
  in AGP 10** (TD-001).
- **D-006 — Kotlin 2.3.21, not 2.4.0.** KSP's newest release (2.3.11) is still built against
  Kotlin 2.3.20. Google's sample runs Kotlin 2.4.0 but does no annotation processing, so it
  proves nothing about KSP. A matched pair beats a newer minor version.
- **D-003 — `:core:domain` is a pure Kotlin/JVM module**, so an Android import there is a
  compile error rather than a review comment.
- **D-017 — convention plugins before modules.** With 14 modules, duplicated Gradle config would
  have drifted immediately. AGP 9's DSL changes hit the convention plugins first, which is
  exactly where you want to absorb them once instead of fourteen times.

----------------------------------

## Current Project Structure

```
CountFlow App/
├── ARCHITECTURE.md  PROJECT_STATUS.md  SESSION_SUMMARY.md
├── DECISIONS.md  KNOWN_ISSUES.md  ROADMAP.md  CHANGELOG.md  TODO.md
├── settings.gradle.kts  build.gradle.kts  gradle.properties
├── gradlew  gradlew.bat  gradle/{wrapper/, libs.versions.toml}
├── build-logic/                 6 convention plugins
├── app/                         Application, MainActivity, NavHost, resources
├── core/
│   ├── common/                  dispatchers, app scope, logging       [implemented]
│   ├── designsystem/            M3 theme + PlaceholderScreen          [implemented]
│   ├── domain/                  pure Kotlin/JVM                       [empty — M2]
│   ├── data/                                                          [empty — M2]
│   ├── database/                                                      [empty — M2]
│   ├── notifications/                                                 [empty — M7]
│   ├── analytics/                                                     [empty — M9]
│   └── billing/                                                       [empty — M9]
├── feature/
│   ├── events/                  Home + Create/Edit placeholders       [nav done]
│   ├── settings/                Settings + About placeholders         [nav done]
│   └── premium/                 Premium placeholder                   [nav done]
└── widget/
    ├── engine/                                                        [empty — M4]
    └── glance/                                                        [empty — M4]
```

----------------------------------

## Dependencies Added

All declared in `gradle/libs.versions.toml`; every version was verified to resolve before use.

| Component | Version | | Component | Version |
|---|---|---|---|---|
| AGP | 9.2.1 | | Hilt (Dagger) | 2.60.1 |
| Gradle | 9.6.1 | | androidx.hilt | 1.4.0 |
| Kotlin | 2.3.21 | | Room *(declared, unused)* | 2.8.4 |
| KSP | 2.3.11 | | DataStore *(declared, unused)* | 1.2.1 |
| compile/target/min SDK | 37 / 36 / 31 | | WorkManager | 2.11.2 |
| Compose BOM | 2026.06.01 | | Glance | 1.1.1 |
| Navigation Compose | 2.9.8 | | kotlinx-coroutines | 1.11.0 |
| Lifecycle | 2.11.0 | | kotlinx-serialization-json | 1.11.0 |
| Activity Compose | 1.13.0 | | kotlinx-datetime | 0.6.2 |
| core-splashscreen | 1.2.0 | | Testing: JUnit4 4.13.2, Turbine 1.2.1, Truth 1.4.5 | |

----------------------------------

## Current Features Working

- Builds, installs, and launches on API 36 with no crashes.
- Five destinations with a correct back stack — verified by driving the UI, not by inspection.
- Material 3 light and dark theming with dynamic colour active.
- Hilt component graph builds and injects at runtime.
- WorkManager configured with `HiltWorkerFactory`, ready for injected workers.

----------------------------------

## Pending Work

**P0 — blocks Session 3**
1. **Confirm "Kotlin Native" meant native Android, not Kotlin Multiplatform.** The brief listed
   it beside an entirely Android-specific stack (Glance, Room, Hilt, Compose), so it was built
   as a native Android app in Kotlin. If KMP was actually intended, that is foundational and
   Milestone 1 must be revisited before more code lands.
2. **Explicit approval to begin Milestone 2.**

**P1 — Milestone 2:** domain models, `CountdownEngine` plus its test suite, Room entities and
DAOs with a migration harness, repositories, DataStore.

**P2 and beyond:** Milestones 3–9 per `ROADMAP.md`. Full task breakdown in `TODO.md`.

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`. No runtime bugs. The items worth knowing before writing code:

**Technical debt**
- **TD-001 (High)** — `android.builtInKotlin=false` / `android.newDsl=false` are removed in
  AGP 10. Migration to built-in Kotlin is required before upgrading past AGP 9.x. Budget a
  full session.
- **TD-002 (Low)** — eight empty scaffold modules cost build time before they hold code.
- **TD-003 (Medium)** — no tests exist. Test infrastructure is wired; Milestone 2 opens with
  the `CountdownEngine` suite.
- **TD-004 (Low)** — the Gradle build cache served stale resource output after a resource
  *directory* rename. `clean` did not help; only `--no-build-cache` did. Sidestepped by keeping
  the conventional `-v26` mipmap qualifier. If a resource that exists on disk is ever reported
  as not found, test with `--no-build-cache` first.

**Platform limitations that constrain later milestones**
- **LIM-001** — Glance has no determinate circular progress. Rings must be Canvas bitmaps.
- **LIM-002** — `PeriodicWorkRequest` has a 15-minute floor; hence D-008.
- **LIM-003** — widget bitmaps are capped at `6 × screenW × screenH` bytes; exceeding it makes
  the host **silently drop the widget**.
- **LIM-004** — Glance has no autosizing text; the sample's workaround inflates a real View per
  call and uses deprecated `scaledDensity`.
- **LIM-005** — Hilt cannot inject `GlanceAppWidget`; use `EntryPointAccessors`.
- **LIM-006** — widget emoji rendering is launcher-dependent; verify on real hardware.

**Accepted lint warnings:** 0 errors, 11 warnings — `OldTargetApi` ×4 (targetSdk 36 with
compileSdk 37, deliberate), `AndroidGradlePluginVersion` ×5 and `NewerVersionAvailable` ×1
(informational), `ObsoleteSdkInt` ×1 (the `-v26` qualifier, kept on purpose).

----------------------------------

## Next Session Plan

**Step 0 is a gate.** Resolve the two P0 items above. Do not start Milestone 2 without approval.

Then, Milestone 2 — database, repositories, and the countdown engine:

1. Build `:core:domain` first, in pure Kotlin: `Event`, `WidgetBinding`, `EventCategory`,
   `WidgetStyle`, `ProgressStyle`, `AccentColor` (sealed: `Dynamic` or `Fixed(argb)`), and a
   `Clock` abstraction. Apply D-013 (style on the binding) and D-014 (epoch millis + zone id +
   all-day flag).
2. Write `CountdownEngine` and its tests **together**. Table-driven: DST transitions both
   directions, leap years, all-day versus timed, targets in a foreign zone, past events, and the
   exact boundaries where Today / Tomorrow / Next Week / Yesterday / Completed flip. Compare
   `LocalDate` in the target zone — never divide a duration (D-015).
3. Define repository interfaces in domain: `EventRepository`, `WidgetBindingRepository`,
   `PreferencesRepository`.
4. Add a `countflow.android.room` convention plugin, then `:core:database` — `EventEntity`,
   `WidgetBindingEntity` with a cascade-delete foreign key, DAOs, converters, schema export,
   and a migration test harness from version 1.
5. Implement `:core:data` — repositories returning `Flow`, DataStore for preferences, and
   entity/domain mappers with round-trip tests.
6. Verify: `./gradlew assembleDebug test :app:lintDebug` all green, and confirm `:core:domain`
   still has no Android dependency.
7. Write the Milestone 2 rationale note and update all seven documents.

Suggested commits: `feat(domain): event and countdown models`,
`feat(domain): countdown engine with timezone-aware labels`,
`test(domain): countdown engine dst and boundary coverage`,
`feat(database): room entities, daos, and migration harness`,
`feat(data): repositories and datastore preferences`.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified from clean this session:
- `./gradlew clean assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :app:lintDebug` → BUILD SUCCESSFUL, 0 errors, 11 accepted warnings
- `./gradlew test` → BUILD SUCCESSFUL
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (40 MB — debug tooling and no R8;
  not indicative of release size)
- No dependency cycles; Gradle configures all 14 modules cleanly.

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

- Tests written: **none**
- Tests passing: **none** (`./gradlew test` succeeds with no test classes)
- Tests failing: none

Infrastructure is in place — JUnit4, Turbine, Truth, and coroutines-test are wired into the
feature and JVM convention plugins; Compose UI test and `glance-appwidget-testing` are wired
where relevant. There is genuinely nothing to test yet: Milestone 1 is configuration, a theme,
and placeholder screens.

Milestone 2 opens with the `CountdownEngine` suite, which is pure Kotlin and testable from its
first line. Later: repositories against in-memory Room with Turbine, ViewModels with a
`TestDispatcher` rule, and widgets via `GlanceAppWidgetUnitTest`.

----------------------------------

## Git Status

Repository initialized this session on branch `master`. Seven commits, ordered so each builds
on the last:

```
5cf0d34  chore: initialize gradle wrapper and project scaffolding
6f46d61  build: add version catalog and convention plugins
eeedc94  build: add module structure and dependency graph
3d99b64  feat(designsystem): material 3 theme with dynamic color
1630563  feat(core): coroutine dispatchers and logging facade
bb7f170  feat(navigation): navigation graph with placeholder destinations
a91057d  feat(app): hilt application, workmanager config, and single activity
         docs: milestone 1 documentation        ← this commit
```

No remote configured. `local.properties` is git-ignored.

----------------------------------

## Developer Notes

- **`ARCHITECTURE.md` is authoritative**, with one exception: **D-002 supersedes its §4.3
  snapshot recommendation.** The owner chose Room as the single source of truth for the MVP.
  When the two disagree elsewhere, `ARCHITECTURE.md` wins.
- **Do not add versions inline in a module's build script.** Everything goes in
  `libs.versions.toml`. And verify a version resolves before committing to it — several
  plausible-looking versions were checked this session and the exercise paid for itself.
- **The convention plugins absorb AGP churn.** AGP 9 made `CommonExtension` non-generic and
  moved lambda overloads onto the concrete extensions, so shared config uses property access
  (`extension.defaultConfig.minSdk = …`) rather than block syntax. Expect DSL breakage to
  surface in `build-logic` first — that is the design working.
- **Verify at runtime, not just at compile time.** The navigation graph compiled fine while
  being completely unreachable, because the placeholder screens accepted navigation callbacks
  without exposing any way to trigger them. Only driving the UI caught it.
- **Two warnings are load-bearing information.** `OldTargetApi` and `ObsoleteSdkInt` are
  deliberate; do not "fix" them without reading `KNOWN_ISSUES.md` first. Removing the `-v26`
  qualifier is what triggered TD-004.
- **Emulator note.** `Pixel_9` AVD, API 36. It goes unresponsive under sustained Compose
  rendering with the software GPU; a clean restart with `-no-snapshot` fixes it. Cold-start
  numbers from it are not meaningful.
- Build: `./gradlew assembleDebug` · Lint: `./gradlew :app:lintDebug` · Tests: `./gradlew test`.

----------------------------------

## Estimated Progress

```
Overall Progress            12%

Research & Architecture    100%
Project Setup              100%
Database                     0%
Domain / Countdown Engine    0%
Widgets                      0%
UI                           8%    (theme + navigation shell; no real screens)
Notifications                0%
Billing                      0%
Testing                      0%
Play Store                   0%
```
