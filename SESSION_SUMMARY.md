# CountFlow

## Session 12

Date: 2026-08-09
Current Milestone: **Background refresh infrastructure — Milestone 8 scope, pulled forward (COMPLETE, real-device verified); Milestone 5's remaining widget-sizing gaps (TD-016/TD-017) unchanged**

> **READ THIS FIRST:** This session's brief was explicit that Core Product (Event CRUD/UI,
> responsive widgets) is done — this session is about *reliability*, not new functionality. The
> temporary Milestone 4 scheduler only kept widgets current while the app process happened to be
> alive; this session built the real one D-008 always planned: a coalesced, alarm-based background
> refresh system, and proved it works with real-device evidence, not just architecture.
>
> **What changed:** a pure, zone-aware `CountdownEngine.nextTransitionAt` (`:core:domain`, D-062)
> decides exactly when any event's countdown next meaningfully changes — correctly handling a real
> bug found by testing (`CountdownLabel.NextWeek` can stay unchanged across several consecutive
> local midnights, so "check the next midnight" is the wrong algorithm). `WidgetRefreshPlanner`
> (`:widget:engine`) coalesces every placed widget's bound event to one global instant, deduplicated
> by event. `WidgetRefreshCoordinator` orchestrates a full redraw-then-reschedule cycle behind two
> Android-free seams. `:widget:glance` supplies the real mechanics: one `AlarmManager
> .setAndAllowWhileIdle` alarm (never more than one — a fixed `PendingIntent` request code replaces
> rather than stacks), one `BroadcastReceiver` for both the alarm firing and the four genuine system
> recovery broadcasts (boot, timezone, time, date), and a `WorkManager` periodic safety net.
>
> **Verified on a real device, not just unit-tested:** a widget transitioned to "Expired" with the
> app backgrounded and its process killed, with no manual reopen (`dumpsys alarm` showed a real
> `1`-wakeup alarm fire; logcat showed the refresh cycle run; a screenshot showed the result). A
> full device reboot correctly restored both placed widgets and re-armed a fresh alarm. A real
> timezone change correctly recomputed the schedule — and the *first* attempt at that specific test
> found a real, nine-session-old bug: a `@Singleton Clock` (`Clock.systemDefaultZone()`, present
> since D-026 in Milestone 2) froze its resolved zone at construction, so an already-running
> process silently kept computing against the *old* zone even after correctly receiving the
> `TIMEZONE_CHANGED` broadcast. Fixed (`LiveDefaultZoneClock`, D-064, BUG-R013), regression-tested,
> and re-verified on the same device the same session.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_REFRESH_ARCHITECTURE.md` (new — the permanent reference for this session's system),
> `PROJECT_STATUS.md`, `DECISIONS.md` (64 entries — D-062 through D-064 are new this session), then
> this file.
>
> One item is open for Session 13 — see "Requires approval" at the end.

----------------------------------

## Objective

Build the production background widget refresh system the architecture always planned (D-008),
replacing the Milestone 4 scheduler that only worked while the app process was alive. Per the
brief: for every active countdown with a placed widget, determine the *next* moment its displayed
info meaningfully changes and schedule exactly one refresh for that moment — never blindly refresh
every widget on a fixed interval; coalesce every placed widget to one system wakeup, not one per
widget; keep the mechanism zone-aware and calendar-correct (no naive `+24h` midnight math); handle
reboot, timezone change, and manual clock change without waking the device unnecessarily; do not
attempt to defeat Android's Force Stop semantics (D-052 stands); exhaustively test the pure
next-refresh calculator and the coalescing scheduler; verify on a real device that a widget updates
with the app backgrounded and the process not in use, that reboot recovery works, and that
timezone-change recovery works; document the battery/wake-frequency reasoning; and answer nine
specific closing questions, then stop before Notifications or Billing.

----------------------------------

## Completed

**`CountdownEngine.nextTransitionAt` — the pure next-refresh calculator**

New function in `:core:domain`, `(Event, Instant, ZoneId) → Instant?`. Returns `null` for
completed or expired (terminal) events. For a still-future event, walks a bounded superset of
candidate instants — every local midnight up to `min(daysUntilTarget, nearFutureDays + 14)` days
out, the event's own start instant, and (for timed events) the imminent-threshold instant — and
returns the earliest one whose label or status actually differs from now. Same-day/past events
walk exactly one day forward. The `+14`-day buffer exists specifically because of a real bug found
mid-session: `CountdownLabel.NextWeek`'s window re-anchors to a shifting `today` each day, so the
label can stay unchanged across several consecutive midnights even while the day count keeps
decreasing — an initial "just check the next midnight" implementation produced the wrong instant
for exactly this case, caught by a test that manually traced the label day-by-day. See D-062.

**`WidgetRefreshPlanner` — coalescing (`:widget:engine`)**

`nextGlobalRefresh(boundWidgets, now, deviceZone)` reduces every placed widget to one global
`Instant?` by calling `nextTransitionAt` once per *distinct event* (`distinctBy { it.event.id }`),
then taking the minimum. N widgets sharing one event cost exactly what one widget would; an event
with no widgets contributes nothing; no bound widgets at all returns `null`.

**`WidgetRefreshCoordinator` + two seams (`:widget:engine`)**

`refreshAndReschedule()`: read every bound widget from Room, redraw all of them, compute the next
global instant, then schedule or cancel the one alarm. `AlarmScheduler` and `WidgetRedrawer` are
two small interfaces keeping this class free of `Context`/`AlarmManager`/Glance, so it is tested
with plain fakes, not Robolectric. `RefreshOutcome(widgetsRefreshed, nextRefreshAt)` is what every
real caller logs.

**Android mechanics (`:widget:glance`)**

- `AndroidAlarmScheduler` — the real `AlarmManager`, via `setAndAllowWhileIdle` (no exact-alarm
  permission needed, survives Doze, inexact by at most a few minutes), always targeting the same
  explicit `PendingIntent` (fixed request code) so a reschedule replaces rather than stacks.
- `GlanceWidgetRedrawer` — `CountdownGlanceWidget().updateAll(context)`.
- `WidgetRefreshReceiver` — one `@AndroidEntryPoint BroadcastReceiver` for both the alarm firing
  (`ACTION_REFRESH`, delivered only via the explicit `PendingIntent` above — no manifest entry
  needed) and the four genuine system recovery broadcasts (`BOOT_COMPLETED`, `TIMEZONE_CHANGED`,
  `TIME_SET`, `DATE_CHANGED`, all manifest-registered). Every reason runs the identical
  `refreshAndReschedule()` cycle via `goAsync()` + the app's `ApplicationScope`. Also calls
  `TimeZone.setDefault(null)` specifically on `ACTION_TIMEZONE_CHANGED` (added mid-session — see
  D-064 below).
- `WidgetRefreshSafetyNetWorker` — a `WorkManager` periodic backstop (`Duration.ofHours(6)`
  interval, 2h flex), enqueued with `ExistingPeriodicWorkPolicy.KEEP` so a process restart never
  resets its timer.
- `GlanceWidgetRefreshScheduler` rewritten: still prunes orphaned bindings at startup unchanged,
  now subscribes to `observeEventsWithWidgets()` and calls `refreshCoordinator.refreshAndReschedule()`
  on every emission (replacing the old direct `updateAll` call), and enqueues the safety net.
- `WidgetGlanceModule` gains two `@Binds`: `AlarmScheduler → AndroidAlarmScheduler`,
  `WidgetRedrawer → GlanceWidgetRedrawer`.
- Manifest: `RECEIVE_BOOT_COMPLETED` permission, the new receiver's four-action `<intent-filter>`.
- `widget/glance/build.gradle.kts`: `androidx.work.runtime.ktx`, `androidx.hilt.work`,
  `ksp(androidx.hilt.compiler)` — previously only `:app` had these.

**BUG-R013 — a `@Singleton Clock` froze its zone at construction (found and fixed this session)**

`TimeModule.providesClock()` had returned `Clock.systemDefaultZone()` since D-026 (Milestone 2).
`Clock.systemDefaultZone()` snapshots `ZoneId.systemDefault()` once, at construction, into an
immutable `Clock`; bound `@Singleton`, that snapshot never updates for the life of the process.
Found live during real-device timezone testing (see "Real-device verification" below), not by
inspection: a correctly-received `TIMEZONE_CHANGED` broadcast triggered a correctly-run refresh
cycle that nonetheless recomputed the exact same absolute alarm instant as before the change — the
new zone was never actually being read. Fixed with `LiveDefaultZoneClock` (`core/common/…/di/
TimeModule.kt`), whose `getZone()` calls `ZoneId.systemDefault()` fresh on every read instead of
caching it, plus `TimeZone.setDefault(null)` in `WidgetRefreshReceiver` on `ACTION_TIMEZONE_CHANGED`
to bust the underlying JVM-level cache `ZoneId.systemDefault()` itself reads through. Rebuilt and
reinstalled the APK, re-ran the exact same timezone test, and confirmed the recomputed alarm now
correctly shifted by the full zone offset. See D-064.

**Real-device verification (`Pixel_9` AVD)**

- **Background refresh, app not reopened.** Edited "QuickTest"'s target to `22:55:00` today
  through the real UI; `dumpsys alarm` confirmed a real `RTC_WAKEUP` alarm at that exact instant.
  Backgrounded the app (`KEYCODE_HOME`) and killed the process (`adb shell am kill` — the normal
  low-memory-reclaim path, confirmed distinct from Force Stop, which would have cancelled the
  alarm outright; an earlier attempt in this session that accidentally used `am force-stop`
  demonstrated exactly that cancellation, `Reason=pi_cancelled` in `dumpsys alarm`, and had to be
  redone). Confirmed the process dead via `pidof`, confirmed the alarm survived the kill, then
  waited past the scheduled time. Logcat showed the alarm fire and the full cycle run
  (`WidgetRefreshReceiver: reason=... widgetsRefreshed=2 nextRefreshAt=...`); `dumpsys alarm`'s
  `Top Alarms` recorded `1 wakeups` for the refresh tag — a genuine device wake, not a coincidental
  redraw. A home-screen screenshot confirmed the "QuickTest" widget had transitioned to "Expired"
  on its own; the unrelated "Swiss Conference" widget was correctly left unchanged.
- **Reboot recovery.** `adb reboot`; CountFlow was never manually reopened afterward. Both widgets
  reappeared with correct data — process-start logs confirmed this happened through the widget-
  restore/`BOOT_COMPLETED` path, not a manual launch. `dumpsys alarm` confirmed a fresh alarm was
  scheduled post-boot — proof of real recovery, since `AlarmManager` state does not survive a
  genuine reboot.
- **Timezone-change recovery (found and fixed BUG-R013 in the process).** `adb shell cmd alarm
  set-timezone America/New_York` (from `Africa/Casablanca`, a 5-hour shift). First attempt exposed
  the stale-zone `Clock` bug above. After the fix, the identical test correctly produced a
  ~5-hour-shifted alarm (confirmed via `dumpsys alarm`), with exactly one
  `com.countflow.widget.action.REFRESH` entry — no stale old-zone alarm left behind
  (`grep -c` = `1`).

**Documentation**

`docs/WIDGET_REFRESH_ARCHITECTURE.md` (new) — the permanent reference for this system: module
split, `nextTransitionAt`'s algorithm and the plateau bug it handles, coalescing, the alarm and
its exact-vs-inexact tradeoff, the receiver's four-actions-one-class design, the safety net,
real-device evidence for every claim (§9), Force Stop's explicitly-unchanged behavior (§10),
battery/wake-frequency reasoning (§11), and known limitations (§12). `docs/WIDGET_ARCHITECTURE.md`
§5 rewritten as a summary pointing there; §2's file tables and §10's known-limitations updated.
`DECISIONS.md` D-062 (the plateau-walk algorithm), D-063 (the module split, coalescing, and
`AlarmManager`/receiver/safety-net mechanics), D-064 (the `LiveDefaultZoneClock` fix). `PROJECT_
STATUS.md`, `ROADMAP.md` (new Milestone 8 "In Progress" section), `TODO.md`, `KNOWN_ISSUES.md`
(BUG-R013 resolved entry, BUG-011 updated to confirm the new scheduler does not change its status,
LIM-002 updated), `CHANGELOG.md`, `AI_CONTEXT.md` all updated per the standing working agreement.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL (run
  twice: once before the D-064 fix, once after, both green).
- 299 tests, 0 failures (up from 259).
- Lint: 0 errors, 17 warnings, unchanged since Session 9.
- `:core:domain` coverage unchanged at 97.0%, gated at 95%.

----------------------------------

## Files Created

```
docs/WIDGET_REFRESH_ARCHITECTURE.md                                          (new)
core/domain/src/test/kotlin/…/countdown/CountdownEngineNextTransitionTest.kt (new, 20 tests)
core/common/src/test/kotlin/…/di/LiveDefaultZoneClockTest.kt                 (new — first test
                                                                                source set for
                                                                                :core:common)
widget/engine/…/refresh/AlarmScheduler.kt                                    (new)
widget/engine/…/refresh/WidgetRedrawer.kt                                    (new)
widget/engine/…/refresh/RefreshOutcome.kt                                    (new)
widget/engine/…/refresh/WidgetRefreshPlanner.kt                              (new)
widget/engine/…/refresh/WidgetRefreshCoordinator.kt                          (new)
widget/engine/src/test/kotlin/…/refresh/WidgetRefreshPlannerTest.kt          (new, 7 tests)
widget/engine/src/test/kotlin/…/refresh/WidgetRefreshCoordinatorTest.kt      (new, 9 tests)
widget/glance/…/refresh/AndroidAlarmScheduler.kt                             (new)
widget/glance/…/refresh/GlanceWidgetRedrawer.kt                              (new)
widget/glance/…/refresh/WidgetRefreshReceiver.kt                             (new)
widget/glance/…/refresh/WidgetRefreshSafetyNetWorker.kt                      (new)
```

----------------------------------

## Files Modified

```
core/common/…/di/TimeModule.kt                          (LiveDefaultZoneClock, D-064)
core/domain/…/countdown/CountdownEngine.kt               (nextTransitionAt + KDoc, D-062)
widget/glance/build.gradle.kts                           (+WorkManager, +hilt-work)
widget/glance/src/main/AndroidManifest.xml                (RECEIVE_BOOT_COMPLETED, new receiver)
widget/glance/…/di/WidgetGlanceModule.kt                  (+2 @Binds: AlarmScheduler, WidgetRedrawer)
widget/glance/…/refresh/GlanceWidgetRefreshScheduler.kt   (uses coordinator, enqueues safety net)
AI_CONTEXT.md, CHANGELOG.md, DECISIONS.md, KNOWN_ISSUES.md, PROJECT_STATUS.md, ROADMAP.md, TODO.md,
docs/WIDGET_ARCHITECTURE.md
```

----------------------------------

## Architecture Decisions

Three new entries, D-062 through D-064, detailed in `DECISIONS.md`:

- **D-062** — `nextTransitionAt` walks a bounded set of midnight candidates, not just "the next
  one" — the fix for the `NextWeek` plateau bug.
- **D-063** — Widget refresh scheduling splits across `:core:domain`/`:widget:engine`/
  `:widget:glance`, coalesces to one alarm, and uses `setAndAllowWhileIdle` — the full production
  scheduler design, module split, and Android mechanics.
- **D-064** — `LiveDefaultZoneClock` replaces `Clock.systemDefaultZone()` — a `@Singleton` clock
  must not freeze the device's zone at construction. The BUG-R013 fix.

----------------------------------

## Current Project Structure

No new modules, and no new *internal* dependency edges — `:widget:glance` already depended on
`:widget:engine` and `:core:common`, which is where every new class in this session's system
lives. One new *external* dependency edge: `widget/glance/build.gradle.kts` now applies
`androidx.hilt.work`'s KSP processor and depends on `androidx.work.runtime.ktx` and
`androidx.hilt.work` — both already present in the version catalog (previously used only by
`:app`), now applied to a second module for `WidgetRefreshSafetyNetWorker`'s `@HiltWorker`. See
`PROJECT_STATUS.md` for the full, unchanged module graph.

----------------------------------

## Dependencies Added

Two, both already in `gradle/libs.versions.toml` before this session (used only by `:app` until
now): `androidx.work.runtime.ktx` and `androidx.hilt.work` (plus its KSP compiler), newly applied
to `widget/glance/build.gradle.kts`. No new external libraries introduced to the version catalog.

----------------------------------

## Current Features Working

Everything from Session 11, plus: widgets now refresh reliably in the background — a widget
updates on its own, with the app not open and its process not running, at the exact next moment
its countdown display would meaningfully change, confirmed on a real device across normal
background use, a full reboot, and a real timezone change. Exactly one system alarm exists for the
whole app at any time, regardless of widget count. Force Stop recovery remains explicitly out of
scope, by standing decision (D-052) — this session's scheduler makes *normal* background operation
reliable, not a workaround for Android's own Force Stop semantics.

----------------------------------

## Pending Work

**P0 — blocks Session 13**
1. **Approve Notifications (Milestone 7), or further Milestone 5/8 work**, now that background
   refresh infrastructure is delivered and real-device verified.
2. **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — carried
   over unchanged from Session 10; not attempted this session either, which was explicitly scoped
   to background refresh, not widget sizing.

**P3 — remaining Milestone 8 scope:** the launcher-ticked `Chronometer` half of D-008 (final-24-
hours second-level ticking); R8 keep rules, Baseline Profiles, macrobenchmarks, a full
accessibility pass; the first profiler-measured (not reasoned) battery/memory/CPU numbers.

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** BUG-R013 (a `@Singleton Clock` froze its resolved timezone at
construction, silently going stale after a real device timezone change — found and fixed the same
session, D-064).

**Confirmed unchanged this session, with new evidence:** BUG-011 (Force Stop recovery) — the new
alarm-based scheduler was confirmed, by design, not to change this: Force Stop cancels this app's
`AlarmManager` alarms and `WorkManager` work exactly as it cancels everything else the app
scheduled. No further engineering time went toward it, per the standing D-052 decision.

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-009, TD-016, TD-017, TD-018.
LIM-003, LIM-004, LIM-005, LIM-006. LIM-002 updated to note the coalesced-alarm half of its
resolution is now real.

**Lint:** 0 errors, 17 accepted warnings, unchanged since Session 9.

----------------------------------

## Next Session Plan

1. Get explicit approval before starting Notifications (Milestone 7) — the natural next step now
   that both Event CRUD/UI and reliable background refresh are done — or before resuming Milestone
   5's remaining widget-sizing loose ends, or the rest of Milestone 8 (Chronometer ticking, R8,
   Baseline Profiles, the full a11y pass).
2. If Notifications is approved: reuse this session's coalesced-alarm infrastructure
   (`AlarmScheduler`'s pattern) rather than adding a second wakeup source — the brief for
   Milestone 7 already expects this, per `TODO.md`.
3. If a real (ideally physical) device is available and Milestone 5 is prioritized instead:
   the real 4×2 (`WIDE`) placement and screenshot Session 10 could not complete.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents per the standing working agreement.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session (twice — before and after the D-064 fix, both green):
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 299 tests, 0 failures (up from 259)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings, unchanged since Session 9
- Runtime: the same stable local emulator established in Session 8 (`Pixel_9`), used for the full
  background-refresh/reboot/timezone verification sweep — including a genuine `adb reboot` and a
  real `adb shell cmd alarm set-timezone` zone change, both firsts for this project. `dumpsys
  alarm` was this session's primary verification technique (more reliable than logcat alone, since
  `AndroidLogger.debug()` is gated behind `Log.isLoggable`, a pre-existing, unrelated behavior
  worked around via `adb shell setprop log.tag.<TAG> DEBUG` once diagnosed).

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**299 written, 299 passing, 0 failing — up from 259.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 111 | +20 (`CountdownEngineNextTransitionTest.kt`) |
| `:core:common` | 4 | +4 (`LiveDefaultZoneClockTest.kt` — this module's first-ever test source set) |
| `:core:data` | 32 | Unchanged |
| `:core:database` | 40 | Unchanged |
| `:feature:events` | 33 | Unchanged |
| `:widget:engine` | 50 | +16 (`WidgetRefreshPlannerTest.kt` +7, `WidgetRefreshCoordinatorTest.kt` +9) |
| `:widget:glance` | 29 | Unchanged (the new Android-side classes — `AndroidAlarmScheduler`, `WidgetRefreshReceiver`, `WidgetRefreshSafetyNetWorker`, `GlanceWidgetRedrawer` — are thin platform wrappers verified on-device this session rather than by unit test; see Developer Notes) |

**Coverage** — `:core:domain` 97.0% lines, unchanged (`nextTransitionAt` is fully exercised by its
own 20-test file, so the module's aggregate percentage held rather than moved). The Android-side
alarm/receiver/worker mechanics are verified by real-device evidence (§9 of the new architecture
doc), not automated test — consistent with this project's standing practice for classes that are
thin platform wrappers around `AlarmManager`/`WorkManager`/`BroadcastReceiver` with no business
logic of their own to unit-test.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: 6 modified production files, 8 new production files, 3 new test files, 8
modified documentation files, 1 new documentation file, building on `main` at `5d6179a` (Session
11's final commit). No remote configured.

----------------------------------

## Developer Notes

- **"Check only the next midnight" is a plausible-looking algorithm that is provably wrong for a
  real label this domain already has.** `CountdownLabel.NextWeek`'s window re-anchors to a
  shifting `today`, so the label can stay unchanged across several consecutive local midnights.
  The bug was caught by manually tracing a label day-by-day across a real date range with Python,
  not by intuition — worth remembering that "the next boundary" and "the next thing that actually
  changes" are different questions whenever a label's own window can shift under it.
- **A `@Singleton` built from a "read the live system value" API is not guaranteed to stay live.**
  `Clock.systemDefaultZone()` looks like exactly the right tool for "the device's current zone,"
  and reads correctly on every call to `.instant()` — but its `.zone` is captured once, at
  construction, and a `@Singleton` binding means "once" means "once per process," not "once per
  call." This bug (D-026, present since Milestone 2) was invisible for nine sessions specifically
  because nothing before this session's real-device timezone test exercised a live zone change
  against an already-running process — a category of bug that architecture review and unit tests
  alike are structurally unable to catch, since both operate within one (implicitly static) process
  lifetime. Worth checking any other `@Singleton`-scoped "current system value" the same way.
- **`am force-stop` and `am kill` are not the same test, and confusing them cost real time this
  session.** An early attempt to background the app for the "process not in use" verification used
  `am force-stop`, which — correctly, per D-052's own reasoning — cancelled the pending alarm
  outright (`dumpsys alarm` showed `Reason=pi_cancelled`). That is exactly the intended Force Stop
  behavior, but it is a different test than "the process was reclaimed while backgrounded," which
  needed `am kill` instead (a normal low-memory-style reclaim that leaves scheduled alarms intact).
  Worth remembering as a standing distinction for any future device verification that needs to
  simulate "app not in use" without accidentally simulating Force Stop instead.
- **`dumpsys alarm` is a more reliable verification technique than logcat for confirming scheduled
  system work exists,** independent of the codebase's own logging configuration. This session's
  new debug logs didn't appear in logcat at first — not a code bug, but `AndroidLogger.debug()`'s
  pre-existing `Log.isLoggable` gate, which silently suppresses debug logs unless the tag is
  explicitly enabled (`adb shell setprop log.tag.<TAG> DEBUG`). `dumpsys alarm` needed no such
  configuration and gave a direct, unambiguous answer throughout — including the moment that first
  revealed BUG-R013, well before the logging gate was even diagnosed.
- **A background/orchestration class with real business logic (deciding *when*, deciding *which*)
  belongs behind an interface a fake can implement — a background class that's just a thin call
  into a platform API does not need the same treatment.** `WidgetRefreshCoordinator`'s logic (what
  order to redraw/compute/reschedule in) is fully unit-tested via `AlarmScheduler`/`WidgetRedrawer`
  fakes; `AndroidAlarmScheduler` and `WidgetRefreshReceiver` themselves have no decision logic of
  their own to fake around, and were verified correct the only way that's meaningful for them: on
  a real device, against the real `AlarmManager`.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.
  Useful this session specifically: `adb shell dumpsys alarm`, `adb reboot` +
  `adb wait-for-device` + polling `getprop sys.boot_completed`, `adb shell cmd alarm
  set-timezone <tz>`, `adb shell am kill <package>` (not `am force-stop`, unless Force Stop
  itself is what's being tested).

----------------------------------

## Requires approval before Session 13

1. **Notifications (Milestone 7), or further Milestone 5/8 work** — background refresh
   infrastructure is now delivered and real-device verified; the natural next step is either
   Notifications (which can now share this session's coalesced-alarm mechanism) or finishing
   Milestone 5's widget-sizing loose ends (real `WIDE` confirmation) or the rest of Milestone 8
   (Chronometer ticking, R8, Baseline Profiles, the full a11y pass).

----------------------------------

## Estimated Progress

```
Overall Progress            59%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             100%   (Session 11: lifecycle tabs, gestures, live preview — complete for V1)
Widget Engine                98%   (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget Themes & Sizes        70%   (responsive 2×1/2×2/4×2 delivered — Milestone 5B;
                                     real WIDE confirmation and multi-widget polish remain)
Background Refresh           90%   (Session 12: coalesced alarm scheduler delivered and
                                     device-verified; Chronometer ticking still open)
Notifications                 0%
Billing                       0%
Testing                      80%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
