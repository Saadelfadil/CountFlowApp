# CountFlow — Roadmap

Living document. Update the status column as milestones move.

**Status values:** `Not Started` · `In Progress` · `Completed` · `Blocked`

| # | Milestone | Status | Session |
|---|---|---|---|
| 0 | Research & architecture | **Completed** | 1 |
| 1 | Project foundation | **Completed** | 2 |
| 2 | Database, repositories, countdown engine | **Completed** | 3 |
| 3 | Event CRUD | **Completed** | 4 |
| 4 | Widget engine | **Completed** | 5–6 |
| 4.5 | Widget stabilization | **Completed** | 7 |
| 4.9 | Real product validation | **Completed** | 8 |
| 5 | Multiple widgets | Not Started | — |
| 6 | Settings | Not Started | — |
| 7 | Notifications | Not Started | — |
| 8 | Optimization | Not Started | — |
| 9 | Play Store ready | Not Started | — |

---

## Milestone 0 — Research & architecture · Completed (Session 1)

Studied Google's App Widget sample in full and proposed the production architecture.
Three platform constraints found that changed the design: Glance has no determinate circular
progress, `PeriodicWorkRequest` cannot refresh every minute, and the sample's in-memory state
does not survive process death. Deliverable: `ARCHITECTURE.md`.

---

## Milestone 1 — Project foundation · Completed (Session 2)

Git, Gradle 9.6.1 wrapper, version catalog, six convention plugins in a `build-logic` composite,
14 modules with a downward-only dependency graph, Hilt with WorkManager configuration, the
Material 3 theme with dynamic color, and Navigation Compose with five reachable destinations.

Verified: `assembleDebug` succeeds, lint reports 0 errors, the app installs and launches on an
API 36 emulator with no crashes, all five destinations navigate correctly, and both light and
dark themes render.

---

## Milestone 2 — Database, repositories, countdown engine · Completed (Session 3)

**Reordered:** the countdown engine was pulled forward from Milestone 4. It is pure Kotlin, so
it was testable from day one, and everything downstream depends on it.

Delivered: the full domain model (`Event`, `EventTarget`, `WidgetBinding`, `Reminder`, and the
supporting enums and value classes); `CountdownEngine` at 100% line coverage; four repository
contracts; Room with three entities, cascading foreign keys, converters, and committed schema
export; repository implementations with round-trip-tested mappers; and DataStore preferences.

86 tests, 0 failures. `:core:domain` at 99.4% line coverage, enforced by a Kover gate.

Two defects were found by the tests and fixed: all-day events read as "starting soon" for their
whole day, and "remaining" counted upward once an event was in progress.

**Not delivered:** DAO and repository integration tests, which need Robolectric. Tracked as
TD-003 and scheduled for the start of Milestone 3.

---

## Milestone 3 — Event CRUD · Completed (Session 4)

Built in the order the owner set: tests first, then validation, then UI models, then ViewModels,
then screens — so neither validation nor presentation logic could end up living in a composable.

Delivered: Robolectric with 32 DAO tests and 20 repository tests closing TD-003; `EventValidator`
in the domain; `CountdownLabel` and category formatting through plural resources;
`EventCardUiModel` with an injectable mapper; `EventsViewModel` and `EditEventViewModel` over
`StateFlow`; and the home list and create/edit form.

179 tests, 0 failures. `:core:domain` at 99.5% line coverage. Verified on an API 36 emulator with
14 end-to-end checks driving create, validate, search, filter, and edit.

**Not delivered:** the live widget preview in the form, and the accent colour picker — both
deferred to Milestone 5, where the widget renderer they should preview will actually exist.
Archive, complete, and delete exist on the ViewModel but have no UI gesture yet.

---

## Milestone 4 — Widget engine · Completed (Session 5)

Built the pipeline the brief specified: Room → repository → countdown engine → widget engine →
render model → Glance. `:widget:engine` converted from an Android library to pure Kotlin/JVM,
mirroring D-003 — the same structural guarantee that made `:core:domain` trustworthy now
applies to what a widget is allowed to know.

Delivered: `WidgetRenderModel` (pure Kotlin, zero Android dependency), `WidgetThemeResolver`
(all seven styles), `WidgetProgressEngine`, `WidgetRenderMapper`, `WidgetRenderModelProvider`,
`WidgetLifecycleCoordinator`, the first `CountdownGlanceWidget` (2×2, one size), the
configuration Activity with a verified no-orphan-bindings guarantee, and a Milestone-4-scoped
refresh scheduler that keeps widgets current while the app is alive.

35 new tests (30 in `:widget:engine`, plain JUnit; 5 in `:widget:glance`, Glance's own testing
framework). 217 tests total, 0 failures.

Device testing found and fixed one real crash: the configuration Activity's post-save redraw
could throw if the widget id didn't resolve to a `GlanceId`, stranding an already-successful
binding write instead of finishing gracefully.

**Not delivered:** genuine placement through the real `AppWidgetHost`/launcher flow. The
headless AVD used for testing could not satisfy the system's widget-bind user-unlock check
(`adb shell appwidget grantbind` failed with `IllegalStateException: User -2 must be unlocked`,
confirmed to originate from the shell binary itself, not the app). Verified instead by launching
the configuration Activity directly with controlled widget ids and inspecting the database —
strong evidence for the actual code this milestone wrote, but not a substitute for one real
placement on a GUI emulator or physical device. See KNOWN_ISSUES.md.

**Watch resolved:** LIM-005 (bridged via a Hilt `EntryPoint`). **Watch still open:** LIM-003
(bitmap budget — relevant from Milestone 5's progress ring), LIM-006 (emoji rendering on real
hardware — still unverified).

### Session 6 — finishing pass

Session 5 built the architecture; Session 6's brief was explicit that the milestone was not done
until the single 2×2 widget was production quality, not just structurally correct. No new
abstractions were introduced (the session's own architectural rule) — every change either
polished the existing renderer or closed a gap where a value already computed upstream was
silently going nowhere.

Delivered: accessibility (`GlanceModifier.semantics { contentDescription = … }` on the whole
card, one coherent sentence rather than five unrelated text nodes); two real dead-field bugs
found and fixed — `WidgetTheme.isHighContrast` had been computed since the theme resolver was
written but never read by the renderer, and `WidgetBinding.showPercentage` had been persisted
since Milestone 2 but never reached the screen (D-039, D-040; KNOWN_ISSUES.md BUG-R006, BUG-R007);
typography and spacing tightened to a consistent scale; the unconfigured placeholder redesigned
to look intentional rather than provisional. 5 new tests, 222 total, 0 failures.
`docs/WIDGET_ARCHITECTURE.md` was written as the permanent reference for the whole widget system.

**Performance:** the pure-Kotlin compute path (`CountdownEngine.countdownAt` +
`WidgetRenderMapper.map`, everything that decides what a widget should show) measured at
~505ns/call, 200,000 iterations, JIT-warmed — confirming the entire non-I/O cost of producing a
render model is not a performance concern at any plausible widget count.

**Not delivered, despite a real attempt.** TD-010 (real launcher placement) remains open. A
GUI-mode test device this session reached further than Session 5's headless AVD ever did —
`appwidget grantbind` succeeded, the user was `RUNNING_UNLOCKED`, a real launcher rendered and
was screenshotted — but the device connection was unstable throughout and became fully
unreachable before the drag-onto-home-screen flow could be completed. See KNOWN_ISSUES.md TD-010
for the full account; this is the clear first item for the next session with stable device access.

---

## Milestone 4.5 — Widget stabilization · Completed (Session 7)

Not a feature milestone. The brief was explicit: treat the one widget that exists as if it were
shipping tomorrow, find everything wrong with it, fix what's genuinely a bug, and honestly
document everything that's a gap instead of a bug. No new sizes, styles, or capabilities — see
`docs/WIDGET_REVIEW.md` for the full audit and `TODO.md` for the exclusion list this session held
to.

**Constraint that shaped the whole session:** no device was reachable at all (Session 6's
emulator connection never came back, and no local emulator tooling exists to start a
replacement). Every task in the brief that needed a real launcher, real memory profiler, or real
elapsed wall-clock time (a reboot) could not be completed — and is documented as exactly that,
not quietly skipped or guessed at.

**What was still achieved without a device:** a full static architecture audit (module boundary,
dependency graph, injection graph, SOLID read on every Milestone 4 class) found the architecture
holds, with one real finding — three `:widget:engine` types (`WidgetThemeResolver`,
`WidgetProgressEngine`, `WidgetRenderMapper`) were `public` with no consumer outside the module,
tightened to `internal` and verified empirically (D-042). A UX/accessibility review computed
actual contrast ratios from the code's own color constants and found one real, High-severity
defect: GLASS's translucent background could drop below WCAG AA contrast over a light wallpaper —
found, fixed, and regression-tested (BUG-R008, D-041). Three new technical-debt items were opened
honestly rather than fixed reflexively, since fixing them would have meant new engineering this
milestone explicitly excluded (TD-011 corner radius, TD-012 resize-mode risk, TD-013 title
ellipsis).

**Not delivered, and said so plainly:** live verification of any of the eleven lifecycle
scenarios the brief listed, widget update/creation/refresh latency, memory usage, battery impact,
TalkBack's actual output, and testing across more than one launcher. `docs/WIDGET_REVIEW.md` §10
and §12 map every one of these to the strongest evidence that does exist (mostly Session 5's
device work, mostly one milestone old) rather than claiming coverage that isn't there.

---

## Milestone 4.9 — Real product validation · Completed (Session 8)

The brief: pretend CountFlow ships to Google Play tomorrow, and find every reason not to.
Explicitly a validation sprint, not a coding sprint — "no new features, only fixes required for
production quality."

**The headline change from every prior session: a real, stable, self-controlled device.** Sessions
5–7 depended on remote or pooled emulators of varying (mostly poor) reliability; Session 8 found a
local AVD (`Pixel_9`) and the `emulator` binary already present on this machine and launched it
directly, producing the first fully stable test device in this project's history. First priority
per the brief — device stability, verified before any code work — held for the entire session.

**Delivered:** the first real widget placement through an actual system picker and launcher
(closing TD-010 after three sessions); the first-ever confirmation that the widget survives a full
device reboot; confirmed survival of app update, force-stop-then-reopen, and dark/light theme
switching; a genuinely Critical defect found and fixed — the widget's real footprint had been 3×2,
not the 2×2 every session since Milestone 4 assumed, invisible until this session reached a real
widget picker (BUG-R009); a second, smaller visual-consistency defect found and fixed (BUG-R010,
completed/expired progress bar not de-emphasized); pixel-level verification (not eyeballing) of
all seven widget styles, which found four of them visually identical — real, quantified evidence
for work already planned in Milestone 5; a corrected prior finding (TD-013's "no ellipsis" claim
was wrong on a real render); and two new documents, `docs/PRODUCT_REVIEW.md` and
`docs/SCREENSHOT_GUIDE.md` (the latter with real, curated on-device screenshots committed to the
repo, cropped and pixel-sampled, not just described).

**Not delivered:** on-device performance/memory/CPU numbers — the session's device time went
toward lifecycle and visual verification, which had zero prior evidence, ahead of performance
numbers, which had zero prior evidence but lower severity. One real open defect was found and
*not* fixed this session, by design: after Force Stop, the widget sticks on a loading spinner
until the app reopens (BUG-011) — closing it needs either Milestone 8's eventual refresh
infrastructure or a deliberate new "tap to retry" affordance, both bigger than this session's
fix-small-things scope.

---

## Milestone 5 — Multiple widgets · Not Started

Unlimited independent widgets. Seven themes (Minimal, Material, Glass, OLED, Progress, Rounded,
Modern). Sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges. The Canvas-drawn
progress ring required by LIM-001, quantized to whole percent and cached.

**Done when:** two widgets showing the same event in different styles update independently.

---

## Milestone 6 — Settings · Not Started

Theme and dark-mode selection, the dynamic-colour toggle, notification preferences, backup and
restore, About with licences and the privacy policy. Revisit `data_extraction_rules.xml` to
exclude widget bindings.

---

## Milestone 7 — Notifications · Not Started

Opt-in reminders at 30 days, 7 days, tomorrow, and today. Notification channels, the
`POST_NOTIFICATIONS` runtime permission, and scheduling that shares the coalesced-alarm
infrastructure from D-008 rather than adding a second wakeup source.

---

## Milestone 8 — Optimization · Not Started

Implement the full D-008 refresh strategy: the launcher-ticked `Chronometer` for the final 24
hours, one coalesced alarm for the whole app, and event-driven invalidation. Enable R8 and write
the keep rules deferred in D-016. Baseline Profiles and macrobenchmarks against the sub-700 ms
cold start and sub-100 ms widget update budgets. Full accessibility pass: TalkBack, large fonts,
high contrast, dynamic scaling.

---

## Milestone 9 — Play Store ready · Not Started

Wire the real Firebase Analytics and Crashlytics behind the `:core:analytics` interface and
AdMob and Play Billing behind `:core:billing`, then measure the cold-start cost and defer
initialization off the critical path (D-009). Release signing, store listing, screenshots,
privacy policy.

---

## Explicitly deferred

**Android 16 Live Updates.** Not implemented, by instruction. The architecture keeps a single
seam for it: everything deciding what to show lives in `:widget:engine` as a pure function from
data to render model, and each surface — home screen, lockscreen, Live Updates — is a thin
adapter over that model. Adding Live Updates later means one new adapter and no changes to
domain, data, or engine.

**Lockscreen widgets and Always-On Display.** `widgetCategory="home_screen|keyguard"` will be
declared from Milestone 4 so the capability is present, but neither surface is a target before
Milestone 9.
