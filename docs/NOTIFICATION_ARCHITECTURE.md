# CountFlow — Notification Architecture

**Audience:** a senior Android engineer who needs to understand how CountFlow delivers reminder
notifications without reading the code first. Every claim here names the real file and function
it describes, and is backed by real-device evidence (Session 13), not just reasoning — see §12.

**Scope:** MVP basic event reminders — four fixed offsets (30 days / 7 days / 1 day / day of
event), local notifications only. No FCM, no server, no notification history, no recurring
reminders, no custom offsets. This is deliberately smaller than
`docs/WIDGET_REFRESH_ARCHITECTURE.md`; read that document for the coalesced-alarm *pattern* this
one reuses, not for reminder-specific behavior.

---

## 1. The one idea that explains the rest of this document

**A reminder is a fact about an event, not a standing timer.** Nothing counts down to a
notification. Instead, for every reminder the user has turned on, the system computes the exact
instant it should fire — a calendar calculation, not a duration — and asks Android for one wakeup
at the earliest such instant across every reminder that has not already fired. When that alarm
fires, every currently-due reminder is delivered, each one is marked resolved so it can never fire
again for that same instant, and a new alarm is armed for whatever is now earliest. This is the
same loop `docs/WIDGET_REFRESH_ARCHITECTURE.md` §1 describes for widgets, run by an entirely
separate coordinator (§7 explains why).

---

## 2. The reminder model — mostly already there

`Reminder`, `ReminderType`, `ReminderEntity`, `ReminderDao`, and `ReminderRepository` were built in
Milestone 2, well ahead of this session — the domain model, the schema, and the DAO's
active-reminder query (`ACTIVE_REMINDERS_QUERY`, excluding disabled reminders, disabled events,
archived events, and completed events, all in one `WHERE` clause) already existed and needed no
redesign. What did not exist before Session 13: any code that actually scheduled or delivered a
notification, and any UI to turn a reminder on.

```kotlin
enum class ReminderType(val daysBefore: Int) {
    THIRTY_DAYS(30), SEVEN_DAYS(7), ONE_DAY(1), DAY_OF(0)
}

data class Reminder(
    val id: ReminderId,
    val eventId: EventId,
    val type: ReminderType,
    val timeOfDay: LocalTime,           // default 09:00
    val isEnabled: Boolean,
    val deliveredForScheduledTime: Instant? = null,   // new, Session 13 — see §5
)
```

**Default is off.** `EditEventUiState.selectedReminderTypes` starts empty, and there is no
separate master switch in the UI — `Event.remindersEnabled` is derived directly as
`selectedReminderTypes.isNotEmpty()` (`EditEventViewModel.buildEvent`). A user sees no reminder
checkboxes pre-checked on a new event; they explicitly opt in per offset.

---

## 3. `Reminder.scheduledTime` — the trigger-time calculator

```kotlin
fun scheduledTime(event: Event, deviceZone: ZoneId): ZonedDateTime
```

Unchanged in shape since Milestone 2; changed in one important way this session (§4). Steps back
whole *calendar* days from the event's target date, then applies `timeOfDay` — never
`target - Duration.ofDays(n)`, which would drift by an hour across a DST boundary. `DAY_OF` on a
timed event is a special case: it returns the event's own instant directly, not `timeOfDay` —
notifying at 09:00 about a 07:00 flight would be useless.

## 4. Timezone policy — the one real correctness fix this session made

**All-day events follow the device zone; timed events are pinned to their own authored zone.**
Before this session, `scheduledTime` used `deviceZone` unconditionally for *both* kinds of target.
That is correct for all-day (D-014's own "follows a traveller" policy) but wrong for timed: a
Tokyo-zoned flight's "seven days before" reminder should mean the same instant on the day it is set
as on the day it fires, regardless of where the phone physically is by then — exactly the same
zone-pinning `EventTarget` itself already gives the event's own instant.

```kotlin
val zone = if (event.target.isAllDay) deviceZone else event.target.zone
val start = event.target.startAt(zone)
```

**Confirmed on a real device, not just in a unit test** (§12): a timed event's reminder alarm was
scheduled, the device's real timezone was changed by five hours
(`adb shell cmd alarm set-timezone`), and the alarm's absolute epoch millisecond value was
*identical* before and after — `dumpsys alarm` displays the same instant in different local wall-
clock terms, exactly as intended.

**Recommended default (09:00) for all-day `DAY_OF` and every "N days before" case, in the
device's zone, per D-014** — the brief's own suggestion of "09:00 in the event's timezone" was
read as a request to document a final policy, not to introduce a second zone concept: an all-day
target has no meaningfully separate "authored timezone" to notify in beyond the same device-zone
policy the target itself already uses, and this codebase already had that behavior working and
tested before this session touched it. See DECISIONS.md D-065.

---

## 5. Idempotent delivery — `deliveredForScheduledTime`

**A reminder must never fire twice, and never fire for a trigger that had already passed the
moment it was activated.** Both rules are satisfied by one new nullable field,
`deliveredForScheduledTime: Instant?`, compared against a *freshly computed* `scheduledTime` on
every read — never read as a plain boolean:

```kotlin
fun isResolvedFor(event: Event, deviceZone: ZoneId): Boolean =
    deliveredForScheduledTime == scheduledTime(event, deviceZone).toInstant()

fun markResolved(event: Event, deviceZone: ZoneId): Reminder =
    copy(deliveredForScheduledTime = scheduledTime(event, deviceZone).toInstant())

fun withPastTriggerResolved(event: Event, now: Instant, deviceZone: ZoneId): Reminder =
    if (!scheduledTime(event, deviceZone).toInstant().isAfter(now)) markResolved(event, deviceZone) else this
```

Two call sites, two different reasons, one identical resulting shape:

- **`EditEventViewModel.buildReminders`** calls `withPastTriggerResolved` on every reminder being
  persisted — reused or newly created — *before* writing it. A newly-selected reminder whose
  offset has already passed (the brief's own example: a 30-day reminder on an event three days
  out) is silently marked resolved immediately, without ever notifying. So is a reused reminder
  whose trigger this exact edit just moved into the past by pulling the date closer. Neither case
  needs the coordinator to special-case "was this ever a legitimate future candidate" — by the
  time either reaches the database, an already-past trigger has already been resolved.
- **`ReminderNotificationCoordinator.processDueAndReschedule`** calls `markResolved` immediately
  after actually calling `NotificationSender.send`, and persists the result before returning.

**Why comparison, not a boolean.** Editing an event to a new date changes `scheduledTime`. A stale
`deliveredForScheduledTime` from before the edit simply stops matching the freshly computed value,
which is exactly "not resolved for the current schedule" — no explicit reset code needed anywhere.
Confirmed on-device (§12): editing "QuickTest" after its `DAY_OF` reminder had already fired
correctly re-armed a fresh alarm for the new time.

---

## 6. `ReminderNotificationCoordinator` — one full cycle

```kotlin
suspend fun processDueAndReschedule(): ReminderCycleOutcome {
    val now = clock.instant(); val zone = clock.zone
    val active = reminderRepository.observeActiveReminders().first()

    var delivered = 0
    val resolved = active.map { item ->
        if (item.reminder.isDueAt(item.event, now, zone)) {
            sender.send(item.reminder, item.event)
            val markedResolved = item.reminder.markResolved(item.event, zone)
            reminderRepository.upsertReminder(markedResolved)
            delivered++
            item.copy(reminder = markedResolved)
        } else item
    }

    val next = resolved.mapNotNull { it.reminder.pendingTriggerAt(it.event, now, zone) }.minOrNull()
    if (next != null) alarmScheduler.scheduleNextReminder(next) else alarmScheduler.cancelScheduledReminder()
    return ReminderCycleOutcome(delivered, next)
}
```

Every real trigger — the alarm firing, boot, timezone/time/date change, an event edited while the
app is open, the periodic safety net — calls this exact method. `observeActiveReminders().first()`
reads fresh state on every call rather than trusting a value passed in, the same discipline
`WidgetRefreshCoordinator.refreshAndReschedule` (Session 12) uses: every caller can just say
"something might have changed, recompute," with no bespoke path per trigger.

`ReminderRepository.observeActiveReminders(): Flow<List<ActiveReminder>>` (new this session) is
the DAO's `observeActiveReminders` query — the same `WHERE` clause `getActiveReminders()` always
used, now as a `Flow` — joined per-row with its event via `EventRepository`-equivalent lookups in
`ReminderRepositoryImpl`. Room's invalidation tracker registers both the `reminders` and `events`
tables the query reads from, so this re-emits on a write to either — a reminder toggled, an event
edited, completed, archived, restored, or deleted — with zero new receivers, exactly mirroring
`EventRepository.observeEventsWithWidgets()`'s role for the widget scheduler.

**Tested with fakes, no Android, no Robolectric**
(`ReminderNotificationCoordinatorTest.kt`, 10 tests): no reminders schedules no alarm; one future
reminder schedules exactly one alarm at its trigger; many future reminders coalesce to the
earliest; two reminders due at the same instant are both delivered in one cycle; an earlier
reminder added later replaces a later scheduled alarm; a later one added after an earlier one does
not; removing the earliest active reminder recalculates to the next earliest; editing an event to
a nearer date recalculates; a due reminder is delivered once and never redelivered on a later
cycle; a reminder whose trigger already passed before it was ever scheduled is never delivered.

---

## 7. Why this is a separate coordinator from the widget one, not a shared one

Reminder delivery and widget redraw are different outcomes with independent lifecycles — a
notification is a one-time, user-visible event with idempotency requirements a redraw does not
have (redrawing a widget twice is harmless; notifying twice is a real bug). The brief's own
guidance drew this line explicitly: share the *pattern* (coalescing, `AlarmManager` mechanics,
receiver design, boot/timezone recovery), not the code.

**What is shared:** the coalesce-to-one-alarm philosophy, `setAndAllowWhileIdle` over an exact
alarm, a fixed `PendingIntent` request code so a reschedule replaces rather than stacks, and one
receiver registered for the same four system broadcasts.

**What is not shared, and why two receivers exist for the identical four broadcasts.**
`WidgetRefreshReceiver` (`:widget:glance`) and `ReminderNotificationReceiver`
(`:core:notifications`) are two separate classes, each independently registered for
`BOOT_COMPLETED`/`TIMEZONE_CHANGED`/`TIME_SET`/`DATE_CHANGED` in their own module's manifest.
This is not duplicated code doing the same thing — Android dispatches the same broadcast to every
manifest-registered receiver that declared it, which is the normal, supported way for two
independent subsystems in one app to each react to "the clock might be wrong now." Merging them
into one receiver would mean a widget-specific class reaching into notification delivery (or vice
versa) purely to save a manifest entry — exactly the "force unrelated responsibilities into one
class merely to reduce file count" the brief warned against. See DECISIONS.md D-067.

**Request codes are deliberately different** (widget: `1001`, reminders: `2001`) — two independent
alarms are expected to coexist, one per system; sharing a code would make one silently replace the
other.

---

## 8. Android mechanics

- **`AndroidNotificationAlarmScheduler`** — `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, at, ...)`,
  same reasoning as the widget scheduler (D-063): no `SCHEDULE_EXACT_ALARM` permission needed,
  survives Doze, inexact by at most a few minutes — irrelevant for a reminder the user picked in
  whole-day increments.
- **`AndroidNotificationSender`** — builds and posts the real notification.
  - **Permission check**: `ContextCompat.checkSelfPermission(context,
    Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED`, returning early (no crash, no
    exception) if not. Correct on every supported API level with no `SDK_INT` branch, since
    `checkSelfPermission` for a permission the current platform doesn't runtime-check returns
    `GRANTED` unconditionally below API 33. **Confirmed on-device** (§12): permission revoked via
    `adb shell pm revoke`, the reminder's alarm fired, the coordinator logged
    `remindersDelivered=1` (the reminder is still marked resolved — see below), and no
    notification appeared. No crash.
  - **Why "still resolved" even when not delivered.** The reminder is not retried once permission
    is later granted — it was logically due at its scheduled time, and there is no queue of missed
    notifications to replay. The alternative (leave it unresolved so it fires the moment
    permission is granted) would mean a reminder from days ago suddenly appearing the instant a
    user flips a Settings toggle, which reads as a bug, not a feature.
  - **Notification body reuses the real `CountdownEngine`, not a second calculation.**
    `countdownEngine.countdownAt(event, clock.instant(), clock.zone).label` is called at the
    moment of delivery, then mapped to a short, notification-specific string (`bodyFor`) —
    `"${days} days to go"`, `"Tomorrow"`, `"Today"`, `"Expired"`, etc. This is the actual decision
    `CountdownEngine` already owns, not duplicated — only the final short-text rendering is
    separate, and deliberately so: reusing `:core:designsystem`'s `CountdownLabelFormatter` would
    mean pulling Compose Material3 into a module with no UI of its own (D-068). **Confirmed
    on-device**: a `DAY_OF` reminder that fired a few minutes after its target correctly showed
    "Expired," the real-time label, not a stale "Today."
  - **Tap target.** `context.packageManager.getLaunchIntentForPackage(context.packageName)` — the
    same "ask the `PackageManager` for whatever the launcher would open" technique D-035 already
    used for widget click targets, avoiding a `:core:notifications → :app` dependency inversion —
    with `EXTRA_EVENT_ID` attached. `MainActivity` reads it in both `onCreate` (cold start) and
    `onNewIntent` (the common case in practice, since `FLAG_ACTIVITY_CLEAR_TOP` reuses the single
    running activity instance rather than creating a new one) and feeds a `pendingEventId` into
    `CountFlowNavHost`, which navigates to `EditEventRoute` once, in a `LaunchedEffect`, after the
    graph's real start destination (`HomeRoute`) has already composed. **Confirmed on-device**: a
    tap opened CountFlow directly to the correct event's edit screen.
  - **Notification channel** (`event_reminders`, `IMPORTANCE_DEFAULT`) created idempotently on
    every send — `NotificationManager.createNotificationChannel` with the same id and settings is
    a no-op after the first real call, so no separate "has this run before" state is needed.
- **`ReminderNotificationReceiver`** — one `BroadcastReceiver`, `ACTION_REMINDER_ALARM` (delivered
  only via the explicit `PendingIntent` above, no manifest entry needed) plus the four system
  broadcasts. Busts the JVM-level zone cache (`TimeZone.setDefault(null)`) on
  `ACTION_TIMEZONE_CHANGED`, the identical fix D-064 made for the widget receiver — this
  coordinator also reads `clock.zone` for every all-day reminder's calendar subtraction.
- **`ReminderSafetyNetWorker`** — a `WorkManager` periodic backstop (6h interval, 2h flex, `KEEP`
  policy), the same shape as the widget system's own safety net, added even though the brief did
  not name it explicitly: a missed *reminder* is a worse user-facing failure than a stale widget,
  and "platform scheduling knowledge" reuse was explicitly permitted.

---

## 9. Contextual permission request

`POST_NOTIFICATIONS` is requested from `CreateEventScreen`'s `ReminderSection`, at the exact
moment a checkbox is checked — never on app launch, never speculatively:

```kotlin
onToggle = { type, enabled ->
    if (enabled && SDK_INT >= TIRAMISU &&
        checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(POST_NOTIFICATIONS)
    }
    onReminderTypeToggle(type, enabled)
}
```

The reminder selection itself is kept regardless of the system dialog's outcome — `onSave`
persists whatever is checked either way. If denied, `AndroidNotificationSender` simply does not
deliver (§8); the user sees their reminder still marked on in the UI, silently not firing, until
they grant the permission through system Settings. Building a full in-app explanation banner for
this state was judged more UI than a "keep it small" MVP session should add; the brief's own
"do not build a full Settings screen for this" instruction was read to cover this too. **Confirmed
on-device**: the system dialog appeared immediately on the first checkbox check, and only then.

---

## 10. Lifecycle behavior

| Event state change | Effect on its reminders | Where |
|---|---|---|
| Event created/edited with reminders selected | Persisted via `replaceRemindersForEvent`; a past-trigger reminder resolved silently | `EditEventViewModel.onSave` |
| Event completed | Excluded from `ACTIVE_REMINDERS_QUERY` (`e.is_completed = 0`) — no explicit cancellation code | `ReminderDao` |
| Event archived | Excluded (`e.is_archived = 0`) — same mechanism | `ReminderDao` |
| Event restored (un-completed/un-archived) | Automatically re-included next time the query runs; a still-future reminder becomes a real candidate again | `ReminderDao` (reactive) |
| Event deleted | Reminders cascade-deleted by the existing foreign key (Milestone 2) | `ReminderEntity`'s `ForeignKey` |
| Reminder toggled off | Simply absent from the next `replaceRemindersForEvent` call | `EditEventViewModel.buildReminders` |

None of these needed new code beyond what Milestone 2 already built and what
`observeActiveReminders()` (§6) already reacts to — the query-level filtering *is* the
cancellation mechanism, the same finding D-066 records for `getActiveReminders()`'s original,
unmodified `WHERE` clause.

---

## 11. Battery and wake-frequency reasoning

**How many notification alarms can exist simultaneously: exactly one**, for the whole app,
regardless of how many events or reminders are configured — the same coalescing guarantee
`docs/WIDGET_REFRESH_ARCHITECTURE.md` §11 gives for widget refresh, confirmed empirically the same
way (`dumpsys alarm` never showed more than one `com.countflow.notifications.action.REMINDER_ALARM`
entry at any point this session, including immediately after an edit and after a real timezone
change).

**This is a second, independent alarm from the widget refresh one, not merged into it.** Two
separate `AlarmManager` entries can exist at once — one per subsystem — which is a deliberate
tradeoff, not an oversight: merging them would mean one system's scheduling decision (when a
widget's label next changes) determining when the other's real, user-facing notifications fire, or
vice versa. A user with active reminders and placed widgets sees at most two wakeups a day in the
common case, not one — accepted as the correct price for keeping the two systems' correctness
independently reasoned about and independently testable, matching §7's reasoning for the receiver
split.

**Expected wake frequency.** A reminder alarm only exists when at least one reminder has a future,
unresolved trigger. Once fired, the coalesced-to-next-earliest behavior means a user with several
reminders across several events sees one wakeup per *distinct* remaining trigger instant, not one
per reminder — identical in shape to the widget system's own per-distinct-transition reasoning.

**Why this is not polling.** As with widget refresh, there is no periodic trigger driving the
primary mechanism — every alarm targets a specific computed instant. The one periodic component,
`ReminderSafetyNetWorker`, runs at most once per ~6–8 hours and performs the exact same
"compute-and-reschedule" work an alarm firing would; it is a backstop for a lost alarm, not a
second, higher-frequency wakeup source.

---

## 12. Real-device evidence

Every claim above was verified on a running `Pixel_9` AVD this session.

- **Delivery while backgrounded, process not in use.** A `DAY_OF` reminder was armed (confirmed
  via `dumpsys alarm`), the app was backgrounded and its process killed (`adb shell am kill`, the
  normal low-memory-reclaim path, not Force Stop), and the notification appeared in the system
  tray without the app ever being reopened. Logcat confirmed
  `remindersDelivered=1 nextReminderAt=none`, and a second, reactive coordinator run (triggered by
  the `upsertReminder` write itself) correctly delivered zero — proof of idempotency against a
  real, not simulated, second trigger.
- **Contextual permission.** The system "Allow CountFlow to send you notifications?" dialog
  appeared the instant the first reminder checkbox was checked, not before.
- **Tap-to-open.** Tapping the delivered notification opened CountFlow directly to the correct
  event's edit screen.
- **Reboot recovery.** With a reminder still pending, `adb reboot` was issued and the app was never
  manually reopened. `dumpsys alarm` showed a freshly re-armed alarm (a new alarm object, same
  target instant) immediately after boot; the reminder fired exactly once at its scheduled time,
  confirmed via logcat (`remindersDelivered=1`) and a single notification in the tray — no
  duplicate from the pre-reboot state, since `AlarmManager` state does not survive a genuine
  reboot in the first place.
- **Timezone change.** A timed event's reminder alarm's absolute epoch was recorded, the device's
  real timezone was changed by five hours, and the epoch was confirmed unchanged (§4) — the alarm
  object itself was freshly rescheduled (a new alarm id), proving the `TIMEZONE_CHANGED` receiver
  path ran and recomputed correctly, landing on the same instant by design.
- **Permission denied.** `adb shell pm revoke ... POST_NOTIFICATIONS`, then the reminder's alarm
  fired: no crash, the coordinator still logged `remindersDelivered=1` (resolved, not retried —
  §8), and the notification shade showed nothing.

---

## 13. Known limitations

- **No profiler-measured battery/memory number exists** — this document's battery reasoning is
  alarm-count-based, the same standing limitation `docs/WIDGET_REFRESH_ARCHITECTURE.md` §12 already
  states for the widget system.
- **The safety net's actual necessity is unverified** — no scenario this session tested lost an
  alarm through anything other than Force Stop or a genuine reboot, both correctly recovered
  through their own dedicated paths.
- **All-day reminder zone-following was not re-verified via a live device timezone change this
  session** — its logic is unchanged from before Session 13 (already using `deviceZone`
  unconditionally) and is covered by unit tests
  (`an all-day event's reminder genuinely shifts with a real device timezone change`,
  `ReminderTest.kt`); the timed-event case, the actual fix this session made, received the live
  device confirmation in §12 instead, since it carried the real correctness risk.
- **No physical device, only the `Pixel_9` emulator** — the same caveat every prior session's
  device work carries.
- **Notification copy is not localized** — plain English strings in `AndroidNotificationSender
  .bodyFor`, consistent with this project's existing TD-007-tracked gap elsewhere.
- **Reminders do not deep-link past the edit screen to a specific field** — tapping a notification
  opens the event's edit screen, not a dedicated "reminder detail" view, since none exists; this
  was the brief's own accepted fallback for a case that "requires significant unrelated
  architecture work."

---

## 14. Where to look for proof, not just claims

| Claim | Where it's verified |
|---|---|
| A timed event's reminder is zone-pinned; an all-day one follows the device | `ReminderTest.kt` — 21 tests, including both zone-behavior tests |
| Reminders never fire twice, never fire for an already-past trigger | `ReminderNotificationCoordinatorTest.kt` — 10 tests |
| Exactly one real alarm exists at a time | `adb shell dumpsys alarm`, this session, repeatedly — §12 |
| A migration from schema v1 preserves existing reminders | `core/database/.../MigrationTest.kt` |
| A reminder delivers with the app backgrounded and killed, exactly once | Real-device screenshot + logcat, §12 |
| Reboot recovery, no duplicate | Real-device `adb reboot` + `dumpsys alarm` + logcat, §12 |
| Timezone-change pinning | Real-device `cmd alarm set-timezone` + `dumpsys alarm`, before/after epoch comparison, §12 |
| Permission-denied handling | Real-device `pm revoke` + logcat + notification-shade screenshot, §12 |
