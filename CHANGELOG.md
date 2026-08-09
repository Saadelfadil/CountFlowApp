# Changelog

All notable changes to CountFlow are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The app is pre-release until
Milestone 9, so the version stays at `0.x`.

---

## [Unreleased]

Nothing yet. Milestone 5 begins multiple widgets, themes, and sizes.

---

## [0.4.1] — 2026-08-09 — Milestone 4 finishing pass: one production-quality widget

Session 6. No new features, no new abstractions — the brief was explicit that this session closes
Milestone 4, not starts Milestone 5. Every change either polishes the existing 2×2 widget or fixes
a value that was already computed upstream and silently never reached the screen.

### Added
- `docs/WIDGET_ARCHITECTURE.md` — the permanent reference for the whole widget system: data flow,
  render flow, refresh flow, binding and configuration lifecycles, Glance integration sharp
  edges, known limitations, and the forward-compatibility seams for multiple widgets, Android 16
  Live Updates, and lockscreen.
- Accessibility: the whole widget card now carries one `contentDescription`
  (`GlanceModifier.semantics { … }`) built from exactly its visible fields — e.g. "Trip to Kyoto.
  In 12 days. 40% complete." — instead of a screen reader piecing together unrelated text nodes.
- `WidgetRenderModel.showPercentageText`, wiring `WidgetBinding.showPercentage` (persisted since
  Milestone 2, never read until now) through to an actual percent-text element beside the
  progress bar.

### Fixed
- **BUG-R006** — `WidgetTheme.isHighContrast` had been computed by `WidgetThemeResolver` since
  Milestone 4 began but was never read by the renderer. Forced-background themes (OLED, Glass)
  also pulled on-colors tuned for the *dynamic* Material You surface rather than the background
  the theme itself forced, with no guarantee the two agreed. Fixed by resolving text and
  progress-track colors explicitly against `hasForcedBackground` and `isHighContrast`.
- **BUG-R007** — `WidgetBinding.showPercentage` could never have had any visible effect; see
  "Added" above.

### Changed
- Typography and spacing tightened to a consistent scale; the unconfigured ("tap to set up")
  placeholder redesigned to look intentional — centered, with a "+" mark and clearer copy —
  rather than provisional.

### Tests
5 new: 3 in `:widget:glance` (percent-text visibility, including that it stays hidden when
progress itself is off even if requested) and 2 in `:widget:engine` (the same conjunction at the
mapper level). 222 total, 0 failures. `:core:domain` unchanged at 97.0% line coverage.

### Performance
The pure-Kotlin compute path — `CountdownEngine.countdownAt` + `WidgetRenderMapper.map`, the
entire non-I/O decision of what a widget should show — measured at ~505ns/call (200,000
iterations, JIT-warmed). Not a performance concern at any plausible widget count; see
`docs/WIDGET_ARCHITECTURE.md` §3.

### Known gaps
- TD-010 (real launcher placement) remains open. This session's test device got materially
  further than Session 5's — `appwidget grantbind` succeeded and the user was
  `RUNNING_UNLOCKED`, unlike Session 5's outright failure — but the device connection was
  unstable and became unreachable before the drag-onto-home-screen flow could be completed. See
  KNOWN_ISSUES.md.

---

## [0.4.0] — 2026-08-09 — Milestone 4: widget engine

The first widget. `:widget:engine` is now pure Kotlin/JVM.

### Added

**Widget engine (`:widget:engine`, pure Kotlin/JVM)**
- `WidgetRenderModel` — the entire contract between the engine and the render layer.
- `WidgetTheme` and `WidgetThemeResolver`, resolving all seven named styles to background,
  accent, corner radius, and contrast. OLED forces true black regardless of accent.
- `WidgetProgress` and `WidgetProgressEngine`, computing fraction/percent/percentText once for
  both the linear bar today and the circular ring Milestone 5 adds.
- `WidgetRenderMapper`, applying `WidgetBinding.resolveWidgetStyle`/`resolveProgressStyle` on
  the widget side of the override-else-default precedence rule (D-013).
- `WidgetRenderModelProvider`, the "load event, load binding, generate model" pipeline —
  needing only `WidgetBindingRepository`, since `observeBoundWidget` already joins event and
  binding (built with this consumer in mind back in Milestone 2).
- `WidgetLifecycleCoordinator` and `WidgetRefreshScheduler` (interface), the seams for widget
  removal cleanup and for Milestone 8's eventual alarm-based scheduler.

**Widget (`:widget:glance`)**
- `CountdownGlanceWidget`, one 2×2 size, reaching Hilt through an `EntryPoint` since
  `GlanceAppWidget` cannot be constructor-injected (LIM-005).
- `CountdownGlanceWidgetReceiver`, cleaning up bindings in `onDeleted` by delegating to
  `WidgetLifecycleCoordinator` — the receiver itself carries no logic.
- `WidgetConfigurationActivity` and `WidgetConfigurationViewModel`: pick an event, bind it,
  redraw immediately, close. `RESULT_CANCELED` set before any UI shows is the entire mechanism
  behind "no orphan bindings" — a binding is only ever written in direct response to a
  selection, so a cancelled configuration has nothing to clean up.
- `GlanceWidgetRefreshScheduler`, the Milestone 4 implementation of the refresh seam: while the
  app is alive, redraw every widget when `observeEventsWithWidgets()` emits. Also runs
  `pruneOrphanedBindings` once at startup against the launcher's live widget ids.
- Click actions implemented as `ActionCallback`s rather than `actionStartActivity<MainActivity>()`,
  since `:widget:glance` cannot depend on `:app` without inverting the module graph.

**Domain**
- `CountdownResult.showsMeaningfulDayCount` and `EventCategory.defaultEmoji` moved from
  `:feature:events` into `:core:domain`, so the app and the widget can never disagree about
  either.
- `AppWidgetId.INVALID`.

**Tests** — 35 new: 30 in `:widget:engine` (plain JUnit, no Robolectric — the module has no
Android dependency to need it for), 5 in `:widget:glance` using Glance's own unit-test
framework, which renders a composable against a fabricated `WidgetRenderModel` with no
repository or Hilt involved. 217 total, 0 failures.

### Fixed
- `stringResource()`/`pluralStringResource()` replace a Session 4 pattern that manually read
  `LocalConfiguration.current` as a recomposition signal, which a lint check correctly flagged
  as insufficient.
- `WidgetConfigurationActivity.onEventBound` no longer crashes if the widget id cannot resolve
  to a `GlanceId` after a successful binding write; the redraw is now best-effort and the
  activity always confirms and closes.
- Removed an unused `plurals` resource from Session 4 that nothing ever rendered.

### Known gaps
- No widget has been placed through a real `AppWidgetHost`/launcher flow — the headless test
  AVD cannot satisfy the system's widget-bind unlock check. See KNOWN_ISSUES.md.
- Emoji rendering is unverified on real hardware (LIM-006).

---

## [0.3.0] — 2026-08-08 — Milestone 3: event CRUD

The first release with real screens. Events can be created, validated, listed, searched,
filtered, sorted, and edited.

### Added

**Domain**
- `EventValidator` with field-tagged error tokens, reporting every problem at once rather than
  stopping at the first. Emoji validation counts grapheme clusters, so a ZWJ family or a
  regional-indicator flag is correctly one emoji while a pasted word is rejected.

**Design system**
- `CountdownLabelFormatter` mapping countdown and category tokens to string resources, with
  plurals. Exposed both as a `Resources` function for the coming widget layer and as a composable
  that re-resolves on configuration change.

**Events feature**
- `EventCardUiModel` and an injectable `EventUiMapper`; Compose no longer sees domain objects.
- `EventsViewModel` with realtime search, four sort orders, category filtering, and two distinct
  empty states.
- `EditEventViewModel` serving both create and edit, with validation gating every write.
- Home screen: list, search field, category chips, sort menu, empty states, add button.
- Create/edit form: emoji, title, category, date picker, time picker, all-day toggle with an
  explanation of what it actually changes.

**Build and tests**
- Robolectric, pinned to SDK 34.
- 93 new tests: 32 DAO, 20 repository, 22 feature, and 19 domain. 179 in total.

### Changed
- `:core:designsystem` now depends on `:core:domain` and owns token-to-text formatting (D-028).
- `:core:database` and `:core:data` gained Robolectric-backed integration tests, closing TD-003.
- Gradle's `failOnNoDiscoveredTests` is disabled, because several modules are empty by design.

### Fixed
- Repository tests collided with two coroutine schedulers.
- The first search debounce would also have delayed sort taps and lagged the text field.

### Known gaps
- Sort names, validation messages, and empty-state copy are not yet localised (TD-007).
- Archive, complete, and delete exist on the ViewModel but have no UI gesture (TD-008).

---

## [0.2.0] — 2026-08-08 — Milestone 2: domain, countdown engine, and persistence

The business model everything else will depend on. Still no UI and no widgets by design.

### Added

**Domain (`:core:domain`, pure Kotlin/JVM)**
- `Event`, `EventTarget`, `WidgetBinding`, `Reminder`, and supporting types: `EventCategory`,
  `WidgetStyle`, `ProgressStyle`, `AccentColor`, `ReminderType`, and `EventId` / `ReminderId` /
  `AppWidgetId` value classes.
- `EventTarget` distinguishes all-day from timed events. All-day resolves in the device's
  current zone so it follows a traveller; timed stays pinned to its authored zone.
- `CountdownEngine` computing years, months, weeks, days, hours, minutes, seconds, percentage
  complete, elapsed, and remaining, plus a status and a display label.
- `CountdownResult` splits calendar remainders (`CountdownBreakdown`), unit totals
  (`CountdownTotals`), and the midnight count (`calendarDaysRemaining`) into distinct fields.
- `CountdownLabel` as a sealed token type the UI maps to string resources.
- `CountdownConfig` making label thresholds and the locale's first day of the week configurable.
- Repository contracts: `EventRepository`, `WidgetBindingRepository`, `ReminderRepository`,
  `PreferencesRepository`, with `EventFilter`, `EventSort`, `ThemeMode`, and `UserPreferences`.

**Database (`:core:database`)**
- `EventEntity`, `WidgetBindingEntity`, `ReminderEntity` with cascading foreign keys and indexes
  on the columns the list actually filters and sorts by.
- Type converters for enums, `Instant`, and `LocalTime`.
- Three DAOs. Filtering and sorting run in SQL, with sorting expressed as `CASE WHEN` arms so
  Room verifies the query at compile time.
- Schema export to `core/database/schemas`, committed, plus a `Migrations` list and a guard test
  asserting the migration count matches the schema version.

**Data (`:core:data`)**
- Repository implementations backed by Room, mapping on the IO dispatcher.
- Entity/domain mappers with round-trip test coverage.
- DataStore preferences with a corruption handler and defensive enum parsing.

**Build**
- `countflow.android.room` convention plugin configuring schema export centrally.
- Kover, gating `:core:domain` at 95% line coverage.
- `java.time.Clock` provided through DI so nothing calls `Instant.now()` directly.

**Tests** — 86 across three modules, 0 failures. `:core:domain` at 99.4% line coverage with the
`countdown` and `model` packages at 100%. The daylight-saving tests assert that a transition
genuinely falls inside the window they exercise.

### Changed
- `:core:database` now depends on `:core:domain`, so entity columns hold real enums rather than
  strings. Reversed mid-session; see D-019.
- `kotlinx-datetime` removed in favour of `java.time` (D-018).

### Fixed
- All-day events reported `IMMINENT` for their entire day, which would have shown a live ticking
  countdown to a moment already past.
- `remaining` counted upward for an event already in progress, reading as time still to wait.

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
