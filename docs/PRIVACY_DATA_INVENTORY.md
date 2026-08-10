# CountFlow — Privacy & Data Inventory

**Session 15, Final MVP Release Audit.** A factual inventory of exactly what CountFlow stores,
processes, and transmits — derived from reading the code, not from a policy template. This is the
intended factual basis for the eventual Google Play Data Safety declaration and Privacy Policy;
it makes no legal claims beyond what the code proves, and does not itself constitute a privacy
policy.

---

## 1. One-sentence summary

**CountFlow stores the countdown events, widget placements, reminders, and preferences a user
creates entirely on-device, makes zero network requests of any kind, and contains no analytics or
advertising SDK.**

---

## 2. What data exists, and where

### 2.1 Room database (`countflow.db`, on-device SQLite, schema v2)

| Table | Columns (user-authored or derived from it) | Purpose |
|---|---|---|
| `events` | `id`, `title`, `emoji`, `icon_key`, `category`, `target_epoch_millis`, `target_zone_id`, `is_all_day`, `accent_color`, `is_archived`, `is_completed`, `reminders_enabled`, `default_widget_style`, `default_progress_style` | The countdown events the user creates. `title` is free text the user types — it can contain a person's name (e.g. "Mom's birthday") or any other content the user chooses, but it is authored by the user, about the user's own life, and never leaves the device. |
| `widget_bindings` | `app_widget_id`, `event_id`, style/visibility overrides, `created_at` | Maps a placed home-screen widget to the event it shows, plus per-widget style overrides. Contains no data beyond what `events` already has, referenced by foreign key. |
| `reminders` | `id`, `event_id`, `type` (30/7/1-day/day-of), `enabled`, `delivered_for_scheduled_time` | Which reminder offsets are selected per event, and idempotent-delivery bookkeeping (Session 13, D-065). No content of its own beyond a reference to the event and a timestamp. |

Foreign keys cascade on delete (`widget_bindings.event_id`, `reminders.event_id` → `events.id`) —
deleting an event removes its widget bindings and reminders in the same transaction (Milestone 2).
No table stores anything about the *device*, the *user's identity*, or any third party.

### 2.2 DataStore preferences (`UserPreferences`, on-device, key-value)

| Key | Type | Purpose |
|---|---|---|
| `theme_mode` | enum (System/Light/Dark) | Appearance preference (Session 14). |
| `use_dynamic_color` | boolean | Material You toggle (Session 14). |
| `default_widget_style`, `default_progress_style` | enum | Pre-selected when creating an event. |
| `default_event_sort` | enum | Home list's remembered sort order. |
| `is_premium` | boolean | Always `false` — no billing implementation exists yet (Milestone 9, not built). Present so gating code can be written and tested ahead of the real entitlement. |
| `last_widget_update_epoch_millis` | long? | Used by the background refresh scheduler as a self-healing check (Session 12). |

No account identifier, email, name, or any cross-device identifier is stored here or anywhere else
in the app.

### 2.3 Notifications

Reminder notifications (Session 13) are built and posted entirely on-device via the platform
`NotificationManager`. The notification body is derived from the same event data already in
`events` (title, computed countdown label) — no new data is created or sent anywhere to produce a
notification.

### 2.4 What is explicitly *not* stored anywhere

No account system, no sign-in, no user identifier of any kind (not even an anonymous installation
ID). No location data. No contacts access. No device identifiers (IMEI, Advertising ID, etc.) are
read — confirmed by the absence of any such API call anywhere in the codebase (see §4). No crash
reporting or analytics payloads are collected or queued for later transmission.

---

## 3. Does any network request occur?

**No.** Confirmed by direct code inspection, not inference:

- No HTTP client library is on the classpath — no OkHttp, Retrofit, Ktor client, or raw
  `java.net.URLConnection`/`HttpURLConnection` usage exists anywhere in the codebase (grepped
  across every module; zero matches).
- No `network_security_config` or `android:usesCleartextTraffic` override exists in the manifest —
  the platform default (HTTPS-only if any network code existed) is untouched, which is moot since
  no network code exists to use it.
- `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` in the merged manifest are pulled in transitively
  by WorkManager (used for its own internal scheduling machinery, §8 of
  `docs/WIDGET_REFRESH_ARCHITECTURE.md` and §8 of `docs/NOTIFICATION_ARCHITECTURE.md`), not
  declared by any CountFlow code and not used by CountFlow for any network purpose.
- No Firebase, no Google Play Services SDK, no AdMob, no crash-reporting or analytics library
  (Crashlytics, Sentry, Amplitude, Mixpanel, or similar) appears anywhere in
  `gradle/libs.versions.toml` or any module's dependency list.

Every one of CountFlow's features — the countdown engine, widgets, reminders, and now settings —
operates entirely from data already on the device, computed on the device, and displayed on the
device.

---

## 4. Is there any analytics or advertising SDK?

**No.** `:core:analytics` and `:core:billing` exist as empty scaffold modules (boundaries
established since Milestone 1, TD-002) with **no implementation and no SDK dependency** — they
contain a build script and a namespace declaration only. `ARCHITECTURE.md` (D-009) always planned
real implementations behind these interfaces for Milestone 9, deliberately deferred so that
"cold start stays measurable and every other module stays testable without \[a third-party SDK\]
on the classpath" until that milestone is explicitly approved. As of this session, neither module
contains a single line of implementation code.

---

## 5. Permissions used, and why

| Permission | Why CountFlow needs it | Runtime-requested? |
|---|---|---|
| `POST_NOTIFICATIONS` | Deliver the reminder notifications the user explicitly opts into per event (Session 13). | Yes — contextually, the instant the user checks their first reminder, never on app launch (D-070's notification-status reasoning, §9 of `docs/NOTIFICATION_ARCHITECTURE.md`). |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the widget-refresh and reminder alarms after a device reboot, since `AlarmManager` state does not survive one. | No — normal (non-dangerous) permission, granted at install. |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | Declared by WorkManager itself (a transitive dependency), not by CountFlow's own code, and not exercised for any network or foreground-service purpose CountFlow implements. | No — normal permissions, granted at install. |

No permission requests any of the traditionally sensitive categories: no camera, microphone,
location, contacts, calendar (the system Calendar app, that is — CountFlow has its own,
unconnected, countdown "events"), SMS, call log, or storage beyond what the app's own private
directory already gets without a runtime prompt.

---

## 6. Backup and data portability

`data_extraction_rules.xml` permits both Android Auto Backup (cloud) and device-to-device transfer
for the full app data directory, which includes the Room database and DataStore preferences — a
deliberate choice, since restoring a user's own countdown events onto a new device is the behavior
a user expects, and none of the data qualifies as sensitive by any of the categories in §5. Widget
bindings travel with a backup too (Android's data-extraction-rules operate at file granularity, not
table granularity, so they cannot be selectively excluded without a second database — D-037,
`KNOWN_ISSUES.md`) but are self-correcting on the next launch: `WidgetLifecycleCoordinator
.pruneOrphans()` discards any restored binding that doesn't match a real, launcher-assigned widget
id on the new device.

---

## 7. Third-party SDKs on the classpath, in full

Every dependency in `gradle/libs.versions.toml` is either a first-party AndroidX/Jetpack library
(Compose, Room, DataStore, WorkManager, Navigation, Glance, Lifecycle) or Google's own Hilt (Dagger)
and Kotlin's own coroutines/serialization libraries — all build-time and runtime tooling, none of
which transmits data off the device on CountFlow's behalf. No Facebook SDK, no Firebase, no ad
network SDK, no analytics SDK, no crash-reporting SDK. See `docs/MVP_RELEASE_AUDIT.md` §Dependency
Audit for the full accounting.

---

## 8. Summary for a future Data Safety declaration

Based strictly on the above:

- **Data collected:** None, in the Play Console's sense of data that leaves the device. The user's
  own countdown events, reminders, widget placements, and appearance/sort preferences are stored,
  but never collected by CountFlow or any third party — they never leave local device storage
  except through the user's own OS-level backup/restore or device transfer, which the user
  controls, not CountFlow.
- **Data shared with third parties:** None. No third party is ever contacted.
- **Data deletion:** Uninstalling the app removes the local database and preferences entirely,
  since neither is backed by any server-side copy CountFlow controls.
- **Security practices:** All data is stored in the app's private, sandboxed storage using
  Android's standard mechanisms (Room/SQLite, DataStore) — no custom encryption is implemented or
  needed, since nothing is transmitted and Android's app-sandboxing already isolates this storage
  from other apps.

This document is the factual basis for that declaration and for the eventual Privacy Policy; it is
not itself either. See `TODO.md` P0 for the standing release blocker: a final privacy-policy URL
still needs to be supplied by the app's owner and wired into `AboutUiState.privacyPolicyUrl`
(D-073) before public submission — this document exists specifically so that policy can be written
accurately once that happens.
