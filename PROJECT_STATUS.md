# CountFlow — Project Status

**Permanent project overview. Updated every session; information is added, never deleted.**

CountFlow is a premium Android countdown widget app. The app itself is lightweight; the widgets
are the product. Users create countdown events and display them as home-screen widgets, with
Android 16 lockscreen and Always-On Display as later targets.

---

## At a glance

| | |
|---|---|
| **Current milestone** | 3 of 9 complete |
| **Last session** | Session 4 — 2026-08-08 |
| **Build status** | ✅ `assembleDebug` succeeds |
| **Lint** | 0 errors, 10 accepted warnings |
| **Tests** | 179 passing, 0 failing. `:core:domain` 99.5% line coverage, gated at 95% |
| **Runtime** | ✅ API 36 emulator, Session 4: 14 end-to-end checks across create, validate, search, filter, edit |
| **Overall progress** | ~36% |

---

## Document map

Read in this order when picking the project up cold:

| Document | What it is |
|---|---|
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
:widget:engine ──► :core:common

:core:data ──► :core:domain, :core:database, :core:common
:core:database ──► :core:domain, :core:common
:core:notifications, :core:analytics, :core:billing ──► :core:common
:core:domain ──► nothing
```

`:core:domain` is a pure Kotlin/JVM module, not an Android library. That is deliberate: an
accidental `import android.*` in the domain layer is a compile error rather than something a
reviewer has to catch (D-003).

**Module status**

| Module | State |
|---|---|
| `:app` | Application, MainActivity, NavHost |
| `:core:common` | Dispatchers, application scope, logging facade, `Clock` provision |
| `:core:designsystem` | Theme, typography, shapes, **token-to-text formatting** |
| `:core:domain` | **Model, countdown engine, validation, repository contracts** |
| `:core:database` | **Room: 3 entities, 3 DAOs, converters, schema v1** |
| `:core:data` | **Repository implementations, mappers, DataStore preferences** |
| `:feature:events` | **Home list, create/edit form, two ViewModels, UI mapper** |
| `:feature:settings` `:feature:premium` | Navigation + placeholder screens |
| `:core:notifications` `:core:analytics` `:core:billing` `:widget:engine` `:widget:glance` | Empty scaffolds — boundaries established, code arrives on the roadmap schedule (TD-002) |

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

## What does not exist yet

No widgets, no notifications, no settings, no billing. Within the events feature: no delete or
archive gesture (TD-008), no accent-colour picker, and no live widget preview — the last two wait
for the widget renderer they would preview. Several UI strings are not localised (TD-007).

## Where the important logic lives

| Question | File |
|---|---|
| How is time until an event computed? | `core/domain/…/countdown/CountdownEngine.kt` |
| Why is a day count not a duration division? | Same file, plus `CountdownEngineCalendarTest` |
| How do all-day and timed events differ? | `core/domain/…/model/EventTarget.kt` |
| What does the widget display? | `CountdownResult.calendarDaysRemaining` |
| Where are label thresholds set? | `core/domain/…/countdown/CountdownConfig.kt` |
| What is the schema? | `core/database/schemas/…/1.json` |
| What may be saved? | `core/domain/…/validation/EventValidator.kt` |
| How does a token become text? | `core/designsystem/…/format/CountdownLabelFormatter.kt` |
| What does Compose actually consume? | `feature/events/…/model/EventCardUiModel.kt` |

---

## Progress

```
Overall                      36%

Research & architecture     100%   Milestone 0
Project foundation          100%   Milestone 1
Domain & countdown engine   100%   Milestone 2
Database & persistence      100%   Milestone 2
Event CRUD / UI              85%   Milestone 3 (gestures and colour picker outstanding)
Widget engine                 0%   Milestone 4
Widget themes & sizes         0%   Milestone 5
Settings                      0%   Milestone 6
Notifications                 0%   Milestone 7
Optimization & a11y           0%   Milestone 8
Play Store                    0%   Milestone 9
Testing                      70%   domain, DAO, repository, ViewModel; no UI tests yet
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

Build: `./gradlew assembleDebug` · Lint: `./gradlew :app:lintDebug` · Tests: `./gradlew test`
