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
| **Current milestone** | Final MVP Release Audit (Session 15) complete: **MVP NOT READY** for public submission — two owner-action blockers (signing key, privacy-policy URL), no code-level blocker found |
| **Last session** | Session 15 — 2026-08-10 |
| **Build status** | ✅ `assembleDebug`, `assembleRelease` (unsigned), `bundleRelease` (unsigned) all succeed |
| **Lint** | 0 errors, 17 accepted warnings (unchanged since Session 9, all documented) |
| **Tests** | 340 passing, 0 failing (unchanged from Session 14 — this session wrote no production code). `:core:domain` 97.0% line coverage, gated at 95% |
| **Runtime** | ✅ **Session 15: a full, critical MVP release audit — feature freeze, no new product code.** Verified: release build succeeds and leaks no debug-only code; every permission and exported component is justified; zero network requests exist anywhere in the codebase; zero analytics/ads SDK; Room migration/FK/no-destructive-fallback all correct; the reminder pipeline was re-verified live end-to-end (created, permission granted, alarm scheduled, fired, correct "Expired" content, tap-to-open, no duplicate); a clean install through first event creation has no dead ends. Found and classified real gaps rather than hiding them to reach "MVP COMPLETE": two release **BLOCKERs** (no signing key, no privacy-policy URL — both owner actions), and HIGH findings on cold-start time (~2.5–2.8 s measured on a debug build, real number, needs a release-build re-measurement), 4×2 WIDE still unconfirmed on a real launcher after every session that has attempted it, and this session's own widget-picker automation not succeeding in placing a fresh widget. Full findings: `docs/MVP_RELEASE_AUDIT.md`; factual privacy basis: `docs/PRIVACY_DATA_INVENTORY.md`; practical follow-up: `docs/RELEASE_CHECKLIST.md` |
| **Overall progress** | ~66% |

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
| `docs/WIDGET_REFRESH_ARCHITECTURE.md` | The production background refresh system: next-transition calculation, coalescing, alarm lifecycle, system receivers, timezone/reboot/Force Stop behavior, battery reasoning, real-device evidence (Session 12). Read this before changing anything under `refresh/`. |
| `docs/NOTIFICATION_ARCHITECTURE.md` | Basic event reminders: trigger-time calculation, timezone policy, idempotent delivery, the coalesced-alarm scheduler, permission flow, real-device evidence (Session 13). Read this before changing anything under `:core:notifications`. |
| `docs/MVP_RELEASE_AUDIT.md` | The Final MVP Release Audit: every finding classified BLOCKER/HIGH/MEDIUM/LOW/POST-MVP, across 15 phases (release build, manifest, dependencies, data, widgets, background reliability, reminders, accessibility, performance, privacy, security, metadata, localisation, install/upgrade, engineering gate) (Session 15). Read this before any Play Store submission. |
| `docs/PRIVACY_DATA_INVENTORY.md` | Factual inventory of what CountFlow stores, processes, and transmits — the basis for the eventual Play Data Safety declaration and Privacy Policy (Session 15). |
| `docs/RELEASE_CHECKLIST.md` | Practical, checkbox-oriented pre-submission checklist (Session 15). |
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
:core:notifications ──► :core:domain, :core:common   (real code since Session 13; deliberately not :core:designsystem, D-068)
:core:analytics, :core:billing ──► :core:common
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
| `:core:common` | Dispatchers, application scope, logging facade, `Clock` provision (now zone-live, D-064) |
| `:core:designsystem` | Theme, typography, shapes, token-to-text formatting |
| `:core:domain` | Model, countdown engine, validation, repository contracts |
| `:core:database` | Room: 3 entities, 3 DAOs, converters, schema v2 (Session 13: `reminders.delivered_for_scheduled_time`) |
| `:core:data` | Repository implementations, mappers, DataStore preferences |
| `:feature:events` | Home list (three lifecycle tabs, swipe + menu actions), create/edit form (live widget preview), two ViewModels, UI mapper |
| `:widget:engine` | **Render model, theme resolver, progress engine, mapper, provider, lifecycle coordinator, refresh coalescing/orchestration** |
| `:widget:glance` | **First widget, configuration activity, production alarm-based refresh scheduler** |
| `:feature:settings` | **Real Settings + About screens (Session 14): appearance, notification status, app version — see `docs/NOTIFICATION_ARCHITECTURE.md` for what it deliberately does not surface** |
| `:feature:premium` | Navigation + placeholder screen (no entry point from Settings, D-071) |
| `:core:notifications` | **Reminder scheduling and delivery: coordinator, coalesced alarm, notification sender, channel, receiver, safety net** |
| `:core:analytics` `:core:billing` | Empty scaffolds — boundaries established, code arrives on the roadmap schedule (TD-002) |

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
- **Widgets now refresh reliably in the background, with real-device evidence.** Session 12 built
  the production scheduler D-008 always planned: a pure, zone-aware `nextTransitionAt` calculator
  (`:core:domain`, D-062) decides exactly when any bound event's countdown will next change,
  correctly handling a genuine plateau bug (`CountdownLabel.NextWeek` can stay unchanged across
  several consecutive midnights) that a naive "check the next midnight" implementation would have
  gotten wrong; `WidgetRefreshPlanner` (`:widget:engine`) coalesces every placed widget to one
  global instant, deduplicated by event, so widget count never multiplies wakeups; one real
  `AlarmManager.setAndAllowWhileIdle` alarm (D-063) fires it. Confirmed on-device: a widget
  transitioned to "Expired" with the app backgrounded and its process killed, with no manual
  reopen; a full reboot correctly restored both widgets and re-armed a fresh alarm; a real
  timezone change (`Africa/Casablanca` → `America/New_York`) correctly recomputed the schedule —
  the first attempt at that test found a real, nine-session-old bug (a `@Singleton Clock` that
  froze its zone at construction, D-064), fixed and re-verified the same session. Force Stop
  remains explicitly unrecovered (D-052, BUG-011 unchanged). See
  `docs/WIDGET_REFRESH_ARCHITECTURE.md`.
- **Basic event reminders exist, deliver reliably, and never fire twice, with real-device
  evidence.** Session 13 turned on the `Reminder`/`ReminderType` domain model and database schema
  Milestone 2 already built — four fixed offsets (30/7/1 days before, day of event), off by
  default, one worded checkbox each in the create/edit form. Found and fixed one real correctness
  bug in the process (D-065): a timed event's reminder used to recompute against whichever zone
  the device currently happened to be in rather than the event's own authored zone, which could
  have silently drifted a traveller's reminder — confirmed fixed via a real device timezone
  change, not just a unit test. A new `:core:notifications` module coalesces every pending
  reminder to one `AlarmManager` alarm (mirroring, not sharing, Session 12's widget scheduler
  pattern, D-067) and resolves each reminder against a freshly-computed trigger time rather than a
  plain delivered flag, so an edited event's date change correctly re-arms an already-fired
  reminder with no special-case code. Confirmed on-device: delivers with the app backgrounded and
  its process killed; survives a full reboot with no duplicate; the notification permission is
  requested only when the first reminder is enabled, never on app launch; a denied permission
  fails silently with no crash; tapping a delivered notification opens the correct event. See
  `docs/NOTIFICATION_ARCHITECTURE.md`.
- **A real Settings screen exists, with real-device evidence for every claim it makes.** Session
  14 turned on `PreferencesRepository`'s `ThemeMode`/`useDynamicColor` fields — stored and unit
  -tested since Milestone 2, never read by any screen until now. `MainActivity` reads them
  directly as Compose state and drives `CountFlowTheme`'s existing `darkTheme`/`dynamicColor`
  parameters; confirmed on-device to apply instantly, persist across a full process kill, and
  leave placed widgets' own theme/style/accent completely unaffected (D-069). Notification status
  reads `NotificationManagerCompat.areNotificationsEnabled()` — correct on every Android version,
  not just the ones with a runtime `POST_NOTIFICATIONS` prompt — and refreshes on every screen
  resume, confirmed flipping both directions after a real trip out to Android's own notification
  settings and back, with no CountFlow restart (D-070). About shows the installed package's real
  version, read from `PackageManager` rather than `:app`'s `BuildConfig` (D-072); Privacy Policy
  and Open-source licenses render as honest, visibly-disabled placeholders rather than fake links
  or a new dependency pulled in just to enumerate licenses (D-073).
- **A full, critical MVP release audit exists, with every finding classified and nothing hidden to
  claim readiness.** Session 15 was feature-freeze: no product code was written. The release build
  succeeds cleanly (debug, unsigned release APK, unsigned release AAB), leaks no debug-only code
  into release, and every manifest permission/exported component is individually justified. Zero
  network requests exist anywhere in the codebase and zero analytics/advertising SDK is present —
  confirmed by direct inspection, not assumption (`docs/PRIVACY_DATA_INVENTORY.md`). The reminder
  pipeline was re-verified live, end to end, on a genuine clean install. Two real release
  **blockers** were found and not downplayed — no signing key, no privacy-policy URL, both owner
  actions — alongside HIGH findings on cold-start time (measured, not reasoned: ~2.5–2.8 s on a
  debug build) and 4×2 WIDE remaining unconfirmed on a real launcher after every session that has
  attempted it. Full findings, classified BLOCKER/HIGH/MEDIUM/LOW/POST-MVP: `docs/MVP_RELEASE_AUDIT.md`.

## What does not exist yet

No billing, no notification history, no recurring or custom-offset reminders — see
`docs/NOTIFICATION_ARCHITECTURE.md`'s own scope note for the full MVP boundary. No backup/restore,
accounts, cloud sync, or localisation settings — Session 14 scoped Settings down to appearance,
notification status, and About only, per the brief's own explicit exclusions; backup/restore in
particular was in the original Milestone 6 spec (`ARCHITECTURE.md`) but is now out of MVP scope by
the same kind of deliberate scope narrowing Milestone 7 applied to notifications. No real privacy
-policy URL yet, and no open-source-license enumeration mechanism — both tracked as explicit
release-preparation items in `TODO.md`'s P0 section (D-073). Within widgets: the same-event-two-different-styles
case is unit-tested but not verified through real UI, and the 4×2 (WIDE) size has no real-device
visual confirmation at all — Robolectric only (TD-017); the `WidgetSizeClass` thresholds are
confirmed against exactly one emulator/launcher combination (TD-016). Background refresh now
exists (Session 12), but the launcher-ticked `Chronometer` half of D-008 (second-level ticking for
the final 24 hours) does not — a widget in its final hours updates at its next computed
transition, not every second. One open bug: the widget sticks on a loading spinner after Force
Stop until the app reopens — now at least a branded prompt rather than a generic spinner, but does
not recover on its own, and per D-052 this stays open by design (BUG-011); the real alarm-based
scheduler now exists but, per that same decision, is deliberately not used to defeat Force Stop's
own semantics. Several UI strings are not localised (TD-007). No widget performance, memory, or
battery *measurement* (numbers from a profiler) has ever been taken on a device — Session 12 gives
the first *reasoned* battery/wake-frequency answer (`docs/WIDGET_REFRESH_ARCHITECTURE.md` §11),
not an instrumented one; the device-heavy sessions so far (8, 9, 10, 11, 12) have each prioritised
a different kind of verification with zero or near-zero prior evidence over profiler numbers.

## Where the important logic lives

| Question | File |
|---|---|
| How is time until an event computed? | `core/domain/…/countdown/CountdownEngine.kt` |
| Why is a day count not a duration division? | Same file, plus `CountdownEngineCalendarTest` |
| How do all-day and timed events differ? | `core/domain/…/model/EventTarget.kt` |
| What does the widget display? | `widget/engine/…/model/WidgetRenderModel.kt` |
| Where are label thresholds set? | `core/domain/…/countdown/CountdownConfig.kt` |
| What is the schema? | `core/database/schemas/…/2.json` |
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
| When does a widget's countdown next need to change? | `core/domain/…/countdown/CountdownEngine.kt` `nextTransitionAt`, or `docs/WIDGET_REFRESH_ARCHITECTURE.md` §3 |
| How do N widgets on one event coalesce into one alarm? | `widget/engine/…/refresh/WidgetRefreshPlanner.kt`, or `docs/WIDGET_REFRESH_ARCHITECTURE.md` §4 |
| How does a background refresh actually happen, end to end? | `widget/engine/…/refresh/WidgetRefreshCoordinator.kt`, or `docs/WIDGET_REFRESH_ARCHITECTURE.md` §5–8 |
| Why does the injected `Clock`'s zone stay correct across a real timezone change? | `core/common/…/di/TimeModule.kt` `LiveDefaultZoneClock`, or DECISIONS.md D-064 |
| When does a reminder fire, and why is a timed event's reminder unaffected by device travel? | `core/domain/…/model/Reminder.kt` `scheduledTime`, or DECISIONS.md D-065 |
| How is a reminder guaranteed to never fire twice? | `core/domain/…/model/Reminder.kt` `isResolvedFor`/`markResolved`, or `docs/NOTIFICATION_ARCHITECTURE.md` §5 |
| How do N pending reminders coalesce into one alarm? | `core/notifications/…/ReminderNotificationCoordinator.kt`, or `docs/NOTIFICATION_ARCHITECTURE.md` §6 |
| Why are there two receivers for the same four system broadcasts? | DECISIONS.md D-067, or `docs/NOTIFICATION_ARCHITECTURE.md` §7 |
| How does the app-wide Theme/Dynamic Color preference reach the UI? | `app/…/MainActivity.kt`, or DECISIONS.md D-069 |
| Why don't placed widgets change with the app's Light/Dark setting? | DECISIONS.md D-069 |
| How does Settings know whether a notification will actually be seen? | `feature/settings/…/notification/NotificationStatusProvider.kt`, or DECISIONS.md D-070 |
| Why does the notification status row update without restarting the app? | `feature/settings/…/SettingsScreen.kt` `LifecycleResumeEffect`, or DECISIONS.md D-070 |
| Where does the About screen's version number actually come from? | `feature/settings/…/about/AndroidAppVersionProvider.kt`, or DECISIONS.md D-072 |

---

## Progress

```
Overall                      66%

Research & architecture     100%   Milestone 0
Project foundation          100%   Milestone 1
Domain & countdown engine   100%   Milestone 2
Database & persistence      100%   Milestone 2
Event CRUD / UI             100%   Milestone 3 (Session 11: lifecycle tabs, gestures, live preview)
Widget engine                98%   Milestone 4.9 (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget themes & sizes        70%   Milestone 5B of 5 (responsive 2×1/2×2/4×2 delivered; multi-widget polish remains)
Settings                      90%  Milestone 6 (Session 14: appearance, notification status, About delivered
                                     and device-verified; backup/restore, accounts, and localisation settings
                                     explicitly out of MVP scope — see "What does not exist yet")
Notifications                 90%  Milestone 7 (Session 13: basic 30/7/1-day/day-of reminders delivered and
                                     device-verified — docs/NOTIFICATION_ARCHITECTURE.md; recurring/custom
                                     offsets and a notification history remain explicitly out of MVP scope)
Optimization & a11y           25%  Milestone 8 (Session 12: background refresh infrastructure delivered and
                                     device-verified — Chronometer ticking, R8, Baseline Profiles, full a11y
                                     pass, and real performance numbers all remain)
Release readiness             20%  Milestone 8.9 (Session 15: full release audit complete — release build,
                                     manifest, dependencies, data, security, privacy, metadata, localisation
                                     all verified clean; two owner-action blockers found, not yet resolved —
                                     see docs/MVP_RELEASE_AUDIT.md)
Play Store                    0%   Milestone 9 (Firebase, AdMob, billing, store assets — none started)
Testing                      80%   domain, DAO, repository, ViewModel, widget engine, Glance UI
```

---

## Standing constraints

- **Play deadline.** From **31 August 2026**, Google Play requires new apps and updates to
  target API 36 or higher. Already satisfied.
- **Performance budgets.** Cold start under 700 ms; widget update under 100 ms. Neither is
  measured yet — benchmarks land in Milestone 8.
- **Battery.** `updatePeriodMillis` is never used. Exactly one coalesced `AlarmManager` alarm
  exists for the whole app at any time, scheduled for the next real countdown transition rather
  than a fixed interval (D-063, `docs/WIDGET_REFRESH_ARCHITECTURE.md` §11) — confirmed via
  `dumpsys alarm` throughout Session 12, not just designed. The launcher-ticked `Chronometer` half
  of D-008 (final-24-hours second-level ticking) is not yet built.
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
