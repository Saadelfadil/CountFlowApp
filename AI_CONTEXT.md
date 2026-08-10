# AI_CONTEXT.md

**Read this first if you are an AI assistant starting cold on CountFlow.** It is a synthesis,
not a replacement — every claim here traces to a fuller document, and where the two disagree,
that fuller document wins. `ARCHITECTURE.md` is the one exception: it wins over everything,
including this file, except where a later `DECISIONS.md` entry explicitly supersedes it.

---

## What this is, in two sentences

CountFlow is an Android countdown-widget app: users create events, and the events render as
home-screen widgets. The app is lightweight; the widgets are the product — everything about the
architecture optimises for that, not for the app screens being impressive.

## The one thing to understand before touching any code

**The countdown engine and the widget engine are both pure Kotlin/JVM modules with zero Android
dependency, enforced by the build system, not by convention.** `:core:domain` and
`:widget:engine` apply `countflow.jvm.library`, not an Android library plugin. An accidental
`import android.*` in either is a compile error. This is the single structural decision that
makes the rest of the codebase trustworthy: business logic cannot leak into a screen or a widget
renderer because the type system will not let it compile there. If you are about to write
countdown arithmetic, a "which label applies" decision, or a "what does this look like" rule
inside a Composable — stop. It almost certainly belongs in `:core:domain` or `:widget:engine`
instead, and probably already exists there.

## Module graph, compressed

```
:app ──► every feature, every core module, :widget:glance

:feature:events, :feature:settings, :feature:premium ──► :core:designsystem, :core:domain, :core:common
:feature:events ──► :widget:engine also                     (D-059: reuses preview(), not :widget:glance)
:core:designsystem ──► :core:domain                         (D-028: token-to-text formatting)

:widget:glance ──► :widget:engine, :core:designsystem, :core:common
:widget:engine ──► :core:domain                              (D-033: pure Kotlin/JVM)

:core:data ──► :core:domain, :core:database, :core:common
:core:database ──► :core:domain, :core:common
:core:domain ──► nothing
```

Two modules are pure Kotlin/JVM (`:core:domain`, `:widget:engine`). Everything else is an
Android library or the app module. Full detail: `PROJECT_STATUS.md` § Module graph.

For anything inside `widget/`, read `docs/WIDGET_ARCHITECTURE.md` before touching code — it is
the single-file version of this document, scoped to the widget system, with real file paths and
function names for data flow, render flow, refresh flow, and both lifecycles. Read
`docs/PRODUCT_REVIEW.md` and `docs/SCREENSHOT_GUIDE.md` before assuming anything about the widget
is production-verified — as of Session 8 there is finally real, on-device, screenshotted evidence
(`docs/WIDGET_REVIEW.md`, Session 7, predates that and is largely superseded — see its own banner).
For the widget's **visual design** specifically — why each of the seven styles looks the way it
does, and the before/after evidence that they now look genuinely different — read
`docs/WIDGET_DESIGN_GUIDE.md` and `docs/WIDGET_DESIGN_REVIEW.md` (Session 9). For **size
responsiveness** — why 2×1, 2×2, and 4×2 each have their own layout rather than one stretched
tree, and the 21-combination matrix — read `docs/WIDGET_SIZE_MATRIX.md` and
`docs/RESPONSIVE_WIDGET_REVIEW.md` (Session 10). For **background refresh** — how a widget stays
current while the app is not open, the alarm/receiver mechanics, and real device evidence for all
of it — read `docs/WIDGET_REFRESH_ARCHITECTURE.md` (Session 12); `docs/WIDGET_ARCHITECTURE.md` §5
is now a summary pointing there, not the source of truth for refresh behavior. For **reminder
notifications** — trigger-time calculation, timezone policy, idempotent delivery, the coalesced
alarm, permission flow, and real device evidence — read `docs/NOTIFICATION_ARCHITECTURE.md`
(Session 13); it deliberately does not share code with the widget refresh system, only the
scheduling *pattern* (D-067). For **Settings** — how the app-wide Theme/Dynamic Color preference
reaches the UI without a dedicated ViewModel, why widgets are unaffected by it, and how
notification status stays correct across every Android version — read `PROJECT_STATUS.md`'s
"Where important logic lives" table and DECISIONS.md D-069 through D-073 (Session 14); there is no
separate architecture doc for Settings, since the whole system is a handful of small, self-
explanatory classes in `:feature:settings`.

**If you need a device this session, check for a local one before assuming you need a remote
one.** Sessions 5–7 fought a flaky remote device at `127.0.0.1:6555` and Session 7 wrongly
concluded no local emulator existed — that conclusion came from `which emulator` failing (not on
`PATH`), not from checking `~/Library/Android/sdk/emulator/emulator` directly, which exists and
works, alongside an existing `Pixel_9` AVD. `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`
launched directly gave Session 8 a fully stable device for the whole session. Try this first.

## What exists right now (Milestone 3 finishing pass + Milestone 5B complete; Milestone 8's background refresh delivered, Session 12; Milestone 7's basic event reminders delivered, Session 13; Milestone 6's essential settings delivered, Session 14)

- **Domain**: `Event`, `EventTarget` (the all-day/timed split — read its KDoc, it is the most
  important type in the app), `WidgetBinding`, `Reminder`, `CountdownEngine`, `EventValidator`,
  four repository interfaces. `EventFilter.lifecycle: EventLifecycleFilter` (D-058) picks exactly
  one of three exclusive buckets (Upcoming/Completed/Archived) — read its KDoc before assuming
  the old `includeArchived`/`includeCompleted` booleans still exist; they don't.
- **Persistence**: Room (3 entities, cascading foreign keys, schema v1 committed), DataStore
  preferences, repository implementations — all integration-tested against real SQLite via
  Robolectric, not mocked.
- **App UI**: home list with search/sort/filter/three lifecycle tabs, create/edit form with
  validation, an accent-colour picker (Dynamic + eight presets, D-050), and now a live widget
  preview of its own (`EventWidgetPreview`, D-059). Complete/archive/restore/delete are reachable
  two ways on every row — a swipe gesture on Upcoming rows, and an overflow menu present on every
  row, on every tab, that is the one path every action (including delete, never a swipe target,
  D-060) always has (Session 11, closing TD-008).
- **Widget**: `CountdownGlanceWidget`, now genuinely responsive across three sizes — 2×1, 2×2,
  4×2 (`SizeMode.Exact`, D-053) — with 21 total Style × Size compositions
  (`CountdownWidgetLayouts.kt`), each with its own information hierarchy rather than a stretched
  or shrunk copy of another size (Session 10, `docs/WIDGET_SIZE_MATRIX.md`). A `WidgetSizeClass`
  classifier (`COMPACT`/`STANDARD`/`WIDE`) reads real `LocalSize`, with dp thresholds calibrated
  against real device measurements, not Android's cell-size formula — see "Defects" below. A
  shared `WidgetHeadline` content-hierarchy model (D-046) decides once, before any style renders,
  what the headline and its supporting line should say — closing the "Tomorrow / Tomorrow" and
  unnecessary "N days / In N days" redundancies the pre-Session-9 renderer had. A configuration
  activity with a verified no-orphan-bindings guarantee, now a two-step flow (pick event →
  customize style/progress/toggles/accent) with a live, size-aware, no-save-required preview
  (D-048, D-049, D-057). A widget-picker preview via `android:previewLayout` (TD-014, resolved). A
  production, alarm-based refresh scheduler (Session 12, D-062/D-063) keeps every widget current
  in the background, not just while the app is alive — see "Confirmed Session 12" below.
- **Reminders**: an MVP local-notification system (Session 13, `:core:notifications`,
  `docs/NOTIFICATION_ARCHITECTURE.md`) reusing the `Reminder`/`ReminderType` domain model and
  `ReminderEntity`/`ReminderDao` persistence built in Milestone 2 but unused until now. Exactly
  four selectable offsets (30/7/1-day/day-of) as checkboxes in Create/Edit Event, a coalesced
  single-next-alarm scheduler (`ReminderNotificationCoordinator`/`NotificationAlarmScheduler`)
  mirroring Session 12's pattern without sharing its code (D-067), comparison-based idempotent
  delivery (`deliveredForScheduledTime`, not a boolean flag), contextual `POST_NOTIFICATIONS`
  permission requesting (never on first launch), and lifecycle cancellation (complete/archive/
  delete) for free via the existing `ACTIVE_REMINDERS_QUERY`'s SQL-level filtering (D-066) — see
  "Confirmed Session 13" below.
- **Settings**: a real `SettingsScreen`/`AboutScreen` (Session 14, `:feature:settings`) replacing
  the Milestone 1 placeholders — Theme (System/Light/Dark) and Dynamic Color, both reading and
  writing the `ThemeMode`/`useDynamicColor` fields `PreferencesRepository` has stored since
  Milestone 2 but nothing read until now; `MainActivity` applies them to `CountFlowTheme` directly
  as Compose state, no dedicated ViewModel needed (D-069). Notification status
  (`NotificationStatusProvider`, `areNotificationsEnabled()`, correct on every Android version) and
  a "Manage notifications" deep link to Android's own settings, refreshed on every screen resume
  (D-070). About reads the installed package's real version via `PackageManager`, not `BuildConfig`
  (D-072); Privacy Policy and Open-source licenses render as honest, disabled placeholders, not
  fake links (D-073) — see "Confirmed Session 14" below.
- **Confirmed Session 8**: the widget has been placed through the actual system picker and
  launcher, configured, updated live, survived an app update, and survived a full device reboot —
  all screenshotted (`docs/SCREENSHOT_GUIDE.md`). This closed TD-010 after three sessions of
  trouble. The same device access found a Critical bug invisible to every prior session: the
  widget's real footprint was 3×2, not the 2×2 everyone assumed, because `minWidth="180dp"` was
  the wrong value under Android's own cell-size formula — fixed (BUG-R009).
- **Confirmed Session 9**: all seven styles verified genuinely distinct in real screenshots (not
  just in code) — `docs/WIDGET_DESIGN_REVIEW.md`'s Final Report answers "would this look
  professionally designed beside Google's own widgets" with **YES**, with the specific remaining
  gaps (BUG-011 still open, one size only) stated plainly rather than hidden. One real bug — a
  word-shaped headline wrapping mid-word — was introduced and fixed within the same session
  (BUG-R011).
- **Confirmed Session 10**: all 21 Style × Size combinations verified free of clipping/overflow;
  2×1, 2×2, and 4×2 each read as intentionally designed rather than a mechanically resized sibling
  — `docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final Report. Real-device work found and fixed a
  size-classification bug (dp thresholds derived from Android's cell-size *formula* did not match
  what a real launcher actually rendered — the same category of mistake as BUG-R009) and a
  `Row`/`fillMaxWidth` layout bug specific to the new compact size. One real, named gap: the 4×2
  (WIDE) size has no real-device visual confirmation, Robolectric only (TD-017) — three
  device-automation attempts to force a real WIDE placement did not succeed within the session.
- **Confirmed Session 11**: the full event lifecycle (create → edit → complete → archive →
  restore → delete, plus cancel-delete) works correctly on a real device, both via swipe and via
  the overflow menu; the menu's accessibility is confirmed against the real semantics tree, not
  just visually — the card's merged description and the overflow button's own independent "More
  actions for X" description both appear as separate nodes. A real placed widget's behaviour
  across completing, archiving, and deleting its bound event was confirmed correct with **zero
  widget-specific code changes** — the existing render pipeline and cascading foreign key already
  handled all three correctly. One real bug found and fixed within the session: the new tab row
  didn't scroll like the category row beside it, so 200% font scale wrapped "Archived" into a
  vertical letter stack.
- **Confirmed Session 12**: a widget transitioned to "Expired" on its own, with the app
  backgrounded and its process killed, and never manually reopened — confirmed via `dumpsys alarm`
  (the alarm firing, `1` wakeup recorded), logcat, and a home-screen screenshot. A full device
  reboot correctly restored both placed widgets and re-armed a fresh alarm. A real timezone change
  correctly recomputed the schedule — the first attempt at that specific test found a real,
  nine-session-old bug (a `@Singleton Clock` that froze its resolved zone at construction, BUG-R013
  / D-064), fixed and re-verified the same session. `docs/WIDGET_REFRESH_ARCHITECTURE.md` has the
  full system and every claim's real-device evidence.
- **Confirmed Session 13**: a reminder fired reliably with the app backgrounded and its process
  killed (`am kill`, not Force Stop), delivered exactly once (confirmed both by unit test and a
  live double-trigger), and tapping it opened the app to the correct event via `onNewIntent`. A
  full device reboot correctly re-armed a fresh alarm from Room's persisted state and the pending
  reminder delivered exactly once, with no manual app open. A real timezone change (5-hour shift)
  correctly recomputed a timed event's trigger — the first attempt at that specific test found a
  real, same-shape-as-D-064 bug: `Reminder.scheduledTime` used the device's current zone
  unconditionally instead of the event's own authored zone for timed events (BUG-R014 / D-065),
  fixed and re-verified the same session. Denying `POST_NOTIFICATIONS` produced no crash, no
  repeated permission-request loop, and a silently-resolved (never-fired) reminder.
  `docs/NOTIFICATION_ARCHITECTURE.md` has the full system and every claim's real-device evidence.
- **Confirmed Session 14**: System/Light/Dark all applied instantly and correctly, confirmed via
  screenshot, and the choice survived a full `am force-stop` process kill — proof of DataStore
  persistence, not in-memory state. Toggling Dynamic Color off visibly switched the app's accent
  from the wallpaper-derived color to CountFlow's static Material 3 palette. Two placed home-screen
  widgets kept their existing dark, translucent styling and accent completely unchanged through
  every theme/dynamic-color combination tested — the widget regression check confirming D-069's
  "app UI only" policy holds in practice. Disabling and re-enabling notifications through Android's
  real system settings and returning to CountFlow flipped the "Allowed"/"Not allowed" row correctly
  both directions with no restart, confirming `LifecycleResumeEffect`'s resume-refresh (D-070).
  "Manage notifications" opened Android's real per-app settings page. 200% font scale reflowed
  every row without clipping. One real defect found and fixed: the app's `versionCode`/`versionName`
  had been frozen at `1`/`"0.1.0"` since Milestone 1, invisible until this session's About screen
  read it back (BUG-R015 / D-072).
- Still not measured on any device, by any session: update latency, memory, CPU, or TalkBack
  output. Battery now has a *reasoned* answer (Session 12, alarm-count-based, not
  profiler-measured — `docs/WIDGET_REFRESH_ARCHITECTURE.md` §11); see `docs/PRODUCT_REVIEW.md` for
  what was prioritized instead of an instrumented measurement, and why.

`ROADMAP.md` has the milestone-by-milestone detail; `SESSION_SUMMARY.md` has what the *most
recent* session specifically did.

## The five things that will bite you if you don't know them

1. **A day count is a calendar comparison, never `duration / 86_400_000`.** Dividing gives the
   wrong answer roughly half the time across a DST boundary. Use
   `CountdownResult.calendarDaysRemaining`, not `totals.totalDays`, for anything a user reads as
   "N days away." (`CountdownEngineCalendarTest` documents every case where they diverge.)
2. **Never call `Instant.now()` or `LocalDate.now()` directly.** Inject `java.time.Clock`
   (provided in `:core:common`'s `TimeModule`). The entire test suite depends on time being a
   parameter; one direct call makes its caller untestable at exactly the boundaries this app
   cares about.
3. **The domain never returns display strings — only tokens.** `CountdownLabel`,
   `EventCategory`. Resolving them to text happens at composition
   (`core/designsystem/…/format/CountdownLabelFormatter.kt`), because resolving early freezes
   text in whatever locale was active when data last loaded.
4. **`GlanceAppWidget` cannot be Hilt-injected** (Glance's runtime instantiates it, not
   Android's). `provideGlance` reaches dependencies through a Hilt `EntryPoint`
   (`widget/glance/…/di/WidgetEntryPoint.kt`) — the one deliberate bridge point. Everything else
   in the widget layer, including the receiver and the configuration activity, is injected
   normally.
5. **Glance's `hasText` test matcher is always a substring match** (D-038) — its second param is
   `ignoreCase`, not `substring`. Use `hasTextEqualTo` for an exact match, or a loose assertion
   will pass when it shouldn't.

## Defects this codebase has already had, and how they were caught

Worth knowing because each is a *shape* of bug likely to recur elsewhere:

- An all-day event read as "starting soon" for its entire day, because the imminent-countdown
  threshold check didn't exclude all-day events (D-023, BUG in Session 3). Found by testing.
- The widget configuration activity crashed after a successful binding write, because forcing an
  immediate redraw threw when the widget id didn't resolve to a real `GlanceId` — stranding
  already-saved data instead of finishing gracefully (BUG-R005, Session 5). Found by device
  testing. The fix: a write that already succeeded must not be undone by an optional follow-up
  step failing.
- Two configuration fields (`WidgetTheme.isHighContrast`, `WidgetBinding.showPercentage`) were
  computed or persisted correctly for milestones but never actually read by the layer that would
  have shown their effect (BUG-R006, BUG-R007, Session 6). Found by re-reading the render model
  against the renderer, not by a failing test — nothing asserted the values were read at all,
  which is exactly the risk: a field with no consumer looks identical to a field that works,
  right up until someone checks. Worth deliberately auditing "does every field on a render model
  have a reader" when a milestone claims to be finished, not just "does every test pass."
- GLASS's translucent widget background could drop below WCAG AA text contrast over a light
  wallpaper — a case no unit test could ever catch, because the actual composited color depends on
  content (the user's wallpaper) that exists entirely outside the app and the test suite (BUG-R008,
  Session 7). Found by computing the real composited contrast from the color constants in the
  code, not by seeing it rendered. Worth remembering: any widget color that composites over
  something the app doesn't control (a launcher's wallpaper, not an app-drawn background) needs
  its worst case reasoned through explicitly — "it looked fine in the emulator" was never even
  available to check this, but wouldn't have been sufficient evidence anyway, since the emulator's
  one wallpaper isn't the worst case.
- **The widget's actual size was wrong for its entire history, and no amount of reading the code
  could have caught it.** `minWidth="180dp"` looked reasonable next to `targetCellWidth="2"` —
  until a real launcher's own widget picker reported the footprint as "3 × 2" (BUG-R009,
  Session 8). Android's own formula (`dp = 70×cells − 30`) makes `180dp` unambiguously the 3-cell
  value, but nothing in the code, the tests, or three sessions of documentation ever checked a dp
  value against that formula. The lesson: a manifest/XML value asserted to mean something specific
  (a cell count, a size class) needs checking against the platform's actual formula for it, not
  just against what a comment claims.
- **A library's public API surface is not the same as its runtime behavior.** Session 7 read
  Glance 1.1.1's `Text` API via `javap` and correctly found no overflow/ellipsis parameter, and
  concluded long titles clip with no ellipsis (TD-013). A real render showed the underlying
  `RemoteViews` `TextView` ellipsizes by default anyway (Session 8). The API reading wasn't wrong;
  it was incomplete evidence being treated as sufficient. Verify a rendering claim against one
  real render before writing it down as fact.
- **A font size tuned for one content shape doesn't generalize to another, and Glance cannot
  autosize to catch the gap.** Session 9's headline sizes were tuned for a 1–3 digit day count;
  the first real screenshot of a completed event showed "Completed" wrapped mid-word into
  "Compl" / "eted" (BUG-R011). Found and fixed within the same session it was introduced, the same
  way — a real screenshot, not code review, since nothing in the type system distinguishes a
  numeric headline from a word-shaped one without an explicit check (`WidgetHeadline.isNumeric`).
  Worth remembering for any future fixed-`sp` constant applied to more than one content shape.
- **A manifest formula and a real launcher's actual behavior can disagree even after the formula
  was already the fix for a prior bug.** BUG-R009 (Session 8) corrected `minWidth` using Android's
  documented `dp = 70×cells − 30` cell-size formula. Session 10 then derived `WidgetSizeClass`'s
  compact/standard breakpoints from that same formula — and a real launcher's real measurements
  came in at roughly 2× the formula's prediction on both axes. Recalibrated against real
  measurements (D-055). The lesson compounds: even a formula that was correct for one purpose
  (declaring a footprint) is not automatically correct for a different purpose (classifying a
  runtime size) — verify each specific claim against a real device, not the previous verification.
- **A layout copied from a sibling needs the sibling's whole behavior copied too, not just its
  look.** Session 11's new tab row visually matched the existing category filter row but omitted
  its `horizontalScroll` — invisible at 100% font scale, and only surfaced at 200%, where three
  fixed-width chips with nowhere to grow forced "Archived" to wrap one letter per line instead of
  scrolling off-screen. Found by the session's own large-font-scale device check, the same
  category of gap BUG-R011 (Session 9) was in: a constant tuned for one condition applied
  unconditionally to another. Worth checking every reused layout pattern for the *whole* behavior
  it was copied for, not just its static appearance.
- **A `@Singleton` built from a "read the live system value" API can still freeze that value at
  construction, and nothing before a genuine live change will ever expose it.**
  `Clock.systemDefaultZone()` had been the injected `Clock`'s implementation since D-026
  (Milestone 2) — nine sessions, unnoticed — because it snapshots `ZoneId.systemDefault()` once,
  at construction, into an immutable `Clock`, and nothing before Session 12 exercised a real
  device timezone change against an already-running process (BUG-R013, D-064). Found by the first
  real-device timezone test this project ever ran: the recomputed refresh alarm landed on the
  exact same absolute instant as before the change instead of shifting with the new zone. Worth
  checking any other `@Singleton`-scoped "current system value" for the same
  construction-time-freeze risk before assuming a live read stays live for the life of the
  process.
- **A value's own "zone-pinned" design intent does not automatically propagate to every
  calculation derived from it.** `EventTarget` has been correctly zone-pinned for a timed event
  since D-014 (Milestone 2) — "a flight from Tokyo stays at Tokyo 14:05 no matter where the phone
  is." `Reminder.scheduledTime`, added the same milestone but left unused (unscheduled) until
  Session 13, still used the device's *current* zone unconditionally for its own calendar-day
  subtraction, for both all-day and timed events alike — unnoticed for eleven sessions because
  nothing before Session 13 both activated a reminder on a timed event *and* exercised a real
  device timezone change against it (BUG-R014, D-065). Found the same way BUG-R013 (D-064) was
  found one session earlier: a real device timezone-change test, not code review. Fixed by making
  the zone conditional on the event's own kind — `deviceZone` for all-day, `event.target.zone` for
  timed — exactly as `EventTarget` itself already branches. Worth checking any other "N days/hours
  before X" calculation for the same "which zone does *this specific derived calculation* use"
  question, independently of what zone the value it derives from uses.
- **A value with no reader is invisible for exactly as long as nothing reads it — a build value,
  not just a data field.** `versionCode`/`versionName` were set once, in Session 2, and never
  touched again despite thirteen real `CHANGELOG.md` releases in between; nothing ever disagreed
  with `1`/`"0.1.0"` because nothing ever read them back (BUG-R015, D-072). The same shape as
  BUG-R006/BUG-R007 (Session 6): a field with no consumer looks identical to a field that works,
  right up until something checks. Found the moment Session 14's About screen became the first
  thing in the app to ask "what version am I" — worth auditing any other value set once at project
  setup and never revisited (an app id, a static config default, a hardcoded string a screen will
  someday display) the same way before trusting it is still correct.

## How to verify the project still works

```
./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug
```

Current baseline: 340 tests, 0 failures, 0 lint errors (17 accepted warnings, all documented in
`KNOWN_ISSUES.md`), `:core:domain` line coverage above 95% (currently 97.0%).

Build output is noisy — every module logs two deprecation warnings per compile, a side effect of
opting out of AGP 9's built-in Kotlin (TD-001, D-005). Filter with:

```
grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"
```

For anything touching the widget layer, `--no-build-cache` is worth using once after a resource
or manifest change — a Gradle build-cache staleness issue bit this project once already (TD-004).

## The working agreement

Read `SESSION_SUMMARY.md`'s most recent entry for exact standing instructions, but the pattern
has held across every session: architecture is proposed and approved before code, work proceeds
one milestone at a time with an explicit approval gate between them, and every session ends by
updating **all** of `SESSION_SUMMARY.md`, `PROJECT_STATUS.md`, `DECISIONS.md`, `ROADMAP.md`,
`CHANGELOG.md`, `KNOWN_ISSUES.md`, `TODO.md`, and this file. Do not start a new milestone without
checking `TODO.md`'s P0 section first — it is where unresolved cross-session questions live.

## Document map (full detail, one line each)

| File | What it actually contains |
|---|---|
| `ARCHITECTURE.md` | The original design proposal. Wins on any conflict. |
| `PROJECT_STATUS.md` | Permanent overview: module graph, tech stack, progress bars. |
| `SESSION_SUMMARY.md` | What the *most recent* session did, in narrative detail. |
| `DECISIONS.md` | Every decision (73 as of Session 14) with reason, alternatives, tradeoffs. |
| `docs/WIDGET_ARCHITECTURE.md` | The widget system in one file: data/render flow, both lifecycles, Glance's sharp edges, forward compatibility. §5 (refresh flow) is now a summary — see the file below for the real system. |
| `docs/WIDGET_REFRESH_ARCHITECTURE.md` | The production background refresh system in one file: next-transition calculation, coalescing, alarm lifecycle, system receivers, timezone/reboot/Force Stop behavior, battery reasoning, real-device evidence (Session 12). |
| `docs/NOTIFICATION_ARCHITECTURE.md` | The MVP reminder notification system in one file: reminder model, trigger-time calculation, all-day/timed zone policy, idempotent delivery, the coalesced alarm scheduler, permission flow, notification channel, lifecycle behavior, boot/timezone recovery, battery reasoning, real-device evidence (Session 13). |
| `docs/WIDGET_REVIEW.md` | The Milestone 4.5 audit (Session 7, no device — see its own banner; largely superseded by the docs below). |
| `docs/PRODUCT_REVIEW.md` | The Milestone 4.9 product-quality verdict: ranked strengths/weaknesses, would-you-ship assessment, real device evidence. |
| `docs/SCREENSHOT_GUIDE.md` | Real, curated on-device screenshots of every major widget state, with the recipe to reproduce each (Session 8 baseline). |
| `docs/WIDGET_DESIGN_GUIDE.md` | Per-style design philosophy for all seven widget styles — why each layout exists, what differentiates it, when to choose it (Session 9). |
| `docs/WIDGET_DESIGN_REVIEW.md` | Before/after evidence for the Milestone 5A redesign and the session's Final Report verdict (Session 9). |
| `docs/WIDGET_SIZE_MATRIX.md` | All 21 Style × Size combinations, field by field, plus the real-vs-formula size table (Session 10). |
| `docs/RESPONSIVE_WIDGET_REVIEW.md` | Real-device evidence for the responsive system and the session's Final Report verdict (Session 10). |
| `ROADMAP.md` | Milestone-by-milestone status and what each one delivered. |
| `KNOWN_ISSUES.md` | Open bugs, technical debt, platform limitations, resolved defects. |
| `TODO.md` | Prioritised outstanding work, P0 first. |
| `CHANGELOG.md` | Keep-a-Changelog-format release notes per milestone. |
