# CountFlow — Project Status

**Permanent project overview. Updated every session; information is added, never deleted.**

CountFlow is a premium Android countdown widget app. The app itself is lightweight; the widgets
are the product. Users create countdown events and display them as home-screen widgets, with
Android 16 lockscreen and Always-On Display as later targets.

Read `AI_CONTEXT.md` first if you are an AI assistant picking this project up cold — it is the
single-file orientation this document map assumes you do not yet have.

---

## At a glance

| | |
|---|---|
| **Current milestone** | 5 of 9: Event CRUD/UI now considered 100% for V1 (Session 11); widget sizing/multi-widget polish remain the rest of Milestone 5 |
| **Last session** | Session 11 — 2026-08-09 |
| **Build status** | ✅ `assembleDebug` succeeds |
| **Lint** | 0 errors, 17 accepted warnings (unchanged since Session 9, all documented) |
| **Tests** | 259 passing, 0 failing (up from 245). `:core:domain` 97.0% line coverage, gated at 95% |
| **Runtime** | ✅ **Session 11: the event list is now organized into three lifecycle tabs (Upcoming/Completed/Archived) with full complete/archive/delete/restore support** — a swipe gesture on Upcoming rows plus an overflow menu present on every row, on every tab, as the one path every action (including delete, deliberately never swipeable) always has. The create/edit form now shows a live, size-agnostic widget preview reusing the real `WidgetRenderModelProvider.preview()` pipeline, confirmed on-device reacting to title, category, and accent-color changes in real time. Real-device work found and fixed one bug: the new tab row didn't scroll like the category row beside it, so 200% font scale wrapped "Archived" into a vertical letter stack. Widget behavior on complete/archive/delete confirmed correct against a real placed widget with no widget-specific code changes needed — the existing render pipeline and cascading-delete design already handled all three correctly |
| **Overall progress** | ~57% |

---

## Document map

Read in this order when picking the project up cold:

| Document | What it is |
|---|---|
| `AI_CONTEXT.md` | Single-file orientation for an AI assistant starting cold. Read this first. |
| `PROJECT_STATUS.md` | This file. Permanent overview. |
| `SESSION_SUMMARY.md` | What the most recent session did and what to do next. |
| `ARCHITECTURE.md` | The authoritative design. Wins over every other document on conflict. |
| `docs/WIDGET_ARCHITECTURE.md` | The widget system specifically: data/render/refresh flow, binding and configuration lifecycles, Glance sharp edges, forward compatibility. Read this before changing anything under `widget/`. |
| `docs/WIDGET_REVIEW.md` | The Milestone 4.5 stabilization audit (Session 7, no device — largely superseded by the two below). |
| `docs/PRODUCT_REVIEW.md` | The Milestone 4.9 product-quality verdict: ranked strengths/weaknesses, would-you-ship assessment, all backed by real device evidence. |
| `docs/SCREENSHOT_GUIDE.md` | Real, curated on-device screenshots (`docs/screenshots/`) of every major widget state, with the exact recipe to reproduce each (Session 8 baseline). |
| `docs/WIDGET_DESIGN_GUIDE.md` | Per-style design philosophy for all seven widget styles — why each layout exists, its hierarchy, what differentiates it, when to choose it (Session 9). |
| `docs/WIDGET_DESIGN_REVIEW.md` | Before/after evidence for the Milestone 5A redesign, plus the session's Final Report verdict (Session 9). |
| `docs/WIDGET_SIZE_MATRIX.md` | All 21 Style × Size combinations (Primary/Secondary/Progress/Alignment/Hidden fields/Typography), plus the real-vs-formula size table and content-fit rules (Session 10). |
| `docs/RESPONSIVE_WIDGET_REVIEW.md` | Real-device evidence for the responsive system — size-threshold correction, multi-widget, edge cases, accessibility — and the session's Final Report (Session 10). |
| `DECISIONS.md` | Every architectural decision with reason, alternatives, tradeoffs, status. |
| `ROADMAP.md` | Milestones 0–9 with status. |
| `TODO.md` | Prioritized outstanding work. |
| `KNOWN_ISSUES.md` | Bugs, technical debt, platform limitations, accepted warnings. |
| `CHANGELOG.md` | Semantic changelog per release. |

---

## Technology

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin (native Android) | 2.3.21 |
| Build | AGP / Gradle / KSP | 9.2.1 / 9.6.1 / 2.3.11 |
| SDK | compile / target / min | 37 / 36 / 31 |
| UI | Jetpack Compose (BOM) | 2026.06.01 |
| Widgets | Jetpack Glance | 1.1.1 stable |
| DI | Hilt / androidx.hilt | 2.60.1 / 1.4.0 |
| Database | Room | 2.8.4 |
| Preferences | DataStore | 1.2.1 |
| Date & time | `java.time` (not kotlinx-datetime) | JDK 17 |
| Coverage | Kover, gating `:core:domain` at 95% | 0.9.9 |
| Testing | JUnit4, Truth, Turbine, Robolectric | 4.16.1 |
| Background | WorkManager | 2.11.2 |
| Navigation | Navigation Compose, type-safe routes | 2.9.8 |
| Architecture | Clean Architecture + MVVM, unidirectional data flow | — |

**Not on the classpath by design:** Firebase, AdMob, Play Billing. All three are deferred to
Milestone 9 behind interfaces (D-009), so cold start stays measurable and every module stays
unit-testable.

---

## Module graph

Dependencies point downward only. Features never depend on each other.

```
:app  ──────────────► every feature, every core module, :widget:glance

:feature:events    ─┬──► :core:designsystem, :core:domain, :core:common
:feature:settings  ─┤    (+ :widget:engine for :feature:events only, D-059)
:feature:premium   ─┘    (+ :core:billing for :feature:premium)

:core:designsystem ──► :core:domain          (token-to-text formatting, D-028)

:widget:glance ──► :widget:engine, :core:designsystem, :core:common
:widget:engine ──► :core:domain              (pure Kotlin/JVM, D-033)

:core:data ──► :core:domain, :core:database, :core:common
:core:database ──► :core:domain, :core:common
:core:notifications, :core:analytics, :core:billing ──► :core:common
:core:domain ──► nothing
```

`:core:domain` and `:widget:engine` are both pure Kotlin/JVM modules, not Android libraries.
That is deliberate: an accidental `import android.*` in either is a compile error rather than
something a reviewer has to catch (D-003, D-033).

**Session 11 added one new edge**: `:feature:events → :widget:engine`, so the create/edit form
can reuse `WidgetRenderModelProvider.preview()` for its live widget preview rather than
re-deriving countdown or theme logic. Deliberately not `:feature:events → :widget:glance` — see
D-059 for why the heavier Glance/AppWidget module stays unreused outside `:app`.

**Module status**

| Module | State |
|---|---|
| `:app` | Application, MainActivity, NavHost, starts the widget refresh scheduler |
| `:core:common` | Dispatchers, application scope, logging facade, `Clock` provision |
| `:core:designsystem` | Theme, typography, shapes, token-to-text formatting |
| `:core:domain` | Model, countdown engine, validation, repository contracts |
| `:core:database` | Room: 3 entities, 3 DAOs, converters, schema v1 |
| `:core:data` | Repository implementations, mappers, DataStore preferences |
| `:feature:events` | Home list (three lifecycle tabs, swipe + menu actions), create/edit form (live widget preview), two ViewModels, UI mapper |
| `:widget:engine` | **Render model, theme resolver, progress engine, mapper, provider, lifecycle coordinator** |
| `:widget:glance` | **First widget, configuration activity, refresh scheduler** |
| `:feature:settings` `:feature:premium` | Navigation + placeholder screens |
| `:core:notifications` `:core:analytics` `:core:billing` | Empty scaffolds — boundaries established, code arrives on the roadmap schedule (TD-002) |

---

## What works today

- The app builds, installs, and launches on API 36 with no crashes; five destinations navigate
  with a correct back stack; Material 3 light and dark with dynamic colour active.
- Hilt builds its component graph and injects at runtime; WorkManager has a `HiltWorkerFactory`.
- **The countdown engine is complete and correct** across DST transitions in both directions,
  leap years and leap days, timezone travel, all-day versus timed events, month and year
  boundaries, and past events. 100% line coverage.
- **Persistence is complete**: Room with cascading foreign keys and a committed schema,
  repository implementations behind domain interfaces, and DataStore preferences — now verified
  against real SQLite rather than assumed.
- **Event CRUD works end to end**: create with validation, list, realtime search, category
  filter, four sort orders, and edit. Driven on a device, not just unit-tested.
- **Event lifecycle management is complete.** Session 11 organized the list into three tabs
  (Upcoming/Completed/Archived, `EventLifecycleFilter`, D-058), added complete/archive/restore/
  delete everywhere — a swipe gesture on Upcoming rows (Complete/Archive) plus an overflow menu
  present on every row on every tab, the one path every action (including delete, deliberately
  never a swipe target, D-060) always has. Delete requires real, worded confirmation. Verified
  on-device across the full lifecycle, including that completing/archiving/deleting a bound
  event correctly updates, leaves alone, or unbinds its widget respectively, with no
  widget-specific code needed — the existing render pipeline and cascading FK already did this
  correctly.
- **A countdown widget exists, has been through both an audit and a real-device validation
  pass, and now has evidence, not just architecture, behind the claim that it works.** One
  genuinely-2×2 size (fixed Session 8 — see BUG-R009). Session 8 achieved the first real
  widget placement through an actual system picker in this project's history, confirmed it
  survives an app update and a **full device reboot**, and found and fixed a Critical defect
  (the widget's real footprint had been 3×2, not 2×2, since Milestone 4) that no amount of
  reasoning could have caught without a real launcher. See `docs/PRODUCT_REVIEW.md` for the full,
  critically-ranked assessment and `docs/SCREENSHOT_GUIDE.md` for the visual evidence.
- **The widget now looks like a professionally designed product, not just a correctly
  functioning one.** Session 9 gave all seven named styles genuinely different layout
  philosophies (alignment, type scale, progress presentation, corner radius — not just paint),
  closed the two content-redundancy bugs the brief named ("Tomorrow / Tomorrow," unnecessary
  "N / In N days"), shipped the first working determinate circular progress ring in this
  project's history, added the "mandatory" widget-picker preview, and upgraded the configuration
  screen to a live, no-save-required preview. See `docs/WIDGET_DESIGN_GUIDE.md` (the philosophy)
  and `docs/WIDGET_DESIGN_REVIEW.md` (the before/after evidence and Final Report verdict: **YES**).
- **The widget is now a responsive system across three sizes, not one fixed layout that stretches
  or clips.** Session 10 migrated to `SizeMode.Exact` and gave 2×1, 2×2, and 4×2 each a genuinely
  distinct information hierarchy per style — 21 combinations total, not a mechanically scaled
  2×2. A content-fit type-scaling system handles 4+ digit day counts and longer titles gracefully.
  Real-device work found and fixed a size-classification bug (the dp thresholds derived from
  Android's cell-size *formula* did not match what a real launcher actually rendered — the same
  category of mistake as BUG-R009) and a `Row`/`fillMaxWidth` layout bug specific to the compact
  size. See `docs/WIDGET_SIZE_MATRIX.md` (the full matrix) and `docs/RESPONSIVE_WIDGET_REVIEW.md`
  (the real-device evidence and Final Report).
- **The create/edit form now previews the widget it configures.** Session 11 added
  `EventWidgetPreview`, a compact, inline card reusing `WidgetRenderModelProvider.preview()` — the
  same pure, no-I/O render path the widget configuration screen's own preview uses (D-048) — so
  the form's preview can never show something a real widget wouldn't, without depending on the
  heavier `:widget:glance` module (D-059). Updates live as title, category, and accent color
  change; confirmed on-device.

## What does not exist yet

No notifications, no settings, no billing. Within widgets: the same-event-two-different-styles
case is unit-tested but not verified through real UI, and the 4×2 (WIDE) size has no real-device
visual confirmation at all — Robolectric only (TD-017); the `WidgetSizeClass` thresholds are
confirmed against exactly one emulator/launcher combination (TD-016). No D-008 alarm-based
refresh. One open bug: the widget sticks on a loading spinner after Force Stop until the app
reopens — now at least a branded prompt rather than a generic spinner, but does not recover on its
own, and per D-052 this stays open by design until Milestone 8 (BUG-011). Several UI strings are
not localised (TD-007). No widget performance, memory, or battery number has ever been measured on
a device, in any session — the device-heavy sessions so far (8, 9, 10, 11) prioritised lifecycle,
visual, and product-completeness verification, which had zero or near-zero prior evidence, over
performance numbers.

## Where the important logic lives

| Question | File |
|---|---|
| How is time until an event computed? | `core/domain/…/countdown/CountdownEngine.kt` |
| Why is a day count not a duration division? | Same file, plus `CountdownEngineCalendarTest` |
| How do all-day and timed events differ? | `core/domain/…/model/EventTarget.kt` |
| What does the widget display? | `widget/engine/…/model/WidgetRenderModel.kt` |
| Where are label thresholds set? | `core/domain/…/countdown/CountdownConfig.kt` |
| What is the schema? | `core/database/schemas/…/1.json` |
| What may be saved? | `core/domain/…/validation/EventValidator.kt` |
| How does a token become text? | `core/designsystem/…/format/CountdownLabelFormatter.kt` |
| What does Compose actually consume? | `feature/events/…/model/EventCardUiModel.kt` |
| How does event data become a widget? | `widget/engine/…/provider/WidgetRenderModelProvider.kt` |
| Why does a widget style differ per binding? | `widget/engine/…/mapper/WidgetRenderMapper.kt` |
| How does "no orphan bindings" actually work? | `widget/glance/…/configuration/WidgetConfigurationActivity.kt` |
| How does a forced-background theme (OLED, Glass) pick text colors? | `widget/glance/…/CountdownWidgetContent.kt` `ForcedBackgroundPalette`, or `docs/WIDGET_ARCHITECTURE.md` §6 |
| What decides which fact is the headline vs. the supporting line? | `widget/glance/…/CountdownWidgetContent.kt` `resolveHeadline`, or `docs/WIDGET_DESIGN_GUIDE.md`'s hierarchy section |
| Why does each named style look different, layout-wise? | `widget/glance/…/CountdownWidgetLayouts.kt`, or `docs/WIDGET_DESIGN_GUIDE.md` |
| How is the determinate circular progress ring drawn? | `widget/glance/…/progress/CircularProgressRenderer.kt` |
| How does the configuration screen preview without saving? | `widget/engine/…/provider/WidgetRenderModelProvider.kt` `preview()`, or DECISIONS.md D-048 |
| How does a widget decide it's 2×1 vs 2×2 vs 4×2? | `widget/glance/…/WidgetSizeClass.kt` `classifyWidgetSize`, or `docs/WIDGET_SIZE_MATRIX.md` |
| Why do the dp thresholds look larger than the manifest's cell-size formula would suggest? | `WidgetSizeClass.kt`'s doc comment, or DECISIONS.md D-055 |
| Which of the three lifecycle tabs does an event belong to? | `core/domain/…/repository/EventRepository.kt` `EventLifecycleFilter`, or DECISIONS.md D-058 |
| How does the create/edit form preview a widget without depending on `:widget:glance`? | `feature/events/…/edit/EditEventViewModel.kt` `refreshPreview`, or DECISIONS.md D-059 |
| Why is delete never a swipe gesture? | `feature/events/…/home/EventCard.kt`, or DECISIONS.md D-060 |

---

## Progress

```
Overall                      57%

Research & architecture     100%   Milestone 0
Project foundation          100%   Milestone 1
Domain & countdown engine   100%   Milestone 2
Database & persistence      100%   Milestone 2
Event CRUD / UI             100%   Milestone 3 (Session 11: lifecycle tabs, gestures, live preview)
Widget engine                98%   Milestone 4.9 (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget themes & sizes        70%   Milestone 5B of 5 (responsive 2×1/2×2/4×2 delivered; multi-widget polish remains)
Settings                      0%   Milestone 6
Notifications                 0%   Milestone 7
Optimization & a11y           0%   Milestone 8
Play Store                    0%   Milestone 9
Testing                      80%   domain, DAO, repository, ViewModel, widget engine, Glance UI
```

---

## Standing constraints

- **Play deadline.** From **31 August 2026**, Google Play requires new apps and updates to
  target API 36 or higher. Already satisfied.
- **Performance budgets.** Cold start under 700 ms; widget update under 100 ms. Neither is
  measured yet — benchmarks land in Milestone 8.
- **Battery.** `updatePeriodMillis` is never used. The refresh strategy is a launcher-ticked
  `Chronometer` for the final 24 hours plus one coalesced alarm for the whole app (D-008).
- **Working agreement.** Architecture is approved before production code, work proceeds one
  milestone at a time with explicit approval to continue, and every session ends by updating
  all seven documents.

---

## Environment

Verified working on this machine as of Session 2:

- JDK 21.0.12 (Homebrew) at `/opt/homebrew/Cellar/openjdk@21/21.0.12/…`
- Android SDK at `~/Library/Android/sdk` with `platforms;android-37.0` and
  `build-tools;37.0.0` (both installed during Session 2), plus platforms 35, 36, 36.1
- Emulator AVD `Pixel_9`, API 36, arm64
- `local.properties` holds `sdk.dir` and is git-ignored

**Resolved in Session 8: a stable local emulator was there all along.** Sessions 5–7 each
depended on a *remote* device reachable only at `127.0.0.1:6555`, of steadily worsening
reliability (headless failure → unstable → fully unreachable). Session 7 concluded "no local
`emulator` binary or AVD exists in this environment" — that conclusion was wrong, and the mistake
is worth recording: it came from `which emulator` failing (the binary isn't on `PATH`), not from
checking `~/Library/Android/sdk/emulator/emulator` directly, which exists and works, alongside
the `Pixel_9` AVD already listed above (present since Session 2). Session 8 launched
`~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly, in GUI mode, and got a fully
stable device for the whole session — `adb devices -l` unchanged across dozens of commands, no
reconnects needed. **Prefer this over the remote `127.0.0.1:6555` device in every future
session**: check for the local binary explicitly (`ls ~/Library/Android/sdk/emulator/emulator`),
not just `which emulator`.

Build: `./gradlew assembleDebug` · Lint: `./gradlew :app:lintDebug` · Tests: `./gradlew test`
