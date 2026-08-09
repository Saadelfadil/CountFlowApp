# CountFlow — Widget Refresh Architecture

**Audience:** a senior Android engineer who needs to understand how CountFlow's widgets stay
current when the app is not open, without reading the code first. Every claim here names the real
file and function it describes, and is backed by real-device evidence (Session 12), not just
reasoning — see §9.

**Scope.** This document is one system, in full: how CountFlow decides *when* a widget's displayed
countdown will next need to change, how that decision becomes exactly one Android alarm, what
happens when that alarm fires, and what happens on reboot, timezone change, and Force Stop. It
supersedes `docs/WIDGET_ARCHITECTURE.md` §5's Milestone 4 description of the refresh flow, which is
now the state *before* this document's system existed, kept there as history.

---

## 1. The one idea that explains the rest of this document

**Nothing polls.** There is no timer that fires every minute, every 15 minutes, or on any fixed
schedule to ask "has anything changed yet." Instead, for every event with at least one placed
widget, the system computes the exact next *instant* its displayed label would change — a day
count ticking over, "Tomorrow" becoming "Today," a countdown reaching its terminal state — and
asks Android for exactly one wakeup at the earliest of those instants across every bound event.
When that alarm fires, every placed widget is redrawn against the database's current state, a new
"next instant" is computed, and one new alarm replaces the old one. The loop is a straight line:

```
compute next meaningful instant → schedule one alarm → alarm fires → redraw everything →
compute next meaningful instant → schedule one alarm → …
```

At any moment, **at most one CountFlow alarm exists** — confirmed, not just designed, via
`adb shell dumpsys alarm` throughout this session's device work (§9).

---

## 2. Module split and where each piece lives

The same boundary `docs/WIDGET_ARCHITECTURE.md` §1–2 already established for rendering applies
here: countdown-transition math is a domain fact, coalescing bound widgets into one wakeup is a
widget-engine policy, and only the actual `AlarmManager`/`WorkManager`/`BroadcastReceiver` calls
touch Android.

| Layer | File | Role |
|---|---|---|
| `:core:domain` | `countdown/CountdownEngine.kt` — `nextTransitionAt` | *Per event*: when does this one event's countdown next meaningfully change? Pure function, `(Event, Instant, ZoneId) → Instant?`. |
| `:widget:engine` | `refresh/WidgetRefreshPlanner.kt` | *Across events*: coalesce every bound widget's event to the single earliest `Instant`, deduplicated by event. |
| `:widget:engine` | `refresh/AlarmScheduler.kt`, `refresh/WidgetRedrawer.kt` | Two small interfaces — "schedule/cancel one alarm" and "redraw every widget" — so the orchestration below never touches Android directly. |
| `:widget:engine` | `refresh/RefreshOutcome.kt` | `data class RefreshOutcome(widgetsRefreshed: Int, nextRefreshAt: Instant?)` — what a cycle did, for logging. |
| `:widget:engine` | `refresh/WidgetRefreshCoordinator.kt` | One full cycle: read bound widgets from Room, redraw, compute the next global instant, (re)schedule. |
| `:widget:glance` | `refresh/AndroidAlarmScheduler.kt` | `AlarmScheduler` via the real `AlarmManager.setAndAllowWhileIdle`. |
| `:widget:glance` | `refresh/GlanceWidgetRedrawer.kt` | `WidgetRedrawer` via `CountdownGlanceWidget().updateAll(context)`. |
| `:widget:glance` | `refresh/WidgetRefreshReceiver.kt` | One `BroadcastReceiver` for the alarm firing *and* the four system recovery broadcasts. |
| `:widget:glance` | `refresh/WidgetRefreshSafetyNetWorker.kt` | A `WorkManager` periodic backstop — not the primary mechanism. |
| `:widget:glance` | `refresh/GlanceWidgetRefreshScheduler.kt` | Wires all of the above together at app startup and on every reactive database change. |

Nothing in `:core:domain` or `:widget:engine` imports `android.*` — both are pure Kotlin/JVM
modules (D-003, D-033), so `nextTransitionAt`, `WidgetRefreshPlanner`, and
`WidgetRefreshCoordinator` are all tested with plain JUnit, no Robolectric, no Android runtime.
See DECISIONS.md D-062 and D-063 for the full reasoning behind this split and its alternatives.

---

## 3. `nextTransitionAt` — the next-refresh calculator

```kotlin
fun nextTransitionAt(event: Event, now: Instant, deviceZone: ZoneId): Instant?
```

Pure, deterministic, and the one place this system decides *when* anything changes. Returns
`null` for a `COMPLETED` or `Expired` event — both are terminal, and scheduling a wakeup for an
event that can never change again would be exactly the unnecessary-wakeup mistake this system
exists to avoid.

**Why "check the next midnight" is the wrong algorithm.** The obvious first design — find the next
local midnight, check if the label changed, schedule that — is wrong for a real, non-hypothetical
case this session found by testing: `CountdownLabel.NextWeek`'s window
(`CountdownEngine.fallsInNextWeek`) re-anchors to a shifting `today` every day, so a `NextWeek`
label can stay **literally unchanged across several consecutive local midnights** even while the
underlying day count keeps decreasing. An event that's `NextWeek` today can still be `NextWeek`
tomorrow at midnight — scheduling a wakeup for that instant would fire, find nothing changed, and
have computed no better answer for what to do next.

**The actual algorithm.** For a still-future event, build a bounded superset of candidate
instants — not just the next midnight — and return the earliest one that actually changes
something:

- Every local midnight from tomorrow up to `min(daysUntilTarget, config.nearFutureDays + 14)` days
  out (the `+14`-day buffer is what covers the `NextWeek` plateau above — enough days that the
  walk always reaches a midnight where `calendarDaysRemaining` has dropped into
  `nearFutureDays`'s window, which is the earliest point a label change is guaranteed).
- The event's own start instant.
- For timed (non-all-day) events, the instant `config.imminentThreshold` before the start — the
  "Starting soon" transition.

Each candidate is checked with the same `countdownAt` the renderer itself uses; the first one
whose `CountdownLabel` or `CountdownStatus` differs from `now`'s is the answer. For a same-day or
already-past event, the walk is always exactly one day forward — there is no multi-day plateau
risk once the target date has arrived or passed.

**Exhaustively tested** (`CountdownEngineNextTransitionTest.kt`, 20 tests): far-future event,
tomorrow, today (not yet imminent), today (imminent, about to expire), the `NextWeek` plateau
itself (two tests — one proving the plateau, one proving the correct later transition), month
boundary, year boundary, leap day, DST spring-forward, DST fall-back, event timezone different
from device timezone, completed event (`null`), expired event in several shapes (`null`), an
all-day event (never imminent, per D-023), an all-day far-future event, and the calculator's
sensitivity to `CountdownConfig`'s thresholds.

---

## 4. Coalescing — one global wakeup, not one per widget

```kotlin
class WidgetRefreshPlanner(private val countdownEngine: CountdownEngine) {
    fun nextGlobalRefresh(
        boundWidgets: List<BoundWidget>,
        now: Instant,
        deviceZone: ZoneId,
    ): Instant? = boundWidgets
        .distinctBy { it.event.id }
        .mapNotNull { countdownEngine.nextTransitionAt(it.event, now, deviceZone) }
        .minOrNull()
}
```

`distinctBy { it.event.id }` is the entire coalescing mechanism, and it is why this scales cleanly
with widget count: two, ten, or a hundred widgets bound to the same event compute
`nextTransitionAt` exactly once for that event, not once per widget, and the global answer is
simply the earliest across every *distinct event* that has at least one widget watching it. No
widget with no placement anywhere ever contributes a candidate; no event contributes more than
one. Tested directly (`WidgetRefreshPlannerTest.kt`, 7 cases): one widget, multiple widgets on
different events, multiple widgets sharing one event (confirmed computed once, not N times), no
widgets at all (`null`), and an event whose own `nextTransitionAt` is itself `null` (terminal)
contributing nothing.

---

## 5. `WidgetRefreshCoordinator` — one full cycle

```kotlin
suspend fun refreshAndReschedule(): RefreshOutcome {
    val boundWidgets = widgetBindingRepository.getAllBoundWidgets()
    redrawer.redrawAll()

    val next = refreshPlanner.nextGlobalRefresh(boundWidgets, clock.instant(), clock.zone)
    if (next != null) alarmScheduler.scheduleExactRefresh(next) else alarmScheduler.cancelScheduledRefresh()

    return RefreshOutcome(widgetsRefreshed = boundWidgets.size, nextRefreshAt = next)
}
```

Every real trigger in this system — the alarm firing, a boot, a timezone change, an event edited
while the app is open, the periodic safety net — funnels through this exact method. That is
deliberate: it means "recalculate the schedule" has exactly one implementation, not one per
trigger, and every caller gets the same guarantee — a full redraw against Room's current state
(source of truth, D-002), immediately followed by scheduling (or, if nothing remains that will
ever change, cancelling) the single next alarm. `redrawAll()` happens **before** the reschedule
computation specifically so the "next transition" is always computed against data at least as
fresh as what was just drawn.

`AlarmScheduler` and `WidgetRedrawer` are the two seams that keep this class free of `Context`,
`AlarmManager`, or Glance — `WidgetRefreshCoordinatorTest.kt` (9 tests) verifies the full
orchestration with fakes for both, entirely off the Android runtime: one global alarm scheduled
correctly; an earlier event added later replaces an already-scheduled later alarm; a later event
added after an earlier one does *not* replace it; deleting the earliest event recalculates the
schedule to the next-earliest; removing the earliest widget recalculates the same way; editing an
event to a nearer date recalculates; multiple widgets on multiple events coalesce to one alarm; no
bound widgets schedules no alarm (and cancels any existing one).

---

## 6. The alarm itself

`AndroidAlarmScheduler` (`:widget:glance`) is the only class in this codebase that calls
`AlarmManager` for widget refresh:

```kotlin
manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), refreshPendingIntent(context))
```

Two choices worth being explicit about, both covered in DECISIONS.md D-063:

- **`setAndAllowWhileIdle`, not `setExactAndAllowWhileIdle`.** No `SCHEDULE_EXACT_ALARM`/
  `USE_EXACT_ALARM` permission is needed, it survives Doze, and it is inexact by at most a few
  minutes — irrelevant here, since nothing this app displays needs to change at the literal
  instant a transition boundary crosses. Confirmed on-device: a real alarm scheduled for
  `22:55:00.000` fired at `22:55:20` (§9).
- **Exactly one alarm, ever.** `refreshPendingIntent` always builds the same explicit
  `PendingIntent` — same request code (`1001`), same target component
  (`WidgetRefreshReceiver`), same action (`ACTION_REFRESH`) — so a second call to
  `scheduleExactRefresh` **replaces** the first via `FLAG_UPDATE_CURRENT`, never adds a second,
  competing one. Confirmed via `dumpsys alarm` throughout this session: exactly one
  `com.countflow.widget.action.REFRESH` entry existed at every check, including immediately after
  a timezone change recomputed and rescheduled it.

`ACTION_REFRESH` is delivered only through this explicit `PendingIntent` — it needs no manifest
`<intent-filter>` entry of its own, since an explicit-component `Intent` bypasses action-based
manifest matching entirely. Only the four genuine system broadcasts below are manifest-registered.

---

## 7. `WidgetRefreshReceiver` — one receiver, four system triggers plus the alarm

```xml
<receiver android:name=".refresh.WidgetRefreshReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.DATE_CHANGED" />
    </intent-filter>
</receiver>
```

Every one of these four means exactly the same thing to this system — "the schedule might now be
wrong, recompute it" — so one `@AndroidEntryPoint BroadcastReceiver` with one `runCatching` block
handles all five reasons (the four above, plus `ACTION_REFRESH`) identically:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)  // D-064
    applicationScope.launch {
        runCatching { coordinator.refreshAndReschedule() }
            .onSuccess { outcome -> logger.debug(TAG, "reason=... widgetsRefreshed=... nextRefreshAt=...") }
            .onFailure { error -> logger.error(TAG, "refresh cycle failed for reason=...", error) }
        pendingResult.finish()
    }
}
```

`goAsync()` is what makes this legal — `onReceive` must return quickly, but reading Room and
computing the next global refresh needs a coroutine, so `goAsync()`'s `PendingResult` is what keeps
the process alive long enough for `applicationScope.launch { … }` to actually finish before the
system considers the broadcast handled.

**Why these four, and no others.** `RECEIVE_BOOT_COMPLETED` is a normal permission (no runtime
prompt) required only so the `BOOT_COMPLETED` filter is actually delivered — every `AlarmManager`
alarm is cleared by a reboot with no exception, so nothing survives without re-registering.
`TIMEZONE_CHANGED` and `TIME_SET` (whose real Intent action string is
`"android.intent.action.TIME_SET"`, not `ACTION_TIME_CHANGED`'s constant name — a genuine Android
API naming quirk, verified directly) both mean a schedule computed against the old zone/time is
not just late, it is silently *wrong* — see D-064 for the real bug this exact scenario found.
`DATE_CHANGED` is a defensive re-check for the ordinary midnight rollover, in case a device ever
fires it without the alarm that was supposed to be scheduled for that exact moment. Two related
system broadcasts are **deliberately not handled**: `ACTION_MY_PACKAGE_REPLACED` (an app update
does not reliably clear `AlarmManager` state the way a reboot does — nothing here needs recovering)
and `ACTION_LOCALE_CHANGED` (a locale change affects only how a label renders, resolved at render
time by `CountdownLabelFormatter`, never *when* a label changes).

**No new receiver for the brief's other rescheduling triggers.** Event created/edited/deleted/
completed and widget added/removed/reconfigured are all already covered — with zero new
code — because `GlanceWidgetRefreshScheduler` (§8) already subscribes to
`EventRepository.observeEventsWithWidgets()`, a Room-backed `Flow` that re-emits on every one of
those writes, and every emission runs the identical `refreshAndReschedule()` cycle this receiver
also calls. Confirmed on-device: editing "QuickTest"'s target time through the real UI produced a
correctly re-scheduled real alarm with no receiver involved at all (§9).

---

## 8. `GlanceWidgetRefreshScheduler` — what starts all of this

Started once from `CountFlowApplication.onCreate()`. Three things:

1. **`pruneOrphanedBindings()`** — unchanged since Milestone 4 (`docs/WIDGET_ARCHITECTURE.md` §7).
2. **Subscribes to `observeEventsWithWidgets()`**, running `refreshCoordinator.refreshAndReschedule()`
   on every emission — this is what turns "an event changed" into "the alarm is now correct," with
   no receiver needed, as described in §7.
3. **`enqueueSafetyNet()`** — arms `WidgetRefreshSafetyNetWorker` via
   `enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)`, a
   `Duration.ofHours(6)` interval with `Duration.ofHours(2)` flex. `KEEP`, not `REPLACE`, is
   deliberate: this method runs on every process start, and `REPLACE` would reset the six-hour
   timer each time, turning "runs roughly every six hours" into "runs whenever the app last
   happened to start plus six hours."

The safety net exists for the case an alarm is lost to something other than an explicit Force
Stop — an aggressive OEM battery-optimization killer, for instance — not as a second primary
mechanism, and not as a Force Stop workaround (§10 explains why it cannot be one).

---

## 9. Real-device evidence

Every claim above was verified on a running `Pixel_9` AVD this session, not just reasoned about.

**Background refresh, app not reopened.** Edited "QuickTest"'s target to `22:55:00` today through
the real UI. `dumpsys alarm` confirmed a real `RTC_WAKEUP` alarm:

```
RTC_WAKEUP #7: Alarm{7596f8c type 0 origWhen 1786312500000 whenElapsed 1060548 com.countflow}
  tag=*walarm*:com.countflow.widget.action.REFRESH
  type=RTC_WAKEUP origWhen=2026-08-09 22:55:00.000 window=+2m28s499ms ...
```

The app was backgrounded (`KEYCODE_HOME`) and the process killed (`adb shell am kill`, the normal
low-memory-reclaim path — distinct from Force Stop, §10) — confirmed dead via `pidof`, then
confirmed the alarm survived the kill. Waited past `22:55`. Logcat showed the alarm fire and the
full cycle run with **no app process interaction of any kind**:

```
D WidgetRefreshReceiver: reason=com.countflow.widget.action.REFRESH widgetsRefreshed=2 nextRefreshAt=2026-08-09T23:00:00Z
```

`Top Alarms` in the same `dumpsys` output recorded `1 wakeups` for
`*walarm*:com.countflow.widget.action.REFRESH` — the system genuinely woke the device for this
alarm, not a coincidental process-alive redraw. A home-screen screenshot taken immediately after
confirmed the "QuickTest" widget had transitioned to **Expired** on its own; the unrelated "Swiss
Conference" widget (a different event, 25 days out) was correctly left unchanged.

**Reboot recovery.** `adb reboot`, and CountFlow was never manually reopened afterward. Both
widgets reappeared on the home screen with correct data (`Expired`, `25 days`) — process-start
logs (`AppWidgetServiceImpl: Trying to notify widget update...`, immediately followed by the
CountFlow process cold-starting) confirm this happened through the widget-restore/`BOOT_COMPLETED`
path, not a manual launch. `dumpsys alarm` confirmed a fresh alarm was scheduled post-boot — proof
of real recovery, since `AlarmManager` state does **not** survive a genuine reboot; every alarm
that exists after one was necessarily re-armed by this app's own code.

**Timezone-change recovery, including a bug it found.** `adb shell cmd alarm set-timezone
America/New_York` (from `Africa/Casablanca`, a 5-hour shift) triggered
`TIMEZONE_CHANGED` → `WidgetRefreshReceiver` → a real refresh cycle, confirmed by logcat. The
*first* attempt at this test exposed a genuine bug — the recomputed alarm landed on the exact same
absolute instant as before the zone change, not a ~5-hour-shifted one — traced to
`Clock.systemDefaultZone()` freezing its zone at construction (D-064). After the fix
(`LiveDefaultZoneClock`), the same test correctly produced a ~5-hour-shifted alarm
(`2026-08-10T04:00:00Z`, i.e. midnight in the *new* zone) — confirmed via `dumpsys alarm`, with
exactly one `com.countflow.widget.action.REFRESH` entry (`grep -c` = `1`), no stale old-zone alarm
left behind.

---

## 10. Force Stop — explicitly not defeated

Per D-052 (Session 10, owner decision, standing since BUG-011 was first found): **no attempt is
made to recover from Android's Force Stop state.** Force Stop cancels an app's `AlarmManager`
alarms and pending `WorkManager` work by platform design — the safety net worker in §8 is
cancelled exactly the same way the real alarm is, since Force Stop does not distinguish between an
app's wakeup sources. This system's entire goal is reliable *normal* background operation, not
overriding an explicit, user- or system-triggered Force Stop. BUG-011 (widget stuck until the app
is manually reopened after Force Stop) remains open, by design, exactly as D-052 decided — this
session did not revisit that decision or attempt a workaround.

---

## 11. Battery and wake-frequency reasoning

**How many alarms can exist simultaneously: exactly one**, for the whole app, regardless of how
many events or widgets exist — enforced structurally (§4's coalescing, §6's fixed `PendingIntent`
request code), confirmed empirically (`dumpsys alarm` never showed more than one
`com.countflow.widget.action.REFRESH` entry at any point this session, including immediately after
edits, timezone changes, and reboots).

**What causes an alarm.** Only `WidgetRefreshCoordinator.refreshAndReschedule()` schedules one, and
only when at least one bound event has a future transition — a terminal (completed/expired) event
with no other bound events schedules nothing.

**Expected wake frequency.**

| Scenario | Frequency |
|---|---|
| One far-future event (e.g. 218 days out) | Roughly once a day, at local midnight — not once a minute, not once an hour. Each wake redraws, recomputes (walking the bounded candidate set, §3), and reschedules for the *next* midnight — until the event enters its near-future window, where transitions become less frequent, not more (a `NextWeek`-labeled event can go several days between real transitions, per the plateau in §3). |
| An event about to go "Today" → "Starting soon" | Two wakes close together — the "Today" midnight transition, then the imminent-threshold instant — then nothing further until it expires. |
| Many widgets on many different events | Still one wakeup per *distinct global next transition* — coalescing (§4) means wake frequency is a function of how many distinct upcoming transition instants exist across all bound events, not how many widgets are placed. |
| Many widgets sharing one event | Zero additional wakeups over a single widget on that event — `distinctBy { it.event.id }` (§4) means N widgets on one event cost exactly what one widget would. |
| No active events, or no placed widgets | Zero alarms. `nextGlobalRefresh` returns `null`; `WidgetRefreshCoordinator` calls `cancelScheduledRefresh()` rather than leaving a stale one armed. |

**Why this is not minute-by-minute polling.** There is no periodic trigger driving the primary
mechanism at all — every alarm is scheduled for a specific, computed `Instant` that is the
*answer* to "when does something actually change," not a fixed interval hoping to catch a change
that may or may not have happened. The one periodic component in this system, the safety net
worker (§8), runs at most once per ~6–8 hours and does the same "compute and reschedule the real
alarm" work an alarm firing would — it is a backstop for a lost alarm, not a second, higher-
frequency wakeup source competing with the first.

---

## 12. Known limitations

- **The safety net's actual necessity is unverified.** No scenario this session tested lost an
  alarm through anything other than Force Stop (§10) or a genuine reboot (both correctly recovered
  through their own dedicated paths) — the worker exists because D-008 always planned one and
  because OEM battery-optimization killers are a documented, real-world risk this project has no
  way to reproduce on an emulator, not because a real loss was observed and this was the fix.
- **`setAndAllowWhileIdle`'s inexactness (a few minutes) is untested at its actual worst case.**
  This session's alarms fired within seconds of their scheduled instant on an active emulator with
  the screen frequently on; Doze-induced multi-minute deferral was not specifically forced or
  measured.
- **Multiple simultaneous timezone-affecting broadcasts were not tested together** — e.g. a
  timezone change arriving in the same moment as a `DATE_CHANGED` rollover. Each is individually
  correct and idempotent (every trigger runs the identical `refreshAndReschedule()`), so a race
  between them should self-correct on whichever runs last, but this was not specifically forced.
- **No physical device, only the `Pixel_9` emulator** — the same caveat every prior session's
  device work carries (TD-016 and others). OEM-specific alarm/Doze/battery-optimization behavior
  could differ from stock AOSP's.

---

## 13. Where to look for proof, not just claims

| Claim | Where it's verified |
|---|---|
| `nextTransitionAt` is correct across every named case, including the `NextWeek` plateau | `CountdownEngineNextTransitionTest.kt` — 20 tests |
| Coalescing reduces N widgets on one event to one computation | `WidgetRefreshPlannerTest.kt` — "multiple widgets sharing one event" |
| The orchestration reschedules correctly as bindings/events change | `WidgetRefreshCoordinatorTest.kt` — 9 tests, fakes only, no Robolectric |
| Exactly one real alarm exists at a time | `adb shell dumpsys alarm`, this session, repeatedly — §9 |
| A widget updates with the app backgrounded and the process killed | Real-device screenshot + logcat, §9 |
| Reboot recovery | Real-device `adb reboot` + `dumpsys alarm` + screenshot, §9 |
| Timezone-change recovery (and the bug it found) | Real-device `cmd alarm set-timezone` + `dumpsys alarm`, before/after D-064's fix, §9 |
| Force Stop is not defeated | D-052; `WidgetRefreshSafetyNetWorker`'s own KDoc |
