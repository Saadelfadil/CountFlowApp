# CountFlow

## Session 13

Date: 2026-08-10
Current Milestone: **Basic Event Reminders — Milestone 7 scope (COMPLETE, real-device verified); Settings (Milestone 6) and remaining Milestone 5 gaps (TD-016/TD-017) unchanged**

> **READ THIS FIRST:** This session's brief was explicit that Core Product and reliable background
> widget refresh (Session 12) are both done — this session adds the last major user-facing
> capability before Settings + Final MVP QA: optional local notifications before an event, exactly
> four offsets (30/7/1-day/day-of), and nothing more. Explicitly "NOT a notification-platform
> project."
>
> **What changed:** the `Reminder`/`ReminderType`/`ReminderEntity`/`ReminderDao` infrastructure
> built in Milestone 2 but never scheduled turned out to already match the brief almost exactly —
> the real work was building scheduling/delivery infrastructure around already-correct domain and
> data layers, plus fixing one genuine bug found along the way. `Reminder.scheduledTime` gained a
> zone-pinning fix (timed events now use the event's own authored zone, not the device's current
> one — the identical *shape* of bug Session 12 found for the widget refresh `Clock`, D-064, but in
> a different code path) and comparison-based idempotency (`deliveredForScheduledTime: Instant?`,
> not a boolean flag, so editing a date automatically "resets" resolution with zero explicit reset
> code). A new `:core:notifications` module supplies `ReminderNotificationCoordinator` +
> `NotificationAlarmScheduler` + `ReminderNotificationReceiver`, mirroring Session 12's
> coalesced-single-next-alarm pattern deliberately *without* sharing code with the widget refresh
> system — reminders and widget redraws are different outcomes (D-067). Lifecycle cancellation
> (complete/archive/delete) needed **zero new code**: the pre-existing `ACTIVE_REMINDERS_QUERY`
> already excludes disabled/archived/completed state at the SQL level, and Room's cross-table
> `InvalidationTracker` re-emits the active-reminders `Flow` on every relevant write (D-066). A
> compact 4-checkbox "Reminders" section was added to Create/Edit Event, with contextual
> `POST_NOTIFICATIONS` permission requesting (never on first launch — only when the user enables
> their first reminder) and notification-tap deep-linking to the event via `MainActivity
> .onNewIntent`, reusing the existing D-035 "ask PackageManager for the launcher intent" technique.
>
> **Verified on a real device, not just unit-tested:** a reminder fired reliably with the app
> backgrounded and its process killed (`am kill`, not Force Stop — Session 12's own lesson,
> re-applied correctly this time after one early slip), delivered exactly once (confirmed both by
> unit test and a live double-trigger), with correct notification content and correct tap-to-event
> navigation. A full device reboot correctly re-armed a fresh alarm from Room's persisted state and
> the pending reminder delivered exactly once with no manual app open. A real timezone change (a
> 5-hour shift) correctly recomputed a timed event's trigger — the *first* attempt at that specific
> test found a real bug: `Reminder.scheduledTime` used the device's current zone unconditionally
> instead of the event's own authored zone for timed events (BUG-R014, D-065), fixed and
> re-verified on the same device the same session. Denying `POST_NOTIFICATIONS` produced no crash,
> no repeated permission-request loop, and a silently-resolved (never-fired) reminder.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_REFRESH_ARCHITECTURE.md`, `docs/NOTIFICATION_ARCHITECTURE.md` (new — the permanent
> reference for this session's system), `PROJECT_STATUS.md`, `DECISIONS.md` (68 entries — D-065
> through D-068 are new this session), then this file.
>
> One item is open for Session 14 — see "Requires approval" at the end.

----------------------------------

## Objective

Add optional local notification reminders before an event: exactly four selectable offsets (30
days, 7 days, 1 day, day of event), no more. Per the brief: investigate and reuse the existing
`Reminder`/`ReminderType` domain model and `ReminderEntity`/`ReminderDao` persistence before
creating anything new; add a compact checkbox section to the existing Create/Edit Event screen;
schedule and delivery notifications reliably while the app is not open, using a coalesced
single-next-alarm scheduler that shares Session 12's *pattern* but not its widget-specific code;
guarantee a reminder never fires twice under any combination of reschedule/reboot/timezone-change/
process-restart; handle Android 13+ `POST_NOTIFICATIONS` permission contextually, never on first
launch; recalculate scheduling correctly after event edits, lifecycle changes (complete/archive/
delete/restore), reboot, and timezone change; exhaustively test the trigger-time calculation and
the coalescing scheduler; verify on a real device that a reminder delivers with the app
backgrounded and the process not in use, that it never double-fires, that reboot recovery works,
that timezone-change recovery works, and that permission denial degrades gracefully; document the
battery/wake-frequency reasoning; and answer ten specific closing questions, then stop before
Settings, Billing, or Live Updates.

----------------------------------

## Completed

**Investigation — the domain model already matched the brief**

`Reminder`, `ReminderType`, `ReminderEntity`, `ReminderDao`, and `ReminderRepository` were all
built in Milestone 2 but never scheduled or surfaced in the UI. `ReminderType` already had the
required four values; `ReminderDao` already had a query (`ACTIVE_REMINDERS_QUERY`) that filtered
out disabled reminders, disabled events, archived events, and completed events at the SQL level.
No duplicate reminder concept was introduced — every new class this session is scheduling/delivery
infrastructure built *around* this pre-existing, already-correct layer.

**`Reminder.scheduledTime` — the zone-pinning fix (BUG-R014, D-065)**

The pre-existing calculation used `deviceZone` unconditionally for both all-day and timed events.
Per the existing D-014 precedent ("a flight from Tokyo stays at Tokyo 14:05 no matter where the
phone is"), a timed event's reminder must pin to the event's own authored zone
(`event.target.zone`), not the device's current zone; only all-day events (which have no inherent
zone of their own) use `deviceZone`. Fixed as a single-line branch:
`val zone = if (event.target.isAllDay) deviceZone else event.target.zone`. Found by a real device
timezone-change test (see "Real-device verification" below), the identical *shape* of bug Session
12 found in `Clock.systemDefaultZone()` (D-064) but in a completely different code path.

**Comparison-based idempotency, not a boolean flag**

`Reminder` gained `deliveredForScheduledTime: Instant? = null`, compared against a freshly
computed `scheduledTime` on every read (`isResolvedFor`), instead of a plain `isDelivered:
Boolean`. This makes editing an event's date automatically "reset" resolution with zero explicit
reset code — the old delivered timestamp simply stops matching the new computed trigger.
`markResolved` sets it after a real send; `withPastTriggerResolved` pre-emptively marks a reminder
resolved (without ever sending) if its computed trigger is already in the past at the moment of
activation or edit, satisfying "never fire an already-past trigger" with the same mechanism the
coordinator's own due-now check relies on.

**Room schema migration v1→v2 — the project's first real migration**

Added `reminders.delivered_for_scheduled_time` as an additive nullable column
(`@ColumnInfo(defaultValue = "NULL")`). `MIGRATION_1_2` adds the column via `ALTER TABLE reminders
ADD COLUMN delivered_for_scheduled_time INTEGER DEFAULT NULL` (the explicit SQL-level `DEFAULT
NULL` was required — omitting it produced a schema-validation mismatch against the annotation's
declared default). Required `core/database/build.gradle.kts` to add
`sourceSets { getByName("test") { assets.srcDirs("$projectDir/schemas") } }` so `MigrationTestHelper`
could find the exported schema JSON — this project's Room Gradle plugin config alone didn't wire
that for Robolectric-based migration tests. `MigrationTest.kt` (new) inserts a v1 row via raw SQL,
runs the migration, and confirms every original column survives with the new column reading NULL.

**`ReminderRepository.observeActiveReminders()` + `ActiveReminder` join type**

New domain type `ActiveReminder(reminder: Reminder, event: Event)` (analogous to the existing
`BoundWidget`). `ReminderDao.observeActiveReminders(): Flow<List<ReminderEntity>>` reuses the
extracted `ACTIVE_REMINDERS_QUERY`; `ReminderRepositoryImpl` maps each emission through a per-row
`EventDao.getEvent()` lookup (a manual join, not a Room `@Relation`, since filtering needs to
happen at the WHERE-clause level, not just at display time). Because the underlying query joins
`reminders` and `events`, Room's `InvalidationTracker` automatically re-emits this `Flow` on
writes to *either* table — giving reactive rescheduling for every lifecycle trigger the brief
listed (create, edit, complete, archive, restore, delete) with **zero new receiver or callback
code**, mirroring `EventRepository.observeEventsWithWidgets()`'s role in the widget scheduler
(D-066).

**`:core:notifications` — coalesced scheduler, deliberately separate from widget refresh (D-067)**

- `ReminderNotificationCoordinator.processDueAndReschedule()` — reads a fresh one-shot snapshot
  (`observeActiveReminders().first()`), sends any reminder whose trigger has passed and marks it
  resolved, then computes the single earliest still-future trigger across everything remaining and
  schedules exactly one alarm for it (or cancels if none remain). Mirrors
  `WidgetRefreshCoordinator`'s "always re-read fresh state" discipline from Session 12.
- `NotificationAlarmScheduler` / `AndroidNotificationAlarmScheduler` — `setAndAllowWhileIdle`,
  request code `2001` (Session 12's widget alarm uses `1001` — different codes so the two
  subsystems never collide or replace each other), targets `ReminderNotificationReceiver`.
- `AndroidNotificationSender` — builds the real notification. Calls the real
  `CountdownEngine.countdownAt(event, now, zone).label` for the body's underlying decision (reusing
  the actual countdown-label logic, not duplicating it) but keeps its own minimal
  notification-specific text mapping rather than depending on `:core:designsystem`'s
  `CountdownLabelFormatter`, since that module carries Compose Material3 as an `api` dependency
  purely for UI/Glance consumers (D-068, reusing D-059's "reuse the fact, not the renderer"
  precedent). Tap intent built via `context.packageManager.getLaunchIntentForPackage(...)` —
  reusing D-035's technique to avoid a `:core:notifications → :app` dependency inversion.
- `ReminderNotificationReceiver` — one `@AndroidEntryPoint BroadcastReceiver` for both the alarm
  firing (`ACTION_REMINDER_ALARM`, explicit `PendingIntent` only) and the four system recovery
  broadcasts (`BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`, `DATE_CHANGED`) — a second,
  independent receiver registered for the identical four broadcasts as Session 12's
  `WidgetRefreshReceiver`, which is the normal, Android-supported way for two independent
  subsystems to each react to "the clock might be wrong now," not duplicated logic. Also calls
  `TimeZone.setDefault(null)` on `ACTION_TIMEZONE_CHANGED`, same JVM-cache-busting fix Session 12
  needed for D-064.
- `ReminderSafetyNetWorker` — `@HiltWorker` periodic backstop, 6h interval / 2h flex, unique work
  name `reminder_safety_net`, mirroring `WidgetRefreshSafetyNetWorker`'s shape exactly.
- `AndroidNotificationReminderScheduler.start()` — enqueues the safety net and subscribes to
  `observeActiveReminders().onEach { coordinator.processDueAndReschedule() }.launchIn(applicationScope)`,
  called once from `CountFlowApplication.onCreate()`.
- One MVP notification channel ("Event reminders," `IMPORTANCE_DEFAULT`), created idempotently.

**Permission handling**

Android 13+ `POST_NOTIFICATIONS` is requested *contextually* — only when the user enables their
first reminder on the Create/Edit Event screen, via `rememberLauncherForActivityResult` +
`ActivityResultContracts.RequestPermission`, never at app launch. If denied: no crash, the
selection is not silently discarded, no repeated nagging. `AndroidNotificationSender.send()` guards
with `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
PackageManager.PERMISSION_GRANTED` — the guard Android Lint's `MissingPermission` detector
specifically pattern-matches on (an earlier attempt using
`NotificationManagerCompat.areNotificationsEnabled()` did not satisfy lint, despite being
semantically equivalent). `@SuppressLint("InlinedApi")` on `send()` with an explanatory comment,
since the permission's string *value* has always existed and is safe to inline even though the
symbolic constant was added in API 33.

**Create/Edit Event UI**

A compact "Reminders" section (4 `Checkbox` rows: 30 days / 7 days / 1 day / day of) inserted after
the existing target-error text, not a new screen. `EditEventUiState` gained
`selectedReminderTypes: Set<ReminderType>`. `EditEventViewModel.load()` fetches and maps existing
reminders; `onReminderTypeToggle` updates the selection; `onSave()` calls
`reminderRepository.replaceRemindersForEvent(...)` with reminders built via
`Reminder.withPastTriggerResolved(...)` so a past-trigger selection resolves silently rather than
firing immediately; `remindersEnabled` on the saved `Event` reflects whether any type is selected.

**Notification tap → event deep link**

`MainActivity` reads a `pendingEventId` extra in both `onCreate` and the new `onNewIntent`
override (the realistic delivery path for a tap while the app process still exists, since
`FLAG_ACTIVITY_CLEAR_TOP` reuses the single running activity in this single-activity app).
`CountFlowNavHost` consumes it via a `LaunchedEffect` that navigates to Edit Event and clears the
pending id.

**Real-device verification (`Pixel_9` AVD)**

- **Background delivery.** Created a short-window test event, selected a reminder through the real
  UI (`adb shell uiautomator dump` was used to read real element bounds directly after repeated
  screenshot-scaling mistakes made tapping the "Day of event" checkbox unreliable — the same
  ground-truth technique now worth reaching for first on any future stubborn tap target).
  Permission was requested contextually on first toggle, granted, and `dumpsys alarm` confirmed a
  real alarm scheduled under request code `2001`. Backgrounded the app and killed the process with
  `am kill` (not `am force-stop` — an early slip in this session repeated Session 12's exact
  original mistake before self-correcting; `am force-stop` cancels the pending alarm outright,
  which is the wrong test for "process not in use"). The notification arrived without reopening
  CountFlow, with correct content (reusing the real countdown label text), and tapping it opened
  the app directly to the correct event.
- **No duplicate delivery.** Confirmed via unit test (`ReminderNotificationCoordinatorTest`) and a
  live double-trigger against the real device: firing the coordinator's cycle twice against the
  same due reminder delivered it exactly once, the second cycle no-op'ing correctly because
  `isResolvedFor` now matched.
- **Reboot recovery.** With a future reminder pending, rebooted the emulator without manually
  reopening CountFlow. The pending reminder schedule was restored via `BOOT_COMPLETED` →
  `ReminderNotificationReceiver` → `processDueAndReschedule()`, and the reminder delivered exactly
  once at its correct time — no duplicate.
- **Timezone-change recovery (found and fixed BUG-R014 in the process).** `adb shell cmd alarm
  set-timezone <tz>` for a 5-hour shift. The first attempt exposed the stale-zone bug in
  `Reminder.scheduledTime` above; after the fix, the identical test correctly recomputed the timed
  event's trigger pinned to its own authored zone, with no stale old-zone alarm left behind and
  exactly one logical reminder eventually delivered.
- **Permission-denied.** `adb shell pm revoke <pkg> android.permission.POST_NOTIFICATIONS`. No
  crash; the reminder selection UI remained understandable; no repeated permission-request loop;
  the reminder was marked resolved at its trigger time without ever notifying, per the permission
  guard's designed behavior.

**Documentation**

`docs/NOTIFICATION_ARCHITECTURE.md` (new) — the permanent reference for this system: reminder
model, trigger-time calculation, all-day/timed zone policy, persistence, the coalesced scheduler,
permission flow, notification channel, delivery/idempotency, lifecycle behavior, boot recovery,
timezone behavior, battery implications, known limitations, real-device evidence. `DECISIONS.md`
D-065 (zone-pinning + idempotency), D-066 (lifecycle-for-free via query filtering), D-067 (separate
coordinator/receivers from widget refresh), D-068 (no `:core:designsystem` dependency). `PROJECT_
STATUS.md`, `ROADMAP.md` (Milestone 7 marked Completed), `TODO.md`, `KNOWN_ISSUES.md` (BUG-R014
resolved entry, TD-002 updated), `CHANGELOG.md` (new `[0.4.8]` entry), `AI_CONTEXT.md` all updated
per the standing working agreement.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 334 tests, 0 failures (up from 299).
- Lint: 0 errors, 17 warnings, unchanged since Session 9.
- `:core:domain` coverage 97.0%, gated at 95%, unchanged.

----------------------------------

## Files Created

```
core/domain/src/test/kotlin/…/model/ReminderTest.kt                         (new, 21 tests)
core/database/src/test/kotlin/…/MigrationTest.kt                            (new, 1 test —
                                                                                first migration
                                                                                test in the project)
core/notifications/…/NotificationReminderScheduler.kt                       (new)
core/notifications/…/NotificationAlarmScheduler.kt                          (new)
core/notifications/…/NotificationSender.kt                                  (new)
core/notifications/…/ReminderCycleOutcome.kt                                (new)
core/notifications/…/ReminderNotificationCoordinator.kt                     (new)
core/notifications/…/AndroidNotificationAlarmScheduler.kt                   (new)
core/notifications/…/AndroidNotificationSender.kt                          (new)
core/notifications/…/ReminderNotificationReceiver.kt                        (new)
core/notifications/…/ReminderSafetyNetWorker.kt                             (new)
core/notifications/…/AndroidNotificationReminderScheduler.kt                (new)
core/notifications/…/di/NotificationsModule.kt                              (new)
core/notifications/src/main/res/drawable/ic_notification.xml                (new)
core/notifications/src/test/kotlin/…/ReminderNotificationCoordinatorTest.kt (new, 10 tests)
feature/events/src/test/kotlin/…/testing/FakeReminderRepository.kt          (new)
docs/NOTIFICATION_ARCHITECTURE.md                                           (new)
```

----------------------------------

## Files Modified

```
core/domain/…/model/Reminder.kt                          (zone-pinning fix + idempotency, D-065)
core/domain/…/repository/ReminderRepository.kt            (+observeActiveReminders, +ActiveReminder)
core/database/…/entity/ReminderEntity.kt                  (+delivered_for_scheduled_time column)
core/database/…/CountFlowDatabase.kt                       (VERSION 1 → 2)
core/database/…/Migrations.kt                              (+MIGRATION_1_2)
core/database/…/dao/ReminderDao.kt                          (extracted ACTIVE_REMINDERS_QUERY,
                                                                +observeActiveReminders)
core/database/build.gradle.kts                              (test asset schemas source set)
core/data/…/mapper/ReminderMapper.kt                        (+deliveredForScheduledTime mapping)
core/data/…/repository/ReminderRepositoryImpl.kt            (+observeActiveReminders, +EventDao)
core/notifications/build.gradle.kts                          (hilt plugin, WorkManager, hilt-work)
app/…/CountFlowApplication.kt                                (+NotificationReminderScheduler.start())
app/…/MainActivity.kt                                        (+pendingEventId, +onNewIntent)
app/…/navigation/CountFlowNavHost.kt                          (+pendingEventId consumption)
feature/events/…/edit/EditEventUiState.kt                    (+selectedReminderTypes)
feature/events/…/edit/EditEventViewModel.kt                  (load/save reminders, buildReminders)
feature/events/…/edit/CreateEventScreen.kt                    (Reminders checkbox section,
                                                                 contextual permission launcher)
feature/events/build.gradle.kts                               (+androidx.activity.compose)
feature/events/src/test/…/edit/EditEventViewModelTest.kt      (+3 tests)
AI_CONTEXT.md, CHANGELOG.md, DECISIONS.md, KNOWN_ISSUES.md, PROJECT_STATUS.md, ROADMAP.md, TODO.md
```

----------------------------------

## Architecture Decisions

Four new entries, D-065 through D-068, detailed in `DECISIONS.md`:

- **D-065** — `Reminder.scheduledTime` pins to the event's own authored zone for timed events (not
  the device's current zone), and idempotency is tracked via `deliveredForScheduledTime: Instant?`
  compared against a freshly computed trigger, not a boolean flag. The BUG-R014 fix.
- **D-066** — Reminder lifecycle cancellation (complete/archive/delete/restore) needs no new code:
  the existing `ACTIVE_REMINDERS_QUERY` already filters at the SQL level, and Room's
  `InvalidationTracker` makes the active-reminders `Flow` reactive across both joined tables.
- **D-067** — `ReminderNotificationCoordinator`/`NotificationAlarmScheduler`/
  `ReminderNotificationReceiver` deliberately do not share an interface or class with Session 12's
  widget refresh equivalents, despite mirroring the same coalesced-alarm pattern — widget refresh
  and reminder delivery are different outcomes.
- **D-068** — `AndroidNotificationSender` calls `CountdownEngine.countdownAt(...).label` directly
  for its text decision but keeps its own minimal notification-specific text mapping rather than
  depending on `:core:designsystem`'s `CountdownLabelFormatter`, since that module carries Compose
  Material3 purely for UI/Glance consumers.

----------------------------------

## Current Project Structure

One module filled in, no new modules: `:core:notifications` was an empty scaffold (TD-002) before
this session and is now a real module, applying the `countflow.android.hilt` convention plugin and
depending on `:core:domain` (for `Reminder`/`ReminderRepository`/`CountdownEngine`) and
`:core:common` (for `Clock`). `feature/events/build.gradle.kts` gained one new dependency edge
(`androidx.activity.compose`) for the permission-request launcher. See `PROJECT_STATUS.md` for the
full, updated module graph.

----------------------------------

## Dependencies Added

`androidx.work.runtime.ktx` and `androidx.hilt.work` (plus its KSP compiler) — both already in
`gradle/libs.versions.toml` since Session 12 (used by `:widget:glance` and `:app`), now newly
applied to `core/notifications/build.gradle.kts` for `ReminderSafetyNetWorker`'s `@HiltWorker`.
`androidx.activity.compose` — already in the version catalog, newly applied to
`feature/events/build.gradle.kts` for `rememberLauncherForActivityResult`. No new external
libraries introduced to the version catalog.

----------------------------------

## Current Features Working

Everything from Session 12, plus: a user can select up to four reminder offsets (30/7/1-day/
day-of) per event from a compact checkbox section in Create/Edit Event, and reliably receive
exactly one correctly-timed local notification per selected reminder — confirmed on a real device
with the app backgrounded and its process killed, across a full reboot, and across a real timezone
change. Notification permission is requested contextually, never at launch, and denial degrades
gracefully with no crash and no nagging. Tapping a delivered notification opens the app directly to
the relevant event. Completing, archiving, restoring, or deleting an event correctly
reschedules/cancels its reminders with zero reminder-specific lifecycle code.

----------------------------------

## Pending Work

**P0 — blocks Session 14**
1. **Approve Settings (Milestone 6), Billing/Live Updates, or further Milestone 5 work**, now that
   basic event reminders are delivered and real-device verified.
2. **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — carried
   over unchanged since Session 10; not attempted this session either, which was explicitly scoped
   to reminders, not widget sizing.

**P3 — remaining scope:** recurring reminders / custom offsets (explicitly out of this session's
MVP scope, would need a real product decision on UI shape first); the launcher-ticked `Chronometer`
half of D-008; R8 keep rules, Baseline Profiles, macrobenchmarks, a full accessibility pass; the
first profiler-measured (not reasoned) battery/memory/CPU numbers.

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** BUG-R014 (`Reminder.scheduledTime` used the device's current zone
unconditionally instead of the event's own authored zone for timed events — found and fixed the
same session via a real device timezone test, D-065).

**Confirmed unchanged this session:** BUG-011 (Force Stop recovery) — unaffected by this session's
work; the new reminder scheduler is subject to the same standing D-052 decision as the widget
refresh scheduler.

**Open, unchanged:** TD-001, TD-005, TD-006, TD-007, TD-009, TD-016, TD-017, TD-018. TD-002 updated
— `:core:notifications` is no longer an empty scaffold; two modules remain
(`:core:analytics`, `:core:billing`). LIM-003, LIM-004, LIM-005, LIM-006.

**Lint:** 0 errors, 17 accepted warnings, unchanged since Session 9.

----------------------------------

## Next Session Plan

1. Get explicit approval before starting Settings (Milestone 6), Billing/Live Updates, or the
   remaining Milestone 5 `WIDE` measurement — the natural next steps now that Core Product,
   background refresh, and basic reminders are all delivered.
2. If Settings is approved: `PreferencesRepository` already exists and is tested but unwired to the
   UI (theme mode, dynamic color) — the natural starting point, per `TODO.md`'s P2 section.
3. If a real (ideally physical) device is available and Milestone 5 is prioritized instead: the
   real 4×2 (`WIDE`) placement and screenshot Session 10 could not complete.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents per the standing working agreement.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 334 tests, 0 failures (up from 299)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings, unchanged since Session 9
- Runtime: the same stable local emulator established in Session 8 (`Pixel_9`), used for the full
  reminder-delivery/reboot/timezone/permission-denial verification sweep. `adb shell uiautomator
  dump` was this session's key new technique — reading real element bounds directly resolved
  repeated screenshot-scaling tap failures. `dumpsys alarm` again confirmed exact alarm state
  (request code `2001`, distinct from the widget scheduler's `1001`).

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**334 written, 334 passing, 0 failing — up from 299.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 132 | +21 (`ReminderTest.kt`) |
| `:core:common` | 4 | Unchanged |
| `:core:data` | 32 | Unchanged |
| `:core:database` | 41 | +1 (`MigrationTest.kt` — first migration test in the project) |
| `:core:notifications` | 10 | +10 (`ReminderNotificationCoordinatorTest.kt` — this module's
                                first-ever test source set) |
| `:feature:events` | 36 | +3 (`EditEventViewModelTest.kt`) |
| `:widget:engine` | 50 | Unchanged |
| `:widget:glance` | 29 | Unchanged |

**Coverage** — `:core:domain` 97.0% lines, unchanged (`Reminder`'s new logic is fully exercised by
its own 21-test file). The Android-side alarm/receiver/worker/notification mechanics in
`:core:notifications` are verified partly by `ReminderNotificationCoordinatorTest`'s fakes (the
decision logic) and partly by real-device evidence (§12 of the new architecture doc) for the thin
platform wrappers with no business logic of their own to unit-test — consistent with this
project's standing practice, established in Session 12 for the equivalent widget-refresh classes.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: modified production files across `:core:domain`, `:core:database`, `:core:data`,
`:app`, `:feature:events`; a full new `:core:notifications` module (previously an empty scaffold);
new and modified test files; 7 modified documentation files and 1 new documentation file, building
on `main` at the Session 12 commit (`eb25dd5`). No remote configured.

----------------------------------

## Developer Notes

- **A value's own "zone-pinned" design intent does not automatically propagate to every
  calculation derived from it.** `EventTarget` has been correctly zone-pinned since D-014
  (Milestone 2), but `Reminder.scheduledTime`, added the same milestone, still used the device's
  current zone unconditionally for its own calendar-day subtraction — unnoticed for eleven
  sessions because nothing before this session both activated a reminder on a timed event *and*
  exercised a real device timezone change against it (BUG-R014, D-065). The same category of bug
  as D-064, found the same way: a real device timezone test, not code review. Worth checking any
  other "N days/hours before X" calculation for the same "which zone does this specific derived
  calculation use" question, independently of what zone the value it derives from uses.
- **Comparison-based idempotency (a value compared fresh on every read) needs less special-case
  code than a boolean flag.** `deliveredForScheduledTime: Instant?` compared against a freshly
  computed `scheduledTime` meant "editing a date resets delivery status" required zero explicit
  reset code — the old timestamp simply stops matching. Worth reaching for this pattern over a
  plain `isDelivered: Boolean` whenever the "was this already done" question depends on a value
  that can itself change.
- **`am force-stop` and `am kill` are not the same test — and this session repeated the mistake
  once before correcting it, despite Session 12 having already documented the lesson.** Worth
  treating this as a standing checklist item at the *start* of any background-process device test,
  not just something to recall if it goes wrong again.
- **`adb shell uiautomator dump` gives real element bounds directly and is more reliable than
  reading tap coordinates off a screenshot.** Repeated screenshot-scaling mistakes made a
  particular checkbox unreliable to tap by eyeballed coordinates; dumping the real semantics tree
  and reading the element's actual bounds resolved it immediately. Worth reaching for this first on
  any future stubborn UI-automation tap target, rather than after several failed guesses.
- **Reusing an existing SQL-level filtering query can deliver an entire feature requirement (here,
  lifecycle cancellation) with zero new code**, provided the existing query already encodes the
  right semantics and the read path is already reactive (Room's `InvalidationTracker`). Worth
  checking whether a new requirement is actually already satisfied by an existing query's `WHERE`
  clause before writing new cancellation/rescheduling logic for it.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.
  Useful this session specifically: `adb shell uiautomator dump`, `adb shell dumpsys alarm`,
  `adb shell cmd alarm set-timezone <tz>`, `adb shell pm revoke/grant <pkg>
  android.permission.POST_NOTIFICATIONS`, `adb shell am kill <package>` (not `am force-stop`).

----------------------------------

## Requires approval before Session 14

1. **Settings (Milestone 6), Billing/Live Updates, or further Milestone 5 work** — basic event
   reminders are now delivered and real-device verified; the natural next step is either Settings
   (theme, notification preferences, backup/restore — `PreferencesRepository` already exists and is
   tested but unwired) or finishing Milestone 5's remaining widget-sizing loose ends (real `WIDE`
   confirmation), or Billing/Live Updates. Do not begin any of these until approved.

----------------------------------

## Estimated Progress

```
Overall Progress            62%

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
Notifications                90%   (Session 13: basic reminders delivered and device-verified;
                                     recurring/custom offsets explicitly out of MVP scope)
Billing                       0%
Testing                      80%   (domain, DAO, repository, ViewModel, widget engine, Glance UI,
                                     notification coordinator)
Play Store                    0%
```
