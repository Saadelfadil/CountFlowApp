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
| **Current milestone** | 4.9 of 9 complete (real product validation; Milestone 5 not started) |
| **Last session** | Session 8 — 2026-08-09 |
| **Build status** | ✅ `assembleDebug` succeeds |
| **Lint** | 0 errors, 10 accepted warnings |
| **Tests** | 223 passing, 0 failing. `:core:domain` 97.0% line coverage, gated at 95% |
| **Runtime** | ✅ **Session 8: first stable, self-controlled real device** (local emulator). First real widget placement, first confirmed reboot survival, one Critical bug found and fixed (widget was 3×2, not 2×2 — BUG-R009), one open High-severity bug documented (stuck loading spinner after Force Stop — BUG-011). See `docs/PRODUCT_REVIEW.md` and `docs/SCREENSHOT_GUIDE.md` |
| **Overall progress** | ~49% |

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
| `docs/SCREENSHOT_GUIDE.md` | Real, curated on-device screenshots (`docs/screenshots/`) of every major widget state, with the exact recipe to reproduce each. |
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
- **A countdown widget exists, has been through both an audit and a real-device validation
  pass, and now has evidence, not just architecture, behind the claim that it works.** One
  genuinely-2×2 size (fixed this session — see BUG-R009). Session 8 achieved the first real
  widget placement through an actual system picker in this project's history, confirmed it
  survives an app update and a **full device reboot**, and found and fixed a Critical defect
  (the widget's real footprint had been 3×2, not 2×2, since Milestone 4) that no amount of
  reasoning could have caught without a real launcher. See `docs/PRODUCT_REVIEW.md` for the full,
  critically-ranked assessment and `docs/SCREENSHOT_GUIDE.md` for the visual evidence.

## What does not exist yet

No notifications, no settings, no billing. Within the events feature: no delete or archive
gesture (TD-008), no accent-colour picker, no live widget preview. Within widgets: only one size
and no theme differentiation beyond colour (four of seven named styles are now confirmed
pixel-identical, not just suspected — see `docs/PRODUCT_REVIEW.md`), no D-008 alarm-based refresh,
no system-matched corner radius (TD-011), no adaptive fallback if a launcher ignores
`resizeMode="none"` (TD-012), no preview image in the widget picker (TD-014), and noticeably unused
vertical space in every widget state (TD-015). One open bug: the widget sticks on a loading
spinner after Force Stop until the app reopens (BUG-011). Several UI strings are not localised
(TD-007). No widget performance, memory, or battery number has ever been measured on a device, in
any session — the one device-heavy session so far (8) prioritised lifecycle and visual
verification, which had zero prior evidence, over performance numbers.

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

---

## Progress

```
Overall                      49%

Research & architecture     100%   Milestone 0
Project foundation          100%   Milestone 1
Domain & countdown engine   100%   Milestone 2
Database & persistence      100%   Milestone 2
Event CRUD / UI              85%   Milestone 3 (gestures and colour picker outstanding)
Widget engine                98%   Milestone 4.9 (validated on a real device — docs/PRODUCT_REVIEW.md)
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
