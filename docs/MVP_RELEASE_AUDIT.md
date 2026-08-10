# CountFlow — MVP Release Audit

**Session 15. Feature freeze in effect.** This document's job is to find every reason CountFlow
should not ship, not to defend prior decisions. Every finding below is classified
**BLOCKER / HIGH / MEDIUM / LOW / POST-MVP** and every claim is either a real measurement taken
this session, a citation to an existing measurement/test, or explicitly marked as reasoned rather
than measured. Nothing here is fabricated confidence.

Companion documents: `docs/PRIVACY_DATA_INVENTORY.md` (Phase 10 in full), `docs/RELEASE_CHECKLIST.md`
(practical, checkbox-oriented). Read `SESSION_SUMMARY.md`'s Session 15 entry for the narrative
version of this session.

---

## Phase 1 — Release build

**`./gradlew :app:assembleDebug`** — BUILD SUCCESSFUL. **`./gradlew :app:assembleRelease`** — BUILD
SUCCESSFUL, produces `app-release-unsigned.apk` (30.5 MB, versus debug's 42.1 MB — the difference
is debug symbols and tooling, not R8 shrinking, since minification is off — see below).
**`./gradlew :app:bundleRelease`** — BUILD SUCCESSFUL, produces `app-release.aab`.

- **R8/minification is disabled for release builds** (`isMinifyEnabled = false`,
  `isShrinkResources = false` in `AndroidApplicationConventionPlugin.kt`) — a standing decision
  since Milestone 1 ("Turned on with the R8 rules pass in Milestone 8"), confirmed still in effect:
  no `mapping.txt` was produced by this session's release build. **MEDIUM** — a real release build
  today ships unobfuscated and unshrunk. Not fixed this session (release-blocking-fixes-only scope,
  and this was already a tracked, deliberate deferral — `TODO.md` P3) but restated here because a
  release audit should not treat a deferred decision as invisible.
- **No release signing configuration exists** — confirmed by searching the repo for `.jks`/
  `.keystore` files and `signingConfig`/`storeFile` references in every build script: none found.
  `app-release-unsigned.apk` is genuinely unsigned; `app-release.aab`'s `signReleaseBundle` task
  ran but produced an unsigned artifact (`jarsigner -verify` confirms "jar is unsigned"). **BLOCKER**
  — the app cannot be uploaded to Google Play without a real signing key or Play App Signing
  enrollment. Per this session's own instruction, no signing credentials were created or configured
  — this is an owner action (§"Owner actions," below), not an engineering gap.
- **No debug-only code leaks into the release build.** The release APK's `AndroidManifest.xml` has
  no `android:debuggable` attribute (defaults to `false` — the secure, correct state) and no
  `androidx.compose.ui.tooling.PreviewActivity` entry (present in the debug manifest, correctly
  absent from release, confirming `ui-tooling`'s `debugImplementation` scoping works as intended).
  Searched the release APK's three `.dex` files directly for `PreviewActivity` and
  `androidx/compose/ui/test/manifest` class references — zero matches. `EventWidgetPreviewKt` and
  `WidgetPreviewCardKt` (found in the release dex) are CountFlow's own production live-preview
  *feature* classes (D-048, D-059), not Android Studio's `@Preview` tooling — confirmed by reading
  their source, not just their name.
- **No development URLs or placeholder secrets exist anywhere in the source tree** — grepped for
  API-key/secret/password/private-key patterns across every `.kt`/`.xml`/`.gradle.kts`/`.toml`
  file; zero matches (see Phase 11).

---

## Phase 2 — Manifest audit

Read from the real merged manifest (`app/build/intermediates/merged_manifest/debug/.../
AndroidManifest.xml` and the `release` equivalent), not from individual module fragments.

| Permission | Declared by | Why | Verdict |
|---|---|---|---|
| `POST_NOTIFICATIONS` | `:app` (own manifest) | Reminder delivery, requested contextually (D-070) | Justified |
| `RECEIVE_BOOT_COMPLETED` | `:app` and `:widget:glance` (deduped by merge) | Re-arm both alarm subsystems after reboot | Justified |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | WorkManager (transitive) | Not declared or used by CountFlow's own code | Justified (not ours to remove) |
| `com.countflow.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AGP-generated | Standard tooling-generated self-permission for dynamically-registered receivers | Justified, not actionable |

No unnecessary permission was found. Full reasoning, including "does any data leave the device,"
lives in `docs/PRIVACY_DATA_INVENTORY.md` §5.

**Exported components**, every one individually justified, none exported merely by omission:

- `MainActivity` — `exported="true"`, required: it is the launcher entry point (`LAUNCHER`
  intent-category).
- `ReminderNotificationReceiver`, `WidgetRefreshReceiver` — both `exported="true"`, required: each
  listens for `BOOT_COMPLETED`/`TIMEZONE_CHANGED`/`TIME_SET`/`DATE_CHANGED`, broadcasts sent by the
  system (a different UID), which an unexported receiver cannot receive. Read both classes'
  `onReceive`: neither trusts any Intent extra, only `intent.action` (used for a log tag and one
  `TimeZone.setDefault(null)` branch) — a spoofed broadcast from a malicious co-installed app could
  at most trigger one extra, harmless recompute cycle, not leak data or execute anything
  attacker-controlled. **Reviewed, no issue.**
- `WidgetConfigurationActivity` — `exported="true"`, required: the launcher starts it directly
  (`android:configure`), a different UID, the standard and necessary shape for any widget
  configuration activity.
- `CountdownGlanceWidgetReceiver` — `exported="false"`. Verified correct, not overlooked: Glance
  widget updates are delivered via `AppWidgetManager`'s own internal mechanism using explicit
  component targeting, not an implicit broadcast a third party could spoof or that would need
  export to receive.
- Every other exported component in the merged manifest (`GlanceRemoteViewsService`, WorkManager's
  `SystemJobService`/`DiagnosticsReceiver`, `ProfileInstallReceiver`, and Glance's action-trampoline
  activities) belongs to AndroidX/Glance/WorkManager itself, each permission-gated
  (`BIND_REMOTEVIEWS`, `BIND_JOB_SERVICE`, `DUMP`) by the library that declares it — not CountFlow
  code, not actionable.

**No component is exported unnecessarily.**

---

## Phase 3 — Dependency audit

Full catalog: `gradle/libs.versions.toml`. No alpha/beta/RC dependency exists anywhere — Glance is
deliberately pinned to `1.1.1` **stable** rather than the sample's `1.3.0-alpha02` (D-007), and
every other AndroidX/Kotlin/Hilt version is a released stable version.

- **LOW** — `androidx-glance-preview`/`androidx-glance-appwidget-preview` are declared as
  `debugImplementation` in `widget/glance/build.gradle.kts` but nothing in the module actually uses
  a Glance `@Preview`/`@AppWidgetPreview` annotation (grepped, zero matches). Debug-build-only dead
  weight; zero release-build impact (these are `debugImplementation`, never packaged in
  `assembleRelease`/`bundleRelease`). Safe to remove; not done this session (feature-freeze,
  non-blocking).
- **LOW** — `androidx-test-espresso-core` and `androidx-test-ext-junit` are declared
  (`androidTestImplementation`) in `app/build.gradle.kts`, but no `androidTest` source set exists
  anywhere in the repository (confirmed: `find . -path "*/src/androidTest/*"` returns nothing).
  Declared, unused, zero release impact — the same class of cleanup opportunity as above, and the
  same reason it exists: `TODO.md`'s own testing-gaps section already names "no instrumented tests
  for the app's own screens" as a known, tracked hole this dependency was added in anticipation of.
- No duplicate library (two different HTTP clients, two different image loaders, etc.) exists,
  because CountFlow has none of either category — see Phase 10/`docs/PRIVACY_DATA_INVENTORY.md`.
- No architectural leftover from the Google sample this project started from remains. The one
  string match for "sample" in the codebase (`CountdownGlanceWidgetReceiver.kt`'s KDoc) is a
  citation explaining why a bug in *that* sample was deliberately not repeated — read in context,
  confirmed benign, not actual leftover code.
- No risky dependency upgrade was performed this session, per the brief's own instruction.

---

## Phase 4 — Data / Room audit

- **Schema version 2**, `exportSchema = true`, both `1.json` and `2.json` committed under
  `core/database/schemas/`.
- **`MIGRATION_1_2`** is a single additive nullable column
  (`reminders.delivered_for_scheduled_time`), covered by `MigrationTest.kt` (Session 13): inserts a
  real row at schema v1, runs the migration, asserts every original column survives and the new
  column reads `NULL`. Re-run as part of this session's full `./gradlew test` — still passing.
- **`fallbackToDestructiveMigration` is not used anywhere** — confirmed by a direct grep across the
  entire codebase (only match: the comment in `DatabaseModule.kt` explaining why it is deliberately
  absent). Room will throw on a missing migration rather than silently wiping the user's countdowns.
- **Foreign keys**: `widget_bindings.event_id` and `reminders.event_id` both cascade
  (`onDelete = CASCADE`, `onUpdate = CASCADE`) to `events.id`, each backed by an index. Deleting an
  event removes its bindings and reminders in the same transaction — confirmed by reading the
  entity annotations directly, consistent with every prior session's device-verified lifecycle
  testing (Session 11's full create→complete→archive→restore→delete sweep,
  Session 13's reminder-specific version).
- **Completed/archived/restored events**: `ACTIVE_REMINDERS_QUERY`'s own `WHERE` clause excludes
  completed and archived events at the SQL level — no separate cancellation code exists or is
  needed (D-066). This is the mechanism, not a claim about it; read directly in `ReminderDao.kt`.
- **No main-thread database access** — every DAO method is `suspend` or returns `Flow`; `Room
  .databaseBuilder` in `DatabaseModule.kt` has no `.allowMainThreadQueries()` call.
  `allowMainThreadQueries()` appears only in test files (`DatabaseTestCase.kt`, `MigrationTest.kt`),
  documented there as "safe here and only here — the tests are synchronous."
- Fresh install and upgrade-from-v1 are both exercised by `MigrationTest.kt`'s real-SQL-insert
  approach; a live device upgrade from an actual v1-schema installed APK was not re-performed this
  session (no v1 APK artifact exists to install from — the migration test is the practical
  equivalent and the standard way this class of claim is verified in this project).

**No BLOCKER, HIGH, or MEDIUM finding in this phase.**

---

## Phase 5 — Widget release audit, and Phase 6 — background reliability

**HIGH — this session could not obtain fresh, on-device confirmation of widget placement,
configuration, or reconfiguration.** Multiple real attempts were made against the `Pixel_9`
emulator's launcher widget picker (search-filtered single-result view, full alphabetical browse,
scroll-then-tap, drag gestures) — every attempt either mis-tapped a neighboring app's widget entry
or failed to expand/drag the CountFlow entry, consistent with **the same category of automation
fragility Session 10 already documented for the 4×2 WIDE case (TD-017: "three genuine
device-automation attempts... did not succeed")**, now observed for basic widget placement too, not
just resizing. This is a testing-coverage gap this session, not a discovered defect: no widget
code has changed since Session 12 (background refresh) — Sessions 13 and 14 touched
`:core:notifications` and `:feature:settings` respectively, neither of which the widget rendering
or configuration path depends on. The evidence this session *does* have:

- **Confirmed this session**: with zero widgets placed (a genuine clean-install state — see Phase
  14), `dumpsys alarm` shows **zero** `com.countflow.widget.action.REFRESH` alarms — the documented
  "no placed widgets → no alarm" behavior (`docs/WIDGET_REFRESH_ARCHITECTURE.md` §11) holds.
- **Relied on, not re-verified**: placement, configuration (event picker → style/progress/toggle
  customization → live preview), reconfiguration, removal, no-orphan-bindings, and multi-widget
  behavior are all real-device-verified in prior sessions (8, 9, 10, 11), most recently exercised
  end-to-end in Session 14's widget-regression check (which used *already-placed* widgets from a
  prior session, not a fresh placement) — see `docs/SCREENSHOT_GUIDE.md` and
  `docs/RESPONSIVE_WIDGET_REVIEW.md` for that evidence in full.

**Phase 6 — background reliability**, similarly: this session did not re-run a fresh reboot or
timezone-change cycle (each takes several minutes and both were already exhaustively proven twice,
Sessions 12 and 13, with the real bugs those exact tests found — D-064, D-065 — already fixed and
re-verified at the time). No code in the refresh/notification scheduling paths changed in Sessions
14 or 15. This is a **MEDIUM**, deliberate scope decision given the session's time budget and the
strength of existing evidence, not a gap in understanding.

### 4×2 WIDE — release blocker check

Per the brief's own instruction, one focused attempt was made this session to obtain real WIDE
confirmation, using the `Pixel_9` emulator (no physical device available). The attempt did not
succeed — it hit the same widget-picker automation fragility described above before even reaching
the resize step. **Consistent with, not contradicting, TD-016/TD-017's standing status**: 4×2
WIDE has *never* been confirmed rendering on a real launcher, across every session that has
attempted it (8, 9, 10, and now 15). It **is** confirmed correct by Robolectric across all seven
styles (renders without clipping, duplication, or content loss) — the render logic itself is
tested; only the real-launcher visual confirmation is missing. **HIGH** in this audit's
classification (`KNOWN_ISSUES.md` tracks it as Medium in its own finer scale; this audit does not
manufacture new severity, only restates it plainly for a release-readiness decision). **Stated
clearly, not fabricated: WIDE is not verified on a real launcher as of this session.**

---

## Phase 7 — Reminder audit

**Fully re-verified live this session**, end to end, on a genuine clean-install state:

1. Created a real timed event ("Release Audit Test," 2026-08-10 07:50 local) through the actual
   UI, checked "Day of event."
2. The contextual `POST_NOTIFICATIONS` permission dialog appeared the instant the checkbox was
   checked, not before — confirmed correct (D-070's contextual-request policy unchanged).
3. `dumpsys alarm` confirmed exactly **one** `com.countflow.notifications.action.REMINDER_ALARM`
   scheduled for `2026-08-10 07:50:00.000`.
4. The alarm fired (logcat: `remindersDelivered=1 nextReminderAt=none`, ~07:52, within
   `setAndAllowWhileIdle`'s documented multi-minute inexactness) and the real notification appeared
   in the system tray: **"Release Audit Test" / "Expired"** — the genuinely-correct, real-time
   label at the moment of delivery (the event had passed by the time the inexact alarm fired), not
   a stale "Today." This is the exact behavior `docs/NOTIFICATION_ARCHITECTURE.md` §8 already
   documents and this session reproduced live.
5. Tapping the notification opened CountFlow directly to that event's edit screen — correct.
6. Exactly one notification existed in the shade at any point; no duplicate.

**All-day timezone-following, timed-event zone-pinning, past-reminder suppression, and permission-
denial handling were not re-exercised live this session** (each requires several minutes of
additional setup and was already proven with real-device evidence in Session 13, with no code
changes since) — relied upon, not re-verified, the same **MEDIUM** scope decision as Phase 6.

**No BLOCKER or new HIGH finding in this phase — the reminder pipeline that was live-tested this
session works correctly.**

---

## Phase 8 — Accessibility

- **Verified this session, structurally**: the Settings screen's Theme dialog exposes correct
  `RadioButton`/`selectableGroup` semantics; the Dynamic Color row exposes a `checkable="true"
  clickable="true"` node spanning the full row (not just the switch thumb); the disabled Privacy
  Policy row correctly reports `clickable="false"` to the accessibility tree — all read directly
  from `uiautomator dump` output, not assumed from source.
- **Verified this session, visually**: 200% font scale (Session 14) reflows every Settings row onto
  multiple lines without clipping or overlap.
- **Not re-verified this session**: TalkBack was not literally enabled and listened to end-to-end
  this session — the checks above confirm the correct semantics *exist* in the accessibility tree
  (which is what TalkBack reads from), not that a live narration sounds right. **LOW**, a
  verification-method gap, not a known defect — TalkBack narration for the app's other screens
  (`EventCard`'s merged semantics, the overflow menu's independent description) was previously
  confirmed with a real semantics-tree read in Session 11.
- **No critical action requires swipe alone** — confirmed by design (D-060): delete, complete, and
  archive are all reachable via the overflow menu on every row, on every tab; the swipe gesture on
  Upcoming rows is a shortcut, never the only path.
- Widget content descriptions (`docs/WIDGET_ARCHITECTURE.md` §4) and dialog/empty-state copy were
  not independently re-audited this session beyond what prior sessions already confirmed.

**No BLOCKER or HIGH finding in this phase.**

---

## Phase 9 — Performance / battery

**Measured this session** (labelled as such — every other performance claim in this project is
explicitly reasoned, not measured, and stays that way here):

| Metric | Measured value | Conditions |
|---|---|---|
| Cold start (`am start-activity -W`) | **2497 / 2532 / 2575 ms** across three consecutive runs | **Debug build**, `Pixel_9` emulator, immediately after `am force-stop` (genuine cold start), same session that had run extensive UI automation beforehand |
| Memory (`dumpsys meminfo`), fresh launch | **~95.8 MB PSS total** (Native Heap 10.9 MB, Dalvik Heap 8.9 MB, `.apk mmap` 39.8 MB, rest split across `.art`/`.so`/`.jar` mmaps) | Debug build, same conditions |
| Widget refresh alarms with 0 widgets placed | **0** | Confirmed via `dumpsys alarm`, matches documented behavior |

**HIGH** — the cold-start number is **more than three times** `ARCHITECTURE.md`'s 700 ms budget.
Stated plainly: **this is a real, measured number that crosses a defined target by a wide margin**,
not a reasoned estimate. It comes with real caveats that keep it from being upgraded to BLOCKER:
measured on a **debug** build (no R8, `debuggable` overhead, no Baseline Profile — none of which
exist for release yet either, per Phase 1's MEDIUM finding, but a signed *release* build would still
differ), on an **emulator** (not representative of real hardware), under a **loaded session**
(extensive prior automation in the same process). It has never been measured on a release build or
physical hardware, in this session or any prior one. **This is the honest state: a real number that
should not be ignored, and should not be over-interpreted as certain doom either — it needs a real
release-build, real-hardware measurement before a confident verdict either way.**

- **No profiler-measured CPU-during-idle, widget-refresh-cost, or reminder-processing-cost number
  exists** — battery reasoning remains alarm-count-based (`docs/WIDGET_REFRESH_ARCHITECTURE.md` §11,
  `docs/NOTIFICATION_ARCHITECTURE.md` §11), unchanged and not re-measured this session. **MEDIUM**,
  a standing, documented limitation, not new.
- **No leak or ANR was observed** during this session's real-device use (event creation, reminder
  firing, Settings interaction, extensive widget-picker interaction) — not the same as a dedicated
  leak-detection pass (LeakCanary or a heap-dump diff was not run), so this is an absence-of-symptom
  observation, not a clean bill of health under instrumentation. Not escalated beyond a note, since
  nothing crashed or visibly degraded across a long, heavy session.
- **No major optimization project was started**, per the brief's own instruction — the cold-start
  number is reported, not chased.

---

## Phase 10 — Privacy / data audit

Full inventory: `docs/PRIVACY_DATA_INVENTORY.md`. Summary: **zero network requests of any kind
anywhere in the codebase** (no HTTP client library exists at all), **zero analytics or advertising
SDK** (`:core:analytics`/`:core:billing` are genuinely empty scaffolds, not just unused), all data
(events, widget bindings, reminders, preferences) stored exclusively in on-device Room/DataStore,
backed up only through the user's own OS-level Android Backup, which the user controls.

**No BLOCKER or HIGH finding in this phase** — the underlying facts are as clean as a privacy audit
can find. The blocker is the *policy document*, not the app's behavior — see below.

### Privacy Policy — BLOCKER, owner action required

**A final Privacy Policy URL does not exist.** None was invented. `AboutUiState.privacyPolicyUrl`
remains `null` (Session 14, D-073); the About screen's Privacy Policy row correctly ships disabled
("Not yet available") rather than linking anywhere. Per this session's own instruction: **the app
must not be declared ready for public Play submission until a real URL is supplied and wired.**
Marked **RELEASE BLOCKER — OWNER ACTION REQUIRED** in `TODO.md` P0 since Session 14; restated here
as this session's own top-level finding, not downgraded.

---

## Phase 11 — Security audit

- **No hardcoded secrets, API keys, credentials, or private keys anywhere** — grepped the entire
  source tree for common secret patterns (`api_key`, `secret`, `password =`, PEM private-key
  headers, Google API key prefixes); zero matches outside expected, benign contexts (e.g.
  `PasswordVisualTransformation` type references, which are not secrets).
- **Every `PendingIntent` in the codebase uses `FLAG_IMMUTABLE`** — verified by reading all three
  construction sites (`AndroidNotificationAlarmScheduler`, `AndroidNotificationSender`,
  `AndroidAlarmScheduler`), each `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`.
  No mutable `PendingIntent` exists anywhere in the app.
- **Exported components**: covered in full in Phase 2. None unsafe.
- **No world-readable files** — Room and DataStore both use their libraries' private-by-default
  storage; no `MODE_WORLD_READABLE`/`MODE_WORLD_WRITEABLE` or equivalent appears anywhere.
- **No unnecessary permissions**: covered in Phase 2 / `docs/PRIVACY_DATA_INVENTORY.md` §5.
- **No unsafe database configuration**: covered in Phase 4 (no destructive fallback, no
  main-thread access, standard Room/SQLite sandboxing).
- **No intent-spoofing or deep-link-abuse risk beyond what Phase 2 already covers** — the app has
  exactly one deep-link-shaped path (a notification tap carrying `EXTRA_EVENT_ID`), delivered only
  through an app-created, `FLAG_IMMUTABLE` `PendingIntent`, never through an externally-triggerable
  implicit intent filter. `MainActivity`'s `LAUNCHER` intent-filter accepts no data URIs.
- **No sensitive data in logs** — logging (`Logger`/`AndroidLogger`) throughout the notification and
  refresh systems logs counts, reasons, and computed instants (`remindersDelivered=1
  nextReminderAt=...`), never event titles or other user content.
- **Release build is not debuggable** — confirmed in Phase 1 (the attribute is absent from the
  release manifest, which is the secure default).
- **Backup behavior**: reviewed in Phase 10 / `docs/PRIVACY_DATA_INVENTORY.md` §6 — permissive by
  design, already reasoned through and documented (D-037), no new concern found.
- **No cleartext-traffic configuration exists**, and none is needed, since no network code exists
  to make it relevant.

**No BLOCKER, HIGH, or new MEDIUM finding in this phase.**

---

## Phase 12 — Package / version / release metadata

| Field | Value | Verdict |
|---|---|---|
| Application ID / package name | `com.countflow` | Final, no placeholder |
| `versionCode` / `versionName` | `14` / `"0.4.9"` | Corrected in Session 14 (BUG-R015, D-072) after being frozen at `1`/`"0.1.0"` since Milestone 1; verified current and matching `CHANGELOG.md` |
| App label | `"CountFlow"` (`@string/app_name`) | Correct, not a placeholder |
| Launcher icon | Adaptive icon only (`mipmap-anydpi-v26`), no legacy PNG mipmaps | Deliberate, documented (minSdk 31 makes density-bucketed fallbacks dead weight) |
| Widget metadata | `countdown_widget_info.xml`, correct cell-size declaration since BUG-R009's fix (Session 8) | Verified against Android's own formula previously; unchanged this session |
| `minSdk` / `targetSdk` / `compileSdk` | `31` / `36` / `37` | Satisfies Google Play's 31 Aug 2026 API-36 requirement already |
| Release build configuration | Builds successfully, unsigned (Phase 1) | Signing is the outstanding blocker |

**One LOW, cosmetic finding**: `ic_launcher.xml`'s own comment cites "`KNOWN_ISSUES.md` (TD-002)"
when explaining why the redundant `-v26` mipmap qualifier is kept — TD-002 is actually about empty
scaffold modules, unrelated; the relevant entry is the `ObsoleteSdkInt` row in `KNOWN_ISSUES.md`'s
Accepted-warnings table. A stale citation, not a functional issue. Not fixed this session
(non-release-blocking, feature-freeze).

**No Google-sample branding or leftover resources remain** — confirmed in Phase 3.

---

## Phase 13 — Localisation audit

Not translating this session, per the brief. Auditing English-release readiness:

- **Date/time formatting is already locale-aware**: `TargetDateFormatter` uses
  `DateTimeFormatter.ofLocalizedDate`/`ofLocalizedTime` with an explicit `.withLocale(locale)` call
  — not a hardcoded format pattern. Confirmed by reading the formatter directly.
- **No RTL-unsafe layout code found** — grepped for `PaddingValues(left/right)`,
  `Modifier.absolutePadding`, and raw `paddingLeft`/`paddingRight` usage across the whole UI layer;
  zero matches. The app's `supportsRtl="true"` manifest flag is backed by actually RTL-safe
  (`start`/`end`) padding throughout, not just declared and hoped for.
- **Pluralization false alarm, checked and ruled out**: `AndroidNotificationSender.bodyFor` hard-
  codes `"${days} days to go"` / `"${days} days ago"` with no plural handling — but
  `CountdownLabel.InDays`/`DaysAgo`'s own domain-level KDoc guarantees `days` is "always two or
  more" (nearer values get their own `Tomorrow`/`Yesterday` tokens), a real, load-bearing invariant
  enforced in `CountdownEngine`, not an assumption. **Verified, not a bug**: "1 days to go" cannot
  actually occur.
- **TD-007 (full localisation pass) remains open**, unchanged, correctly still tracked as post-MVP
  scope — Session 14 added new strings (Settings/About) to the same, already-acknowledged gap
  rather than a new one.

**No BLOCKER, HIGH, or MEDIUM finding in this phase** — the English-only MVP release has no known
formatting bugs.

---

## Phase 14 — Clean install / upgrade

**Performed live this session**: `adb uninstall com.countflow` (removing every trace of prior
sessions' accumulated test data — a genuine clean slate), fresh `adb install`, first launch.

- First launch showed the correct empty state: **"Nothing coming up / Create a countdown for your
  next moment. / Create countdown"** — no crash, no dead end, no stale data from before the
  uninstall.
- Created the first event through the real UI (title, timed target, one reminder type) —
  succeeded, appeared correctly on the home list immediately ("Starting soon").
  This exercised database creation, DataStore initialization (defaults), and the reminder
  scheduling pipeline all from a genuinely fresh install, not an upgraded one.
- **Not performed this session**: an explicit "install an older APK, then install this session's
  APK over it" upgrade test. No prior-version APK artifact was kept to install from. This exact
  path — reinstalling a newer debug build over an existing installation with real data — has
  happened *organically*, many times, across every session in this project's 15-session history
  (each session installs a freshly-built APK over whatever the previous session left installed),
  and no session has ever reported data loss from it. **LOW**, a formal-test gap covered by a very
  large amount of informal, repeated real-world exercise of the identical path.

**No BLOCKER, HIGH, or MEDIUM finding in this phase.**

---

## Phase 15 — Final engineering gate

```
./gradlew clean                                    → BUILD SUCCESSFUL
./gradlew assembleDebug                             → BUILD SUCCESSFUL
./gradlew test                                      → 340 tests, 0 failures, 0 errors, 0 skipped
./gradlew :core:domain:koverVerify                   → PASSED (97.0% lines, gated at 95%)
./gradlew :app:lintDebug                             → 0 errors, 17 warnings (unchanged since Session 9, all accepted/documented)
./gradlew :app:assembleRelease                       → BUILD SUCCESSFUL (unsigned APK)
./gradlew :app:bundleRelease                         → BUILD SUCCESSFUL (unsigned AAB)
```

All required gates pass. No test was skipped or ignored to reach this result.

---

## Findings, classified

### BLOCKER

1. **No release signing key or Play App Signing enrollment exists** — the app cannot be uploaded to
   Google Play. Owner action required; this session did not create one, per standing instruction.
2. **No final Privacy Policy URL exists** — required for Play Console submission; explicitly named
   as a release blocker by this session's own brief. Owner action required.

### HIGH

3. **Cold start measured at ~2.5–2.8 s on a debug build** — over 3× the 700 ms `ARCHITECTURE.md`
   target. Real, measured, not reasoned — but not yet measured on a signed release build or real
   hardware, so its true severity for a real release is still unconfirmed either direction.
4. **4×2 WIDE has never been confirmed rendering on a real launcher**, across every session that
   has attempted it, including this one (TD-017). Robolectric-confirmed correct; real-device
   confirmation remains outstanding.
5. **This session obtained no fresh real-device confirmation of widget placement, configuration,
   or reconfiguration** due to launcher/emulator widget-picker automation fragility. No evidence of
   an actual regression — no widget code changed since Session 12 — but this session adds no new
   confirming evidence on top of Sessions 8–14's existing proof.

### MEDIUM

6. **R8/minification remains disabled for release builds** — a real release build today ships
   unobfuscated and unshrunk (standing, deliberate deferral, `TODO.md` P3).
7. **No profiler-measured battery/CPU/leak numbers exist** — battery reasoning remains alarm-count-
   based; no dedicated leak-detection pass was run.
8. **Reboot and timezone-change regression were not freshly re-run this session** for widget refresh
   or reminders — relied on Sessions 12/13's existing proof, given no code changes since.
9. **Two unused debug/test-only dependencies** (`glance-preview`/`glance-appwidget-preview`,
   `androidx-test-espresso-core`/`androidx-test-ext-junit` with no `androidTest` source set) — zero
   release-build impact, cleanup opportunity only.

### LOW

10. Stale doc-comment citation in `ic_launcher.xml` (cites the wrong `KNOWN_ISSUES.md` entry).
11. TalkBack was verified via structural accessibility-tree semantics this session, not by
    literally enabling the screen reader and listening to narration.
12. Formal "install over an existing version" upgrade test was not performed this session, though
    the identical path has been exercised organically, repeatedly, across this project's history.

### POST-MVP (explicitly out of scope, not gaps)

Billing, AdMob, subscriptions, Pro gating, Android 16 Live Updates, cloud sync, accounts,
backup/restore beyond the OS-level default, recurring reminders and custom offsets, the
launcher-ticked `Chronometer` half of D-008, a full localisation pass (TD-007), R8 keep rules /
Baseline Profiles / macrobenchmarks as a dedicated effort, a real open-source-license enumeration
mechanism (D-073), and confirming `WidgetSizeClass`'s thresholds on a second launcher/device
(TD-016).

---

## Owner actions required (separate from engineering blockers)

1. Supply a real production signing keystore (or enroll in Play App Signing) so a real signed
   release build can be produced.
2. Supply a final Privacy Policy URL; wiring it into `AboutUiState.privacyPolicyUrl` is a trivial
   engineering follow-up once it exists.
3. Decide whether to invest in a physical device for the still-outstanding WIDE/widget-picker
   real-device confirmation, given the emulator's demonstrated automation fragility for this
   specific interaction.

---

## Final verdict

**MVP NOT READY** for public Play Store submission — specifically and only because of the two
BLOCKER items above, both of which are owner actions, not engineering defects. The MVP feature set
itself (event CRUD, responsive widgets, widget customization, background refresh, basic reminders,
essential settings) is complete, and this session's fresh, live regression test of the reminder
pipeline found it working correctly end to end. Once the two BLOCKERs are resolved, the minimum
additional actions worth taking before submission are:

1. Get a real, signed release build's cold-start time measured on real (or at least idle, unloaded)
   hardware, to confirm whether finding #3 is a real problem or a debug-build/emulator artifact.
2. Make an explicit, owner-accepted decision about shipping with 4×2 WIDE's correctness resting on
   Robolectric evidence only, or invest the time (ideally on a physical device) to get real
   confirmation first.

Nothing else found this session rises to blocking severity.
