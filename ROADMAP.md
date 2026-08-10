# CountFlow — Roadmap

Living document. Update the status column as milestones move.

**Status values:** `Not Started` · `In Progress` · `Completed` · `Blocked`

| # | Milestone | Status | Session |
|---|---|---|---|
| 0 | Research & architecture | **Completed** | 1 |
| 1 | Project foundation | **Completed** | 2 |
| 2 | Database, repositories, countdown engine | **Completed** | 3 |
| 3 | Event CRUD | **Completed** (finishing pass: lifecycle tabs, gestures, live preview) | 4, 11 |
| 4 | Widget engine | **Completed** | 5–6 |
| 4.5 | Widget stabilization | **Completed** | 7 |
| 4.9 | Real product validation | **Completed** | 8 |
| 5 | Multiple widgets | **In Progress** (5A, 5B done — visual redesign + responsive sizes; multi-widget polish remains) | 9–10 |
| 6 | Settings | Not Started | — |
| 7 | Notifications | **Completed** (basic reminders; recurring/custom offsets explicitly out of MVP scope) | 13 |
| 8 | Optimization | **In Progress** (background refresh infrastructure pulled forward and delivered; R8, Baseline Profiles, macrobenchmarks, full a11y pass remain) | 12 |
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

### Session 11 — finishing pass: event management polish

Both gaps this milestone left open in Session 4 finally had what they were waiting for: the
accent colour picker landed in Session 9 once the widget renderer existed, and the live preview
landed here, once the whole point of deferring it — a real `WidgetRenderModelProvider` to preview
against — had existed for six sessions. A deliberately smaller session than 9 or 10: product
polish on an already-built screen, not new architecture.

**Delivered:** the event list reorganized into three lifecycle tabs — Upcoming, Completed,
Archived (`EventLifecycleFilter`, D-058), replacing the two independent inclusion flags
`EventFilter` had carried unused-in-the-UI since Milestone 2; complete, archive, restore, and
delete reachable two ways on every row — a swipe gesture on Upcoming rows (Complete one direction,
Archive the other) and an overflow menu present on every row, on every tab, as the one path every
action always has, closing TD-008 (open since Session 4) with the accessible alternative the
brief explicitly required, not a swipe-only shortcut (D-060); a worded delete confirmation dialog
naming the real event and the real cascade behaviour, never a bare swipe-to-delete; four
tab-and-filter-aware empty states; and the create/edit form's own live widget preview
(`EventWidgetPreview`), reusing `WidgetRenderModelProvider.preview()` through a new, deliberately
narrow `:feature:events → :widget:engine` dependency rather than the heavier `:widget:glance`
module (D-059), confirmed on-device updating live as title, category, and accent color change.

**Verified on a real device, not just unit-tested:** the full lifecycle (create → edit → complete
→ archive → restore → delete, plus cancel-delete); both swipe directions and the menu's non-swipe
alternative (confirmed via the real accessibility semantics tree — the card's merged description
and the overflow button's own independent "More actions for X" description); a real placed
widget's behaviour across completing, archiving, and deleting its bound event, with **no
widget-specific code needed at all** — the existing render pipeline (D-039's forced-background
palette, D-051's label policy) already renders "Completed" correctly, archiving already leaves a
widget untouched by design (`Event.isArchived`'s own long-standing doc comment), and the existing
cascading foreign key already unbinds a deleted event's widgets back to the unconfigured state;
light and dark mode; and 200% font scale, which found one real bug — the new tab row didn't scroll
horizontally like the category row beside it, so "Archived" wrapped into a vertical letter stack —
found and fixed within the session.

245 → 259 tests, 0 failures. `:core:domain` unchanged at 97.0% (nothing this session touched
`:core:domain`'s testable surface beyond the new `EventLifecycleFilter` enum). `EditEventViewModel`
gained its first-ever unit tests, closing a gap `TODO.md` had named since the ViewModel existed.

**Not delivered, by explicit scope:** no widget sizing work (TD-016/TD-017 remain exactly where
Session 10 left them), no notifications, no billing, no premium gating, no Milestone 6 work.

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

## Milestone 5 — Multiple widgets · In Progress

Unlimited independent widgets. Seven themes (Minimal, Material, Glass, OLED, Progress, Rounded,
Modern). Sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges. The Canvas-drawn
progress ring required by LIM-001, quantized to whole percent and cached.

**Done when:** two widgets showing the same event in different styles update independently.

### Milestone 5A — Visual redesign of the existing 2×2 widget · Completed (Session 9)

Deliberately scoped narrower than the rest of Milestone 5: the brief was to make the **one 2×2
widget that already exists** look professionally designed before adding any new size or capability
— explicitly excluding 2×1/4×2, Live Updates, lockscreen, billing, AdMob, notifications, cloud
sync, and Wear OS. Directly answers Session 8's `docs/PRODUCT_REVIEW.md` findings #3 (four styles
pixel-identical) and #4 (no picker preview, called "mandatory" by name in this session's brief).

**Delivered:** seven genuinely different per-style layouts (`CountdownWidgetLayouts.kt`) —
different alignment, type scale, progress presentation, and corner radius per style, not a shared
tree re-skinned with color (D-045 through D-050 cover the specific decisions); a shared
`WidgetHeadline` content-hierarchy model closing both redundancy bugs the brief named by example
("Tomorrow / Tomorrow," and "7 / Next week" shown even when it added no information) (D-046); the
first working determinate circular progress ring in this project's history, closing `LIM-001`
(D-047); TD-011 (system-tracked corner radius, per-style) resolved; TD-014 (mandatory widget-picker
preview via `android:previewLayout`) resolved; TD-015 (unused vertical space) resolved as a side
effect of the same redesign; a live-reactive configuration screen upgrade — event picker → a new
customize step with style/progress/toggle/accent controls and an instant preview needing no save
(D-048, D-049); the deferred accent-color picker (Dynamic + eight presets, D-050); BUG-011
investigated and partially addressed (branded initial layout; the underlying force-stop recovery
gap deliberately left open, per instruction not to defeat platform semantics); one real bug found
and fixed within the session itself (a word-shaped headline wrapping mid-word, BUG-R011); and two
new documents, `docs/WIDGET_DESIGN_GUIDE.md` (per-style design philosophy) and
`docs/WIDGET_DESIGN_REVIEW.md` (before/after evidence and the session's Final Report).

**Verdict, from `docs/WIDGET_DESIGN_REVIEW.md`'s Final Report:** YES, the seven widgets would now
read as a professionally designed product beside Google's own widgets in the Pixel picker — with
BUG-011 (still open), five styles sharing one background color (by design, not a residual gap —
see the review's own table), and the still-single size stated plainly as what keeps this from being
a flawless yes rather than left unsaid.

**Not delivered, by explicit scope, not oversight:** 2×1/4×2 sizes, multiple independent widgets on
different events at once, and everything else Milestone 5 still owns. Session 9 stopped at the
brief's explicit instruction and awaits approval before continuing.

### Milestone 5B — Responsive widget system (2×1 / 2×2 / 4×2) · Completed (Session 10)

The mission: turn the 2×2 visual language Session 9 delivered into one coherent responsive system
across three sizes — "not simply three sizes... one coherent responsive design system," with an
explicit rule against mechanically stretching or shrinking the existing 2×2 layouts. Also closed
out two pending product decisions: the countdown label hierarchy is now permanent (D-051), and
BUG-011 is decided closed-until-Milestone-8 with no further recovery engineering against Android's
Force Stop semantics (D-052).

**Delivered:** migration from `SizeMode.Single` to `SizeMode.Exact` (D-053), reading real
`LocalSize` per composition instead of assuming one fixed footprint; a `WidgetSizeClass`
classifier (`COMPACT`/`STANDARD`/`WIDE`) with breakpoints derived from real measured device
dimensions, not Android's cell-size formula (see below); 21 total Style × Size compositions — 14
new layout composables (`*LayoutCompact`, `*LayoutWide` for all seven styles) alongside the
existing seven Standard layouts, each with its own information hierarchy rather than a scaled copy
(`docs/WIDGET_SIZE_MATRIX.md` documents all 21 as a full field-by-field matrix); a content-fit
type-scaling system (`contentFitScale()`) that gracefully handles 4+ digit day counts and longer
titles while leaving Session 9's already-tuned content completely unchanged; a responsive circular
progress ring (`ProgressRing`, shared by Standard and Wide) with 8px bitmap-size quantization and
an LRU cache bumped 32→40 entries to bound memory growth under continuous-size reporting (D-054);
manifest changes enabling real resizing (`resizeMode="horizontal|vertical"`, `maxResizeWidth`/
`maxResizeHeight`, D-056); a size-aware configuration-screen preview reading the widget's actual
current `AppWidgetManager` size rather than always rendering Standard (D-057).

**The headline finding, from real-device work:** the `WidgetSizeClass` dp thresholds, first
derived from Android's `dp = 70×cells − 30` cell-size formula (the same formula BUG-R009 used
correctly in Session 8), did not match what this session's real launcher actually rendered —
measured values were roughly 2× the formula's prediction on both axes. Recalibrated against real
measurements (172×104dp compact, 172×224dp standard) rather than the formula (D-055) — explicitly
documented as a second instance of exactly BUG-R009's mistake: a manifest/dp value asserted to
mean something specific needs checking against real, not just formula-predicted, platform
behavior. A second, related bug was found in the same investigation: `StartIdentity`'s
`.fillMaxWidth()`, safe at every other call site, silently crowded out its sibling headline inside
`MaterialLayoutCompact`'s `Row` — fixed with a modifier parameter, audited across all 16 call
sites.

**Also confirmed on-device this session:** two widgets on different events, in different size
classes, updating independently and simultaneously; font scale robustness at 130% and 200%; the
day-count/title/label edge cases the brief named (1/8/218/999+ days, long titles, Tomorrow/
Completed/Expired); light/dark across five named styles; accessibility re-verification (compact
never announces the secondary line or percentage even when the binding requests them).

**Not delivered, and said so plainly:** no real on-device confirmation of the 4×2 (WIDE) size
exists — Robolectric only. Three separate device-automation attempts to force a real WIDE
placement (drag-resize, remove-then-resize, adjusted-coordinate retry) did not succeed within the
session's time budget; `WIDE_MIN_WIDTH_DP` is a reasoned extrapolation from the compact/standard
measurements, not a measured value (TD-017). The `WidgetSizeClass` thresholds themselves are
confirmed on exactly one emulator/launcher combination (TD-016). The same-event-two-different-
styles case remains unit-tested only, not re-driven on a real device this session.

**Verdict, from `docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final Report:** all 21 combinations are safe
from clipping/overflow and each size reads as intentionally designed, not a stretched or
compressed sibling of another. See the review for the full seven-question answer, including which
size is strongest, which Style × Size combination is weakest, and what would change before Google
Play.

---

## Milestone 6 — Settings · Not Started

Theme and dark-mode selection, the dynamic-colour toggle, notification preferences, backup and
restore, About with licences and the privacy policy. Revisit `data_extraction_rules.xml` to
exclude widget bindings.

---

## Milestone 7 — Notifications · Completed (Session 13)

Basic event reminders: opt-in, per-event, four fixed offsets. Deliberately scoped small by the
brief — "this is NOT a notification-platform project" — explicitly excluding FCM, a server,
notification history, recurring reminders, and custom offsets.

**Delivered:** the `Reminder`/`ReminderType` domain model and `ReminderDao`'s active-reminder
query, both built in Milestone 2 well ahead of this milestone, needed almost no changes — the real
work was the scheduling and delivery infrastructure around them, plus one real correctness fix.
`Reminder.scheduledTime` now pins a timed event's reminder to the event's own authored zone rather
than the device's current one (D-065) — the all-day case already followed the device, unchanged,
per D-014's existing policy. A new `deliveredForScheduledTime` field, compared against a freshly
computed trigger rather than read as a plain flag, makes "never fire twice" and "never fire an
already-past trigger" the same code path with no special-casing. A new `:core:notifications`
module (`ReminderNotificationCoordinator`, `AndroidNotificationAlarmScheduler`,
`AndroidNotificationSender`, `ReminderNotificationReceiver`, `ReminderSafetyNetWorker`) coalesces
every pending reminder to one `AlarmManager` alarm — mirroring, deliberately not sharing,
Session 12's widget refresh scheduler pattern (D-067), since the two systems have genuinely
different correctness properties (a duplicate widget redraw is harmless; a duplicate notification
is not). A compact `ReminderSection` in the create/edit form (four checkboxes, no separate master
switch) requests `POST_NOTIFICATIONS` contextually — only when the first reminder is enabled, never
on app launch. Notification tap deep-links to the correct event via `MainActivity`'s
`onNewIntent`/`CountFlowNavHost`, reusing the "ask the `PackageManager` for the launcher intent"
technique D-035 already established for widget click targets. `docs/NOTIFICATION_ARCHITECTURE.md`
is the new permanent reference.

**The headline finding, from real-device work:** a timed event's reminder alarm's absolute epoch
was recorded, the device's real timezone was changed by five hours
(`adb shell cmd alarm set-timezone`), and the epoch was confirmed *unchanged* — a traveller's
reminder about a zone-pinned event stays pinned, exactly as D-065 intends. The first attempt at
this exact test, before the fix, would have shown the epoch shift by the full zone offset — this
is the one genuine bug this session found and fixed, not merely verified.

**Also confirmed on-device:** a reminder delivered with the app backgrounded and its process
killed (`am kill`, not Force Stop); a reactive second coordinator run, triggered by the delivery's
own database write, correctly redelivered nothing; a full device reboot correctly re-armed the
alarm and the reminder fired exactly once, no duplicate; a revoked `POST_NOTIFICATIONS` permission
produced no crash and no notification, with the reminder still marked resolved rather than stuck
retrying; tapping a delivered notification opened CountFlow directly to the correct event.

**Not delivered, by explicit scope:** recurring reminders, custom offsets, notification history,
FCM/server-side push, notification action buttons beyond tap-to-open, and localized notification
copy (consistent with this project's existing TD-007-tracked gap).

299 → 334 tests, 0 failures. `:core:domain` unchanged at 97.0% line coverage, gated at 95%. Lint:
0 errors, 17 warnings, unchanged since Session 9. First schema migration in this project's history
(`1.json` → `2.json`, one additive nullable column), with a real `MigrationTestHelper` test walking
the old schema forward with real data in it.

---

## Milestone 8 — Optimization · In Progress

Full scope: implement the rest of D-008's refresh strategy, enable R8 and write the keep rules
deferred in D-016, Baseline Profiles and macrobenchmarks against the sub-700 ms cold start and
sub-100 ms widget update budgets, and a full accessibility pass (TalkBack, large fonts, high
contrast, dynamic scaling).

### Background refresh infrastructure · Delivered (Session 12)

Pulled forward from this milestone's original scope, ahead of R8/Baseline Profiles/the a11y pass,
because reliable background widget refresh was judged independently valuable and independently
completable — not a redefinition of the milestone, a partial delivery of it. Full detail:
`docs/WIDGET_REFRESH_ARCHITECTURE.md`.

**Delivered:** a pure, zone-aware `CountdownEngine.nextTransitionAt` calculator (`:core:domain`,
D-062) answering "when does this event's countdown next meaningfully change," correctly handling
a real plateau bug (`CountdownLabel.NextWeek` can stay unchanged across several consecutive local
midnights) a naive "check the next midnight" implementation would have missed; `WidgetRefreshPlanner`
(`:widget:engine`) coalescing every placed widget to one global instant, deduplicated by event, so
widget count never multiplies wakeups; `WidgetRefreshCoordinator` orchestrating a full
redraw-then-reschedule cycle behind two Android-free seams; the real Android mechanics
(`:widget:glance`) — one `AlarmManager.setAndAllowWhileIdle` alarm, replaced rather than stacked on
every reschedule; one `BroadcastReceiver` handling both the alarm firing and the four genuine
system recovery broadcasts (boot, timezone, time, date); a `WorkManager` periodic safety net
(D-063).

**Verified on a real device, not just architecture:** a widget transitioned on its own with the
app backgrounded and its process killed, confirmed via `dumpsys alarm` (the alarm firing, `1`
wakeup recorded) and logcat (the refresh cycle running), then a home-screen screenshot; a full
device reboot correctly restored both placed widgets and re-armed a fresh alarm, with the app
never manually reopened; a real timezone change (`Africa/Casablanca` → `America/New_York`, via
`adb shell cmd alarm set-timezone`) correctly recomputed the schedule to the new zone's actual next
transition. That last test found a real, nine-session-old bug on its first attempt — a `@Singleton
Clock` (`Clock.systemDefaultZone()`, present since D-026 in Milestone 2) froze its resolved zone at
construction, so an already-running process silently kept computing against the *old* zone even
after correctly receiving and handling the `TIMEZONE_CHANGED` broadcast. Fixed
(`LiveDefaultZoneClock`, D-064), regression-tested, and re-verified on the same device the same
session.

**Not delivered, by explicit scope:** the launcher-ticked `Chronometer` for final-24-hours
second-level ticking (the other half of D-008); R8/keep rules; Baseline Profiles and
macrobenchmarks; the full accessibility pass; any profiler-measured battery, memory, or CPU
number (this session's battery answer is reasoned from the alarm design and confirmed alarm
*counts*, not an instrumented measurement — `docs/WIDGET_REFRESH_ARCHITECTURE.md` §11). Force
Stop recovery was explicitly not attempted, per the standing D-052 decision — this session's
scheduler makes *normal* background operation reliable, not a workaround for Android's own
Force Stop semantics.

299 tests, 0 failures (up from 259) — 20 new in `:core:domain`, 16 new in `:widget:engine`, 4 new
in `:core:common`. `:core:domain` line coverage unchanged at 97.0%, gated at 95%. Lint: 0 errors,
17 warnings, unchanged since Session 9.

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
