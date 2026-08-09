# Changelog

All notable changes to CountFlow are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The app is pre-release until
Milestone 9, so the version stays at `0.x`.

---

## [Unreleased]

Nothing yet. The rest of Milestone 5 (real WIDE device confirmation, same-event multi-style
verification, remaining polish) begins next, pending approval — see `TODO.md` P0.

---

## [0.4.5] — 2026-08-09 — Milestone 5B: responsive widget system

Session 10. Turned the 2×2 visual language from Milestone 5A into one coherent responsive system
across 2×1, 2×2, and 4×2 — 21 total Style × Size combinations, each with its own information
hierarchy rather than a mechanically stretched or shrunk copy of another size. Also closed two
pending product decisions: the countdown label hierarchy is now permanent, and BUG-011 stays open
by design until Milestone 8, with no further engineering time against Force Stop semantics.

### Added
- Migration to `SizeMode.Exact` (`CountdownGlanceWidget`), reading real `LocalSize` per
  composition instead of assuming one fixed footprint.
- `WidgetSizeClass` (`COMPACT`/`STANDARD`/`WIDE`) — a size classifier with breakpoints derived
  from real measured device dimensions (see Fixed, below).
- 14 new per-style layout composables (`*LayoutCompact`, `*LayoutWide` for all seven styles),
  bringing the total to 21 genuinely distinct compositions. See `docs/WIDGET_SIZE_MATRIX.md` for
  the full field-by-field matrix (Primary/Secondary/Progress/Alignment/Hidden fields/Typography).
- A content-fit type-scaling system (`contentFitScale()`) handling 4+ digit day counts and longer
  titles gracefully, engineered to introduce zero visual change for content already verified in
  Session 9's range.
- A responsive circular progress ring (shared `ProgressRing` helper) with 8px bitmap-size
  quantization and the LRU cache bumped 32→40 entries, bounding memory growth under
  `SizeMode.Exact`'s continuous size reporting.
- Manifest changes enabling real resizing: `resizeMode="horizontal|vertical"`,
  `maxResizeWidth="250dp"`, `maxResizeHeight="110dp"`.
- A size-aware configuration-screen preview, reading the widget's actual current
  `AppWidgetManager` size via `getAppWidgetOptions` rather than always rendering Standard.
- `docs/WIDGET_SIZE_MATRIX.md` and `docs/RESPONSIVE_WIDGET_REVIEW.md`.

### Fixed
- **The `WidgetSizeClass` dp thresholds did not match real device measurements.** First derived
  from Android's `dp = 70×cells − 30` cell-size formula, the same formula BUG-R009 (Session 8)
  used correctly — but this session's real launcher rendered roughly 2× the formula's prediction
  on both axes. Recalibrated against real measurements (172×104dp compact, 172×224dp standard).
  Documented explicitly as a second instance of BUG-R009's exact mistake — see `DECISIONS.md`
  D-055.
- **`MaterialLayoutCompact` rendered its headline invisible.** `StartIdentity`'s
  `.fillMaxWidth()`, safe at every other call site (all `Column` children), silently consumed the
  entire `Row` in the new compact layout, crowding out the sibling headline `Text`. Fixed by
  adding a `modifier` parameter (default unchanged); audited all 16 call sites.
- A duplicate headline rendered twice in `ProgressLayoutWide` (once beside the ring, once inside
  it) — found by a failing Robolectric test, fixed with a `showHeadline` parameter on the shared
  ring helper.
- Every pre-Session-10 Glance unit test had silently been exercising `WIDE` layouts, not
  `STANDARD` — Robolectric's default test size (349×455dp) classifies as `WIDE`. Every test now
  sets its size explicitly via `setAppWidgetSize`.

### Resolved (technical debt)
- **TD-012** — `resizeMode="none"` risk, moot now that resizing is fully supported (D-053/D-056).

### Known gaps (not fixed by design, or not reachable this session)
- **TD-016** — `WidgetSizeClass` thresholds are confirmed against exactly one emulator/launcher
  combination; nothing guarantees a different host agrees.
- **TD-017** — the 4×2 (`WIDE`) size has no real-device visual confirmation, Robolectric only.
  Three device-automation attempts to force a real WIDE placement did not succeed this session;
  `WIDE_MIN_WIDTH_DP` is a reasoned extrapolation, not a measured value.
- **BUG-011** stays open by explicit decision (D-052) — no further recovery engineering against
  Force Stop until Milestone 8's refresh infrastructure exists as a matter of course.
- The same-event-two-different-styles multi-widget case remains unit-tested only; this session
  confirmed the different-events case, on two different size classes simultaneously, on a real
  device.

### Tests
245 tests, 0 failures (up from 235). `:core:domain` line coverage unchanged at 97.0%, gated at
95%. Lint: 0 errors, 17 warnings, unchanged from Session 9.

---

## [0.4.4] — 2026-08-09 — Milestone 5A: widget visual redesign

Session 9. Scoped narrowly by the brief: make the one existing 2×2 widget look professionally
designed before any new size or capability — explicitly excluding 2×1/4×2, Live Updates,
lockscreen, billing, AdMob, notifications, cloud sync, and Wear OS. Directly answers Session 8's
two highest-severity `docs/PRODUCT_REVIEW.md` findings: four of seven styles pixel-identical, and
no widget-picker preview.

### Added
- Seven genuinely distinct per-style widget layouts (`CountdownWidgetLayouts.kt`) — different
  alignment, type scale, progress presentation, and corner radius per style, not one shared tree
  re-skinned with color. See `docs/WIDGET_DESIGN_GUIDE.md` for the design philosophy behind each.
- The first working determinate circular progress ring in this project's history
  (`CircularProgressRenderer`, `Canvas`→`Bitmap`, LRU-cached, quantized to whole percent) — closes
  `LIM-001`, open since Milestone 0.
- A widget-picker preview via `android:previewLayout` (`res/layout/widget_preview.xml`) — closes
  TD-014, called "mandatory" in this session's brief.
- A branded initial/loading layout (`res/layout/widget_initial_layout.xml`) replacing Glance's
  generic spinner — a partial, honestly-scoped response to BUG-011 (see Known gaps below).
- Target date rendering, wired for the first time via a new locale-aware `TargetDateFormatter`
  (`java.time.format.DateTimeFormatter`, no hardcoded English).
- The configuration screen's live preview: a new customize step (event → style/progress/toggles/
  accent) that redraws instantly via `WidgetRenderModelProvider.preview()`, a pure no-I/O render
  path that never writes to the database, preserving the existing no-orphan-bindings guarantee.
- An accent-colour picker (Dynamic Material You + eight curated presets), wired into both the
  create/edit form and the configuration screen's per-widget override.
- `docs/WIDGET_DESIGN_GUIDE.md` and `docs/WIDGET_DESIGN_REVIEW.md`.

### Fixed
- **The "Tomorrow / Tomorrow" and unnecessary "N / In N days" redundancies**, closed at the type
  level via a new shared `WidgetHeadline` content-hierarchy model, computed once before any style
  renders — not patched per fixture.
- **BUG-R011** — a word-shaped headline ("Completed", "Expired") wrapped mid-word at the type
  scale this session first shipped (`headlineSize()` now selects a smaller size for word-shaped
  headlines; `maxLines = 1` everywhere ellipsizes cleanly instead). Found and fixed within the
  same session it was introduced, on a real device.
- Two lint warnings introduced earlier in the session: `UseKtx` (`Bitmap.createBitmap` → the KTX
  extension) and `LocalContextResourcesRead` ×2 (`WidgetPreviewCard` now reads `LocalResources`
  instead of `LocalContext.current.resources`).

### Resolved (technical debt)
- **TD-011** — corner radius is now per-style: four styles track
  `android.R.dimen.system_app_widget_background_radius`, three (Glass, Rounded, Modern) keep a
  deliberate fixed override. See `DECISIONS.md` D-045.
- **TD-014** — widget-picker preview, above.
- **TD-015** — unused vertical space, closed as a side effect of the per-style redesign rather
  than a separate pass.

### Known gaps (not fixed by design)
- **BUG-011** remains open. The initial layout is now branded, but a Force-Stopped widget still
  does not recover on its own — Android cancels scheduled work on Force Stop, and defeating that
  was explicitly out of scope. Real resolution still needs Milestone 8's refresh infrastructure or
  a "tap to retry" affordance.
- Five of seven styles still share one background color (`(26, 27, 32)`, the ambient Material You
  surface) by design — differentiation this session is layout/typography/progress-presentation,
  not a seven-color palette. See `docs/WIDGET_DESIGN_REVIEW.md` for why this is the correct
  design, not a residual gap.
- Still one size only; 2×1/4×2 remain out of scope until the rest of Milestone 5 is approved.

### Tests
235 tests, 0 failures (up from 223) — `widget:engine` +1, `widget:glance` +11. `:core:domain`
line coverage unchanged at 97.0%, gated at 95%. Lint: 0 errors, 17 warnings (10 pre-existing +
7 new `HardcodedText`, both accepted and documented — the two new plain Android XML layouts this
session added are inherently static preview/loading surfaces, not user-facing localizable text).

---

## [0.4.3] — 2026-08-09 — Milestone 4.9: real product validation

Session 8. The first session with a stable, self-controlled real device — a locally-launched
emulator, not a remote or pooled one. Closed TD-010 (real widget placement) after three sessions,
found and fixed one Critical and one Medium defect neither reasoning nor any prior session's
partial device access could have caught, and produced the first real screenshot evidence and
product-quality assessment this project has had.

### Fixed
- **BUG-R009 (Critical)** — the widget's actual footprint was 3×2, not the 2×2 every document
  since Milestone 4 assumed. `minWidth="180dp"` was the 3-cell value under Android's own cell-size
  formula (`dp = 70×cells − 30`); corrected to `110dp`. Verified empirically: the real widget
  picker's own size label changed from "3 × 2" to "2 × 2" with no other change.
- **BUG-R010** — completed/expired events showed a full-strength accent-colored progress bar next
  to an already muted label. The bar now reuses the same muted color the label already computes.

### Added
- `docs/PRODUCT_REVIEW.md` — a critical, ranked "would you ship this" assessment: strengths,
  weaknesses (ranked Critical/High/Medium/Low), UX, visual quality, accessibility, performance,
  battery, and store/launch readiness.
- `docs/SCREENSHOT_GUIDE.md` — thirteen real, on-device, curated screenshots (cropped, committed
  to `docs/screenshots/`) covering Home Screen, Widget Added, Widget Empty, Widget Loading, Widget
  Config, Completed, Expired, Tomorrow, Next Week, Dark Mode, Light Mode, OLED, Material, and
  Glass, plus the reproducible SQL/adb recipe used to reach each state.

### Corrected
- **TD-013** — Session 7 concluded from Glance's API surface that long titles clip with no
  ellipsis. A real render showed otherwise: the underlying `RemoteViews` `TextView` ellipsizes by
  default. Marked corrected, not deleted, as a reminder that API-surface reading isn't a
  substitute for one real render.

### Known gaps (new this session, not fixed by design)
- **BUG-011 (High, open)** — after Force Stop, the widget sticks on a loading spinner until the
  app is reopened by any means. Scoped precisely: confirmed against Force Stop specifically
  (more aggressive than ordinary process reclaim); this device's Play-Store system image could not
  be rooted to test the gentler case for comparison.
- **TD-014** — no preview image in the widget picker; every other widget in the same picker shows
  one.
- **TD-015** — every widget state shows significant unused vertical space.
- Pixel-verified (not just suspected): four of seven named styles are visually identical
  (Minimal, Material, Progress, Modern) — strengthens the case for Milestone 5's already-planned
  per-style layout work.

### Tests
No new automated tests this session — both fixes are verified visually and empirically on-device
(`docs/SCREENSHOT_GUIDE.md`) rather than by unit test: BUG-R009 is a manifest XML value with no
JVM-testable surface, and BUG-R010 hits the same Glance-testing color-assertion gap noted above.
223 tests total, unchanged from Session 7, 0 failures. `:core:domain` unchanged at 97.0%.

---

## [0.4.2] — 2026-08-09 — Milestone 4.5: widget stabilization

Session 7. A stabilization/audit pass, not a feature session — the brief asked whether this
widget would be ready to ship tomorrow, not for anything new to add. No device was reachable this
session; every finding below came from a static architecture audit and a code-level UX/contrast
review, honestly separated from what could not be verified without a real device. Full detail in
`docs/WIDGET_REVIEW.md`, the new permanent audit record.

### Fixed
- **BUG-R008** — GLASS's translucent background (`0x99101418`, 60% opaque) could composite to
  roughly mid-gray over a light/white wallpaper, dropping the white text drawn on top to ~4.9:1
  contrast — barely above WCAG AA's 4.5:1 floor. Raised the alpha to `0xCC` (80% opaque, ~10.8:1
  in the same worst case). Regression test added.

### Changed
- `WidgetThemeResolver`, `WidgetProgressEngine`, `WidgetRenderMapper` narrowed from `public` to
  `internal` — none had a real caller outside `:widget:engine`. Verified empirically (both the
  module's own tests and `:widget:glance`'s compilation still succeed unchanged), not just
  reasoned about.

### Added
- `docs/WIDGET_REVIEW.md` — the full Milestone 4.5 audit: architecture, SOLID, dependency and
  injection graphs, UX/accessibility/contrast findings with actual computed numbers, a
  simplification pass (what was considered and deliberately left alone, and why), a lifecycle
  verification-status table naming the strongest real evidence for each of eleven scenarios, and
  an honest list of everything this session could not verify without a device.
- One new regression test asserting GLASS's background alpha never regresses below the contrast
  floor BUG-R008's fix depends on.

### Known gaps (opened this session, not fixed — see docs/WIDGET_REVIEW.md and KNOWN_ISSUES.md)
- **TD-011** — widget corner radii are hand-picked constants, not tied to the system's actual
  widget-clip radius (`android.R.dimen.system_app_widget_background_radius`), which ARCHITECTURE.md
  flagged as worth adopting and never was.
- **TD-012** — `resizeMode="none"` is not guaranteed to be honored by every launcher; no adaptive
  fallback exists since `SizeMode.Exact` isn't adopted yet (Milestone 5).
- **TD-013** — long titles clip at one line with no ellipsis; Glance 1.1.1's `Text` has no
  overflow parameter to set one.
- **TD-010** — real launcher placement still unverified; no device was reachable at all this
  session (Session 6's evidence stands as the most recent signal).

### Tests
1 new: GLASS's resolved background alpha must stay at or above the contrast-safe floor. 223
total, 0 failures. `:core:domain` unchanged at 97.0% line coverage.

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
