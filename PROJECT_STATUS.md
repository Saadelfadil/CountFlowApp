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
| **Current milestone** | 4 of 9 complete |
| **Last session** | Session 5 — 2026-08-09 |
| **Build status** | ✅ `assembleDebug` succeeds |
| **Lint** | 0 errors, 10 accepted warnings |
| **Tests** | 217 passing, 0 failing. `:core:domain` 97.0% line coverage, gated at 95% |
| **Runtime** | ✅ API 36 emulator, Session 5: config activity's cancel/confirm/prune paths verified against the database; one real crash found and fixed |
| **Overall progress** | ~46% |

---

## Document map

Read in this order when picking the project up cold:

| Document | What it is |
|---|---|
| `AI_CONTEXT.md` | Single-file orientation for an AI assistant starting cold. Read this first. |
| `PROJECT_STATUS.md` | This file. Permanent overview. |
| `SESSION_SUMMARY.md` | What the most recent session did and what to do next. |
| `ARCHITECTURE.md` | The authoritative design. Wins over every other document on conflict. |
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

:feature:events    ─┐
:feature:settings  ─┼──► :core:designsystem, :core:domain, :core:common
:feature:premium   ─┘    (+ :core:billing)

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

**Module status**

| Module | State |
|---|---|
| `:app` | Application, MainActivity, NavHost, starts the widget refresh scheduler |
| `:core:common` | Dispatchers, application scope, logging facade, `Clock` provision |
| `:core:designsystem` | Theme, typography, shapes, token-to-text formatting |
| `:core:domain` | Model, countdown engine, validation, repository contracts |
| `:core:database` | Room: 3 entities, 3 DAOs, converters, schema v1 |
| `:core:data` | Repository implementations, mappers, DataStore preferences |
| `:feature:events` | Home list, create/edit form, two ViewModels, UI mapper |
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
- **A countdown widget exists.** One 2×2 size. Placing it through configuration writes the
  correct binding; cancelling writes nothing (verified against the database, not assumed); the
  widget redraws while the app is alive when its event changes.

## What does not exist yet

No notifications, no settings, no billing. Within the events feature: no delete or archive
gesture (TD-008), no accent-colour picker, no live widget preview. Within widgets: no widget has
been placed through a genuine `AppWidgetHost`/launcher flow (TD-010 — the headless test
environment could not satisfy the system's bind-permission check), only one size and no theme
differentiation beyond colour, and no D-008 alarm-based refresh. Several UI strings are not
localised (TD-007).

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

---

## Progress

```
Overall                      46%

Research & architecture     100%   Milestone 0
Project foundation          100%   Milestone 1
Domain & countdown engine   100%   Milestone 2
Database & persistence      100%   Milestone 2
Event CRUD / UI              85%   Milestone 3 (gestures and colour picker outstanding)
Widget engine                90%   Milestone 4 (real launcher placement unverified — TD-010)
Widget themes & sizes         0%   Milestone 5
Settings                      0%   Milestone 6
Notifications                 0%   Milestone 7
Optimization & a11y           0%   Milestone 8
Play Store                    0%   Milestone 9
Testing                      75%   domain, DAO, repository, ViewModel, widget engine, Glance UI
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

**Headless emulation (`-no-window`) cannot test real widget placement** — confirmed in Session 5;
see KNOWN_ISSUES.md TD-010. Use a GUI-mode emulator or a physical device for that specific check.

Build: `./gradlew assembleDebug` · Lint: `./gradlew :app:lintDebug` · Tests: `./gradlew test`
