# CountFlow

## Session 14

Date: 2026-08-10
Current Milestone: **Essential Settings — Milestone 6 scope (COMPLETE, real-device verified); Milestone 5's remaining widget-sizing gaps (TD-016/TD-017) unchanged**

> **READ THIS FIRST:** This session's brief was explicit that Event CRUD, responsive widgets,
> widget customization, background refresh, and basic reminders (Sessions 1–13) are all complete —
> this session adds the last piece before Final MVP Release Audit: a small, polished Settings
> screen containing only Appearance, Notification status, and About. Explicitly not a
> general-purpose preferences dashboard — no billing, no AdMob, no backup/restore, no accounts, no
> cloud sync, several of which were in the original Milestone 6 spec (`ARCHITECTURE.md`) but are
> now out of MVP scope by deliberate product decision, the same kind of narrowing Milestone 7
> applied to notifications.
>
> **What changed:** `PreferencesRepository`'s `ThemeMode`/`useDynamicColor` fields — stored and
> unit-tested since Milestone 2, never read by any screen until now — now drive `CountFlowTheme`
> directly from `MainActivity`, with no dedicated ViewModel needed for two fields
> (`collectAsStateWithLifecycle` on the existing `preferences` `Flow`). A real `SettingsScreen` and
> `AboutScreen` replace the Milestone 1 placeholders: a Theme picker dialog (System default/Light/
> Dark), a Dynamic Color switch, a notification-status row backed by
> `NotificationManagerCompat.areNotificationsEnabled()` (correct on every Android version, not just
> 13+, D-070) that refreshes on every screen resume via `LifecycleResumeEffect`, a "Manage
> notifications" row opening Android's real per-app settings, and an About screen reading the
> installed package's actual version via `PackageManager` rather than `:app`'s `BuildConfig`
> (D-072). Privacy Policy and Open-source licenses render as honest, visibly-disabled placeholders
> — no fake URL, no new dependency pulled in just to enumerate licenses (D-073). The Milestone 1
> placeholder's "CountFlow Premium" entry point was deliberately not carried into the real screen
> (D-071), since Billing/Pro features are explicitly out of scope.
>
> **Verified on a real device, not just unit-tested:** System/Light/Dark all applied instantly and
> persisted across a full `am force-stop` process kill (DataStore, not in-memory state). Toggling
> Dynamic Color off visibly switched the app's accent from the wallpaper-derived color to
> CountFlow's static Material 3 palette. Two placed home-screen widgets kept their existing dark,
> translucent styling and accent completely unchanged through every theme/dynamic-color combination
> tested — the widget regression check the brief asked for, confirming D-069's "app UI only" policy
> holds in practice. Disabling and re-enabling notifications through Android's real system settings
> and returning to CountFlow flipped the status row correctly both directions with no restart. One
> real defect was found and fixed in the process: the app's `versionCode`/`versionName` had been
> frozen at `1`/`"0.1.0"` since Milestone 1 — invisible for thirteen real releases until this
> session's own About screen made it visible (BUG-R015, D-072).
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `PROJECT_STATUS.md`'s "Where important logic lives" table (no new architecture doc this session —
> Settings is a handful of small, self-explanatory classes), `DECISIONS.md` (73 entries — D-069
> through D-073 are new this session), then this file.
>
> One item is open for Session 15 — see "Requires approval" at the end.

----------------------------------

## Objective

Build a small, polished Settings screen containing exactly three sections — Appearance,
Notification status, About — reusing the existing `PreferencesRepository` rather than inventing a
second preference system. Per the brief: Theme (System/Light/Dark) and Dynamic Color, both
persisting across restart/process death/reboot; determine whether Global Appearance should affect
app UI only or app UI + widgets, and document the decision; a notification-status row reflecting
Android's actual state (not a second, conflicting preference alongside per-event reminder
selections), correct on every Android version including pre-13, with a path to Android's own
notification settings and no repeated permission nagging; About with app version, a Privacy Policy
row that never ships a fake URL, and an Open-source licenses row that never pulls in a heavyweight
dependency "merely" to exist; a `SettingsUiState`/`SettingsViewModel` using `StateFlow`, no direct
DataStore reads from composables; correct behavior when notification status changes outside
CountFlow and the user returns; accessibility (TalkBack labels, switch/radio semantics, touch
targets, large font scale, contrast in both themes); exhaustive tests where practical; real-device
verification of every claim, including a widget regression check; and answer eight specific closing
questions, then stop before Final QA, Billing, or Ads.

----------------------------------

## Completed

**Investigation — the theme preferences already existed, unused**

`PreferencesRepository`/`UserPreferences` (`:core:domain`) already had `themeMode: ThemeMode`
(`SYSTEM`/`LIGHT`/`DARK`) and `useDynamicColor: Boolean`, backed by `PreferencesRepositoryImpl`'s
DataStore implementation, both stored and unit-tested since Milestone 2. `CountFlowTheme`
(`:core:designsystem`) already accepted `darkTheme`/`dynamicColor` parameters. No new domain or
persistence code was needed — the work was wiring the existing pieces together and building the UI
that reads and writes them.

**`MainActivity` drives `CountFlowTheme` directly (D-069)**

`MainActivity` now injects `PreferencesRepository` via `@Inject lateinit var` (field injection,
matching the existing `@AndroidEntryPoint` pattern) and collects `preferences` as Compose state with
`collectAsStateWithLifecycle`. `darkTheme` is derived with a `when` on `ThemeMode`
(`SYSTEM → isSystemInDarkTheme()`, `LIGHT → false`, `DARK → true`); `dynamicColor` is read straight
from `preferences.useDynamicColor`. No dedicated ViewModel — two fields read from an existing `Flow`
did not justify one. `WidgetConfigurationActivity` was deliberately left unchanged (still following
the system theme unconditionally) — it is reached only via a launcher's "reconfigure" affordance,
not CountFlow's own navigation, and the brief scoped this work to "the app," not every activity in
the APK; documented as a narrow, accepted inconsistency in D-069 rather than silently left
unmentioned.

**Real `SettingsScreen` and `AboutScreen`, replacing the Milestone 1 placeholders**

- **Appearance**: a "Theme" row opening an `AlertDialog` with `RadioButton` rows for System
  default/Light/Dark (`Modifier.selectable(role = Role.RadioButton)` + `Modifier.selectableGroup()`
  for correct TalkBack group semantics); a "Dynamic color" row with a `Switch`, the whole row
  toggleable (`Modifier.clickable(role = Role.Switch)`) so the tap target isn't just the small
  switch thumb.
- **Notifications**: an "Event reminders" status row ("Allowed"/"Not allowed") and a "Manage
  notifications" row that builds `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` with
  `EXTRA_APP_PACKAGE` and starts it — opens Android's real per-app notification settings, confirmed
  on-device.
- **About**: a compact "About CountFlow" row (name + version) navigating to the existing `AboutRoute`
  destination, which shows the full version label, a Privacy Policy row, and an Open-source licenses
  row.
- `Icons.AutoMirrored.Filled.ArrowBack` and `ListItem` (both already proven elsewhere in the
  codebase) used throughout rather than pulling in `material-icons-extended` for decorative icons
  this small, essential-only screen doesn't need.

**Notification status: `NotificationStatusProvider` (D-070)**

A `fun interface NotificationStatusProvider { fun areNotificationsEnabled(): Boolean }`, real impl
`AndroidNotificationStatusProvider` wrapping `NotificationManagerCompat.from(context)
.areNotificationsEnabled()` — the version-uniform check for "will a notification actually reach this
user," not `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)` (which returns `GRANTED`
unconditionally below API 33 regardless of whether the user disabled notifications via the classic
per-app toggle, and would have produced exactly the "misleading messaging on versions where runtime
permission doesn't exist" the brief forbade). `SettingsViewModel.refreshNotificationStatus()` is
called from a `LifecycleResumeEffect(Unit)` in the Composable on every screen resume, not just at
construction, since the only way this value changes is the user leaving CountFlow for Android's
settings and coming back.

**App version: `AppVersionProvider` (D-072), and a real bug found while building it**

`AndroidAppVersionProvider` reads `context.packageManager.getPackageInfo(context.packageName, 0)`
and formats `"${versionName} (${longVersionCode})"` — the installed package's real version, not
`:app`'s `BuildConfig`, keeping `:feature:settings` decoupled the same way `AndroidLogger` already
avoids a direct `BuildConfig.DEBUG` reference. Building this exposed BUG-R015: `Android
ApplicationConventionPlugin`'s `versionCode`/`versionName` had been `1`/`"0.1.0"` since Session 2 and
never bumped despite thirteen real `CHANGELOG.md` releases since — invisible until a screen finally
read it back. Corrected to `14`/`"0.4.9"` to match this session's own release.

**About: Privacy Policy and Open-source licenses as honest placeholders (D-073)**

`AboutUiState.privacyPolicyUrl: String? = null` — always `null` this session, since no final URL
exists. The row renders visibly but disabled (dimmed, non-clickable, "Not yet available") rather
than being hidden or linking to a fake page. Open-source licenses renders the same way permanently
for now ("Coming soon") — a real enumeration mechanism would need Google's
`play-services-oss-licenses` plugin, a new dependency the brief explicitly said not to add "merely"
for this.

**Settings does not surface Premium (D-071)**

The Milestone 1 placeholder's "CountFlow Premium" action was not carried into the real screen;
`onNavigateToPremium` was removed from `SettingsScreen`'s and `settingsSection`'s signatures.
`:feature:premium`'s route stays registered in `CountFlowNavHost` for Milestone 9 — only the link
from Settings was removed, nothing was deleted.

**Real-device verification (`Pixel_9` AVD)**

- **Theme.** System default → Light → Dark all applied instantly via the real UI (`adb shell
  uiautomator dump` for exact `RadioButton`/row bounds, avoiding the repeated screenshot-scaling tap
  mistakes prior sessions hit). Confirmed surviving a full `am force-stop` (a genuine process kill,
  correctly distinguished from `am kill`'s no-op-on-foreground/backgrounded-but-recently-used
  behavior observed while testing this) — CountFlow relaunched still in Light.
- **Dynamic color.** Toggling off visibly changed the Appearance/Notifications/About section-header
  color and the switch's own color from a wallpaper-derived blue/violet to CountFlow's static teal
  Material 3 accent — a real, screenshotted visual confirmation, not just a stored boolean.
- **Widget regression check.** With two widgets already placed (from prior sessions), cycling
  through Light, Dark, and Dynamic-Color-off/on in Settings left both widgets' own dark, translucent
  styling and blue accent completely unchanged on the home screen — confirming D-069's "app UI only"
  policy holds on a real device, not just in code.
- **Notification status, both directions, no restart.** Opened "Manage notifications," disabled
  "All CountFlow notifications" in Android's real settings, pressed back to CountFlow: the row
  updated to "Not allowed" without any restart. Re-enabled it the same way: the row updated back to
  "Allowed." Confirms `LifecycleResumeEffect`'s resume-refresh actually fires on the real navigation
  path a user would take.
- **About.** Version row showed the corrected `"0.4.9 (14)"`; Privacy Policy and Open-source
  licenses rendered visibly dimmed; tapping the disabled Privacy Policy row produced no crash and no
  navigation (confirmed via `pidof` staying alive and a static `uiautomator` dump showing
  `clickable="false"`).
- **200% font scale.** Every Settings row reflowed onto multiple lines without clipping or overlap;
  all text remained readable.

**Documentation**

`DECISIONS.md` D-069 (app-UI-only theming, widget regression policy), D-070 (notification-status
API choice and resume-refresh), D-071 (no Premium entry point), D-072 (PackageManager version read,
the versionCode/versionName fix), D-073 (honest disabled placeholders for Privacy Policy/licenses).
`PROJECT_STATUS.md`, `ROADMAP.md` (Milestone 6 marked Completed), `TODO.md` (P0 rewritten for
Session 15, P2 "Milestone 6: settings" section rewritten as "Post-MVP settings scope"),
`KNOWN_ISSUES.md` (BUG-R015 resolved entry), `CHANGELOG.md` (new `[0.4.9]` entry), `AI_CONTEXT.md`
all updated per the standing working agreement.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 340 tests, 0 failures (up from 334).
- Lint: 0 errors, 17 warnings, unchanged since Session 9 (two new warnings introduced mid-session —
  `ObsoleteSdkInt` on a dead `Build.VERSION.SDK_INT` branch, `UseKtx` on `Uri.parse` — were fixed
  before the final gate run, not accepted).
- `:core:domain` coverage 97.0%, gated at 95%, unchanged (this session touched no domain code).

----------------------------------

## Files Created

```
feature/settings/src/main/kotlin/…/SettingsUiState.kt                       (new)
feature/settings/src/main/kotlin/…/SettingsViewModel.kt                     (new)
feature/settings/src/main/kotlin/…/notification/NotificationStatusProvider.kt          (new)
feature/settings/src/main/kotlin/…/notification/AndroidNotificationStatusProvider.kt   (new)
feature/settings/src/main/kotlin/…/about/AppVersionProvider.kt              (new)
feature/settings/src/main/kotlin/…/about/AndroidAppVersionProvider.kt       (new)
feature/settings/src/main/kotlin/…/about/AboutUiState.kt                    (new)
feature/settings/src/main/kotlin/…/about/AboutViewModel.kt                  (new)
feature/settings/src/main/kotlin/…/di/SettingsModule.kt                     (new)
feature/settings/src/test/kotlin/…/testing/FakePreferencesRepository.kt     (new)
feature/settings/src/test/kotlin/…/SettingsViewModelTest.kt                 (new, 5 tests)
feature/settings/src/test/kotlin/…/about/AboutViewModelTest.kt              (new, 1 test)
```

----------------------------------

## Files Modified

```
build-logic/convention/…/AndroidApplicationConventionPlugin.kt   (versionCode/versionName, BUG-R015)
app/…/MainActivity.kt                                             (+PreferencesRepository, CountFlowTheme wiring)
app/…/navigation/CountFlowNavHost.kt                               (settingsSection signature, no Premium link)
feature/settings/build.gradle.kts                                  (+androidx.core.ktx)
feature/settings/…/SettingsScreen.kt                                (full placeholder → real screen)
feature/settings/…/about/AboutScreen.kt                             (full placeholder → real screen)
feature/settings/…/navigation/SettingsNavigation.kt                 (onNavigateToPremium removed, D-071)
AI_CONTEXT.md, CHANGELOG.md, DECISIONS.md, KNOWN_ISSUES.md, PROJECT_STATUS.md, ROADMAP.md, TODO.md
```

----------------------------------

## Architecture Decisions

Five new entries, D-069 through D-073, detailed in `DECISIONS.md`:

- **D-069** — Global Appearance (Theme, Dynamic Color) controls the app's own Compose UI only;
  placed widgets keep their independent theme/style/accent rules, confirmed by a real-device
  regression check. `WidgetConfigurationActivity`'s own chrome is a narrow, accepted exception.
- **D-070** — Notification status uses `areNotificationsEnabled()`, not a raw `POST_NOTIFICATIONS`
  check, for correctness across every Android version; refreshed on every screen resume via
  `LifecycleResumeEffect`, not just construction.
- **D-071** — Settings does not surface a Premium/Upgrade entry point this session; the placeholder
  link was removed, `:feature:premium`'s route stays registered for Milestone 9.
- **D-072** — App version is read from the installed `PackageManager`, not `BuildConfig`; the
  convention plugin's stale `versionCode`/`versionName` (frozen since Milestone 1) were corrected.
  The BUG-R015 fix.
- **D-073** — Privacy Policy and Open-source licenses ship as visible, honestly-disabled
  placeholders, never a fake URL or a new dependency pulled in "merely" to display licenses.

----------------------------------

## Current Project Structure

No new modules, no new internal dependency edges. `:feature:settings` was already a real module
with placeholder screens (not an empty scaffold like `:core:analytics`/`:core:billing`) and already
depended on everything needed (`:core:domain` for `PreferencesRepository`, Hilt via the feature
convention plugin). One new external dependency edge:
`feature/settings/build.gradle.kts` now applies `androidx.core.ktx` explicitly, for
`NotificationManagerCompat` and `PackageManager`.

----------------------------------

## Dependencies Added

`androidx.core.ktx` — already in `gradle/libs.versions.toml`, newly applied to
`feature/settings/build.gradle.kts`. No new external libraries introduced to the version catalog.

----------------------------------

## Current Features Working

Everything from Session 13, plus: a real Settings screen lets the user choose System/Light/Dark
theme and toggle Material You dynamic color, both applying instantly and persisting across a full
process kill, with placed widgets completely unaffected. The user can see at a glance whether
CountFlow's notifications will actually reach them, right down to Android's own per-app setting, and
jump straight to Android's notification settings to change it — the status stays correct even after
leaving and returning without restarting CountFlow. About shows the app's real, accurate version,
with Privacy Policy and Open-source licenses honestly marked as not yet available rather than faked.

----------------------------------

## Pending Work

**P0 — blocks Session 15**
1. **Approve Final MVP Release Audit, Billing/Live Updates, or further Milestone 5/8 work**, now
   that essential settings are delivered and real-device verified.
2. **Get a real, final privacy-policy URL** — a genuine release blocker (D-073), not an engineering
   task.
3. **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — carried over
   unchanged since Session 10; not attempted this session either.

**P2 — deferred settings scope:** backup/restore, accounts/cloud sync, the full localisation pass
(TD-007, now covering Settings/About's own strings too), and a real open-source-license enumeration
mechanism — all explicitly out of this session's MVP scope, not forgotten.

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** BUG-R015 (`versionCode`/`versionName` frozen at `1`/`"0.1.0"` since
Milestone 1, invisible until this session's About screen read it back — found and fixed the same
session, D-072).

**Confirmed unchanged this session:** BUG-011 (Force Stop recovery) — unrelated to this session's
work, no scheduler changes were made.

**Open, unchanged:** TD-001, TD-005, TD-006, TD-009, TD-016, TD-017, TD-018. TD-007 (localisation)
now also covers Settings/About's new strings, still open — Milestone 6 shipped without this pass by
explicit scope decision. TD-002 unaffected — `:feature:settings` was never an empty scaffold.
LIM-003, LIM-004, LIM-005, LIM-006.

**Lint:** 0 errors, 17 accepted warnings, unchanged since Session 9 (two new warnings introduced and
fixed within this session, not carried forward — see "Completed" above).

----------------------------------

## Next Session Plan

1. Get explicit approval before starting Final MVP Release Audit, Billing/Live Updates, or the
   remaining Milestone 5 `WIDE` measurement — the natural next steps now that Core Product,
   background refresh, basic reminders, and essential settings are all delivered.
2. If Final MVP Release Audit is approved: this is the natural point to also resolve the real
   privacy-policy URL (`TODO.md` P0) before or during that audit, since it blocks a real release
   regardless of which milestone comes next.
3. If a real (ideally physical) device is available and Milestone 5 is prioritized instead: the
   real 4×2 (`WIDE`) placement and screenshot Session 10 could not complete.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents per the standing working agreement.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 340 tests, 0 failures (up from 334)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings, unchanged since Session 9
- Runtime: the same stable local emulator established in Session 8 (`Pixel_9`), used for the full
  theme/dynamic-color/persistence/notification-status/widget-regression/font-scale verification
  sweep. `adb shell uiautomator dump` was used throughout for exact tap coordinates, avoiding the
  screenshot-scaling mistakes earlier sessions made before adopting this technique.

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**340 written, 340 passing, 0 failing — up from 334.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 132 | Unchanged |
| `:core:common` | 4 | Unchanged |
| `:core:data` | 32 | Unchanged |
| `:core:database` | 41 | Unchanged |
| `:core:notifications` | 10 | Unchanged |
| `:feature:events` | 36 | Unchanged |
| `:feature:settings` | 6 | +6 (`SettingsViewModelTest.kt` +5, `AboutViewModelTest.kt` +1 — this
                             module's first-ever test source set) |
| `:widget:engine` | 50 | Unchanged |
| `:widget:glance` | 29 | Unchanged |

**Coverage** — `:core:domain` 97.0% lines, unchanged (this session added no domain code; every new
class lives in `:feature:settings`). `:feature:settings`'s new logic is fully exercised by plain
Kotlin fakes (`FakePreferencesRepository`, lambda-based `NotificationStatusProvider`/
`AppVersionProvider`) rather than Robolectric — consistent with this project's standing practice of
keeping decision logic behind small interfaces a fake can implement, reserving real-device
verification for the thin Android platform wrappers (`AndroidNotificationStatusProvider`,
`AndroidAppVersionProvider`) that have no branching logic of their own to unit-test.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: modified production files across `:app`, `build-logic`, `:feature:settings`;
new test files (`:feature:settings`'s first test source set); 7 modified documentation files; no new
documentation file this session, building on `main` at the Session 13 commit (`3cf6f93`). No remote
configured.

----------------------------------

## Developer Notes

- **A value with no reader is invisible for exactly as long as nothing reads it — a build value,
  not just a runtime data field.** `versionCode`/`versionName` were set once in Session 2 and never
  touched again despite thirteen real `CHANGELOG.md` releases since; nothing ever disagreed with
  `1`/`"0.1.0"` because nothing ever read them back (BUG-R015). The same shape as BUG-R006/BUG-R007
  (Session 6, a render-model field with no consumer) one level up the stack — worth auditing any
  other value set once at project setup and never revisited before trusting it is still correct.
- **The right API for "is this permission-gated feature actually working" is sometimes not the
  permission check itself.** `NotificationManagerCompat.areNotificationsEnabled()` answers "will a
  notification be seen" correctly on every Android version; `checkSelfPermission(POST_NOTIFICATIONS)`
  only answers "does this specific API-33+ runtime prompt exist," which is `GRANTED` unconditionally
  below API 33 regardless of the user's actual, real per-app notification setting. Worth checking
  for other runtime-permission-adjacent status displays whether the version-uniform "is the feature
  actually working" API exists before reaching for the permission check that gates a specific call.
- **A UI value that can change entirely outside the app needs an explicit resume-time refresh, not
  just an initial read.** Notification status is the second case this project has hit where "the
  user leaves this exact screen, changes something in Android's own settings, and comes straight
  back" is the *only* realistic way the value changes — `LifecycleResumeEffect` on the Composable,
  not a `Flow` (Android exposes no such stream for this value), is what makes that path actually
  work rather than silently going stale until the next cold start.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.
  Useful this session specifically: `adb shell uiautomator dump` (used first, not as a fallback,
  for every tap this session — no screenshot-scaling mistakes this time), `adb shell settings put
  system font_scale <n>`, `adb shell am force-stop <package>` (deliberately, for a persistence test
  that needed a genuine process kill — distinct from `am kill`'s use in Sessions 12–13 for
  "process reclaimed while backgrounded" tests, and correctly not confused with it this session).

----------------------------------

## Requires approval before Session 15

1. **Final MVP Release Audit, Billing/Live Updates, or further Milestone 5 work** — essential
   settings are now delivered and real-device verified, alongside every other MVP-scoped feature
   (Event CRUD, responsive widgets, widget customization, background refresh, basic reminders). The
   natural next step is a Final MVP Release Audit before any Play Store submission, or Billing/Live
   Updates if the product direction calls for them first, or finishing Milestone 5's remaining
   widget-sizing loose ends (real `WIDE` confirmation). Do not begin any of these until approved.

----------------------------------

## Estimated Progress

```
Overall Progress            65%

Research & Architecture    100%
Project Setup               100%
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
Settings                     90%   (Session 14: appearance, notification status, About delivered
                                     and device-verified; backup/restore and accounts explicitly
                                     out of MVP scope)
Billing                       0%
Testing                      80%   (domain, DAO, repository, ViewModel, widget engine, Glance UI,
                                     notification coordinator, settings)
Play Store                    0%
```
