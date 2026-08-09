# CountFlow — Known Issues, Technical Debt, and Limitations

Living document. Add entries as they are discovered; mark them resolved rather than deleting,
so the history of what bit us stays readable.

**Legend:** `BUG-nnn` defects · `TD-nnn` technical debt · `LIM-nnn` platform limitations we
must design around · `WARN-nnn` accepted warnings

---

## Open bugs

None. No runtime defects are known as of Session 7.

---

## Technical debt

### TD-001 — AGP built-in-Kotlin opt-out is removed in AGP 10
**Severity:** High (blocks a future upgrade) · **Opened:** Session 2 · **Owner decision:** D-005

`gradle.properties` sets `android.builtInKotlin=false` and `android.newDsl=false`. Both are
deprecated and are removed in AGP 10, so upgrading past AGP 9.x requires migrating to AGP's
built-in Kotlin support first.

**Why we took it on.** AGP 9 refuses to run the `org.jetbrains.kotlin.android` plugin with the
new DSL enabled. KSP, Hilt, and the Compose compiler are documented against that plugin, and
Hilt plus Room both need KSP — so the built-in-Kotlin path would have put DI and persistence on
unproven ground. Google's own `platform-samples` sets the same two flags at the same AGP version.

**Resolution path.** Before AGP 10: verify KSP and Hilt support built-in Kotlin, drop
`org.jetbrains.kotlin.android` from the convention plugins, remove both flags, re-verify the
whole build. Budget a full session.

---

### TD-002 — Empty scaffold modules
**Severity:** Low · **Opened:** Session 2

`:core:data`, `:core:database`, `:core:notifications`, `:core:analytics`, `:core:billing`,
`:core:domain`, `:widget:engine`, and `:widget:glance` contain build scripts and dependency
wiring but no source. They exist to establish boundaries early.

**Cost.** Eight modules of configuration and build time for no code yet. Each adds a small fixed
cost to every build.

**Resolution path.** They fill in on their milestone schedule (see ROADMAP.md). If build times
become painful before then, `:core:analytics`, `:core:billing`, and `:feature:premium` can
collapse into `:app` and be re-extracted later — they sit behind interfaces either way.

---

### TD-003 — No database or repository integration tests — RESOLVED Session 4
**Severity:** was Medium · **Opened:** Session 2 · **Closed:** Session 4

Closed by adding Robolectric and 52 integration tests — 32 DAO tests in `:core:database` and 20
repository tests in `:core:data`, all against real in-memory SQLite. Every behaviour listed below
as unverified is now covered. The original entry is kept for the record.

<details>
<summary>Original entry</summary>

The domain is comprehensively covered (86 tests, 99.4% line coverage on `:core:domain`) and the
entity/domain mappers have round-trip tests. What is **not** covered is anything that needs a
real SQLite database: DAO queries, the foreign-key cascades, and the repository implementations.

Those tests need either Robolectric or an instrumented test run, neither of which is set up.
Adding Robolectric was deliberately deferred rather than bolted on at the end of a long session.

**What this leaves unverified.** That the `CASE WHEN` sort arms order correctly, that the
cascade actually deletes bindings and reminders, that `IN ()` and `NOT IN ()` behave as the
repository assumes, and that the `getActiveReminders` join filters on both switches. The schema
guard test (`DatabaseSchemaTest`) covers the cascade *declaration* but not its behaviour.

**Resolution path.** Add Robolectric to the test convention plugin and write DAO tests against
an in-memory database. Best done at the start of Milestone 3, before UI code starts depending
on query behaviour nothing has exercised.

</details>

---

### TD-005 — Build output is drowned in deprecation warnings
**Severity:** Low, but it hides real warnings · **Opened:** Session 3

Every module emits two build-script deprecation warnings on every compile — one about
`Project.android(...)` being deprecated in favour of the new DSL, one about the
`org.jetbrains.kotlin.android` plugin. With fourteen modules that is ~28 lines of noise per
build, which is enough to hide a genuine warning.

Both are direct consequences of the `newDsl=false` / `builtInKotlin=false` opt-out (D-005), so
they disappear when TD-001 is resolved. Until then, filtering with
`grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"` makes build output readable.

---

### TD-006 — Title search is ASCII-case-insensitive only
**Severity:** Low · **Opened:** Session 3

The event search uses SQLite `LIKE ... COLLATE NOCASE`, whose case folding covers ASCII only.
Searching "ÉCOLE" will not match a title stored as "école".

Acceptable for now — most titles are typed as they are searched — but it will read as a bug to
users of languages with cased non-ASCII characters. The fix is either an ICU collation or a
normalised lowercase shadow column populated on write.

---

### TD-004 — Gradle build cache served stale resource output after a directory rename
**Severity:** Low, but confusing when it strikes · **Opened:** Session 2

Renaming `res/mipmap-anydpi-v26` to `res/mipmap-anydpi` produced a build that failed with
`resource mipmap/ic_launcher not found`, while the same tree built successfully with
`--no-build-cache`. The merged-resources output was being restored from a cache entry
predating the rename. A `clean` did not clear it; only disabling the build cache did.

**Current state.** Sidestepped by reverting to the conventional `-v26` qualifier. Not
investigated further, because chasing a cosmetic lint warning into a cache-correctness problem
is a poor trade.

**If it recurs.** Symptom is a resource that demonstrably exists on disk being reported as not
found. Confirm with `./gradlew <task> --no-build-cache`; if that succeeds, the cache is stale.

---

### TD-007 — Some UI strings are not localised
**Severity:** Medium · **Opened:** Session 4

Countdown labels and category names go through string resources with proper plurals. Three sets
of user-visible strings do not, and are hard-coded in Kotlin:

- The four sort names in `HomeScreen.kt` (`Date`, `Title`, `Recently added`, `Category`).
- The six validation messages in `CreateEventScreen.kt`.
- The empty-state titles and bodies, the field labels, and the all-day explanation.

Left as-is deliberately rather than half-done: moving them is mechanical, and doing it alongside
Settings in Milestone 6 means one pass over every string in the app rather than two.

**Risk if forgotten.** The app looks localised — because the parts a reviewer checks first are —
while most of its text is not.

---

### TD-008 — Archive, complete, and delete have no UI gesture
**Severity:** Low · **Opened:** Session 4

`EventsViewModel` exposes `onArchivedChange`, `onCompletedChange`, and `onDelete`, all covered by
tests, but nothing on the home screen calls them. There is no swipe action, no long-press menu,
and no overflow on the card.

Deliberate: the gesture design belongs with the widget work, since a card that previews a widget
should not sprout a swipe affordance that the widget cannot have. Scheduled for Milestone 5.

---

### TD-011 — Widget corner radii are hand-picked constants, not tied to the system's actual clip radius
**Severity:** Medium · **Opened:** Session 7

`WidgetThemeResolver`'s corner radii (16dp default, 20dp Glass, 28dp Rounded) are fixed constants
with no relationship to `android.R.dimen.system_app_widget_background_radius` — the dimension the
system actually uses to clip a widget's outer bounds, which varies by device and Android version
(notably themed from Android 12 onward). ARCHITECTURE.md D-001 explicitly flagged Google's
`appWidgetInnerCornerRadius()` utility as a "keep — adopt close to verbatim" item from the
canonical sample; it was never adopted.

**Risk.** On a device whose system clip radius differs meaningfully from these constants, the
widget's drawn background corners and the launcher's outer clip mask could visibly disagree —
either straight corners peeking past a tighter clip, or the clip visibly cutting inside a larger
drawn radius.

**Why not fixed immediately.** Adopting it needs an Android `Context.resources` read, which
belongs in `:widget:glance` (the resolver itself is intentionally pure Kotlin — D-033), plus a
product decision about which styles should track the system value and which should keep an
intentionally different one (ROUNDED's entire premise is being *more* rounded than default).
Session 7 was explicitly scoped to stabilization, not this kind of design call.

**Resolution path.** Read the system dimension in `:widget:glance` (Glance's `cornerRadius(Int)`
overload accepts a resource id directly, confirmed via the AAR) and decide per-style whether to
use it or the resolver's own constant. Good candidate for the start of Milestone 5.

---

### TD-012 — `resizeMode="none"` is not guaranteed to be honored by every launcher
**Severity:** Low · **Opened:** Session 7

Some OEM launchers are known to override widget resize hints. Because `SizeMode.Single` (not
`Exact`/`Responsive`) is in use, a launcher that resizes the widget anyway will still receive
content rendered for the single declared size — no adaptive fallback exists.

**Why not fixed.** Requires `SizeMode.Exact` with breakpoint ranges, which is explicitly Milestone
5 scope (multiple sizes). Unverifiable this session regardless, since no non-compliant launcher
was reachable — only one emulator was reachable at all, in the prior session, and not to
completion.

**Resolution path.** Addressed as a side effect of Milestone 5's size work, not separately.

---

### TD-013 — Title truncation has no ellipsis
**Severity:** Low · **Opened:** Session 7

`Event.MAX_TITLE_LENGTH` is 120 code points; the widget's title `Text` uses `maxLines = 1`, but
Glance 1.1.1's `Text` composable has no overflow/ellipsis parameter at all (confirmed via `javap`
on the `glance` 1.1.1 AAR — only text, modifier, style, and maxLines exist). A title near the
length ceiling clips mid-character with no "…", standard RemoteViews `TextView` behavior absent an
explicit `android:ellipsize` Glance provides no way to set.

**Resolution path.** No clean fix exists within Glance's plain `Text` API. Worth revisiting if a
future Glance release adds overflow support, or by dropping to `AndroidRemoteViews` interop
specifically for the title — the latter is more machinery than this single cosmetic issue
currently justifies.

---

### TD-010 — No widget has been placed through a real launcher flow
**Severity:** Medium (unchanged from Session 6 — no device was reachable this session at all) · **Opened:** Session 5 · **Progress:** Session 6

Every other piece of Milestone 4 was verified on a real emulator: the configuration Activity's
event picker, the no-orphan-bindings cancel path, the confirm path writing the correct Room row,
and the startup pruning mechanism discarding a binding not backed by a live widget. What was
**still not** verified by the end of Session 6 is a widget placed through the actual
`AppWidgetHost`/launcher flow — dragged onto a real home screen and rendering real `RemoteViews`
there.

**Session 5 finding.** `adb shell appwidget grantbind` failed on a headless (`-no-window`) test
AVD with `IllegalStateException: User -2 must be unlocked for widgets to be available`, confirmed
by process/PID to originate from the shell command binary itself, not from CountFlow.

**Session 6 progress, and why it stopped short.** A different test device this session was a
genuine GUI-mode emulator — a real launcher was visible and screenshotted (wallpaper, search bar,
app icons, navigation bar), and `adb shell dumpsys user` reported `RUNNING_UNLOCKED`, unlike
Session 5's `-2` failure. `adb shell appwidget grantbind --package com.countflow --user 0`
**succeeded** (exit 0) — a materially different, more promising signal than Session 5's outright
failure. The app installed successfully. However, the device connection was unstable throughout
(required reconnecting between nearly every command) and the device's own reported identity
changed mid-session (`model:Pixel_8` → `model:Pixel_9`), and unrelated application data was
observed on it (a "Reminders" app with auto-generated entries CountFlow never created) —
consistent with an ephemeral or pooled test device that can be reclaimed without notice, not a
dedicated stable target. The device became fully unreachable (`Connection refused`) partway
through attempting the home-screen long-press → widget picker → drag flow, before it could be
completed and before a screenshot of a placed widget could be captured.

**What remains unverified as a result.** Real `RemoteViews` rendering on an actual home screen
surface; the system's genuine `ACTION_APPWIDGET_DELETED` broadcast reaching
`CountdownGlanceWidgetReceiver.onDeleted` (a protected broadcast the shell cannot send directly,
so this was only exercised via unit test); and the widget picker correctly listing CountFlow's
provider. The underlying code path (`CountdownGlanceWidget.provideGlance`,
`WidgetRenderModelProvider`, `CountdownWidgetContent`) continues to be exercised end-to-end by
`CountdownWidgetContentTest` using Glance's own unit-test framework, which does not depend on a
real widget host — this has not changed.

**Session 7.** No device was reachable at all — the Session 6 emulator (`127.0.0.1:6555`)
returned `Connection refused` on every attempt, including after an `adb kill-server`/`start-server`
cycle, and no local `emulator` binary or AVD exists to start a replacement. No new evidence either
way this session; the Session 6 finding stands as the most recent signal (permission problem
resolved, stability problem still open).

**Resolution path.** Retry on a *stable* GUI-mode emulator or a physical device, ideally one
whose connection persists for the full session. Session 6 shows the remaining blocker is very
likely environment stability rather than the widget-bind permission problem Session 5 hit — worth
opening with a direct `appwidget grantbind` + `dumpsys user` check before attempting the full
manual placement, to confirm the device is usable before investing time in it.

---

### TD-009 — The date picker converts through UTC
**Severity:** Low · **Opened:** Session 4

Material 3's `DatePicker` speaks in UTC epoch milliseconds, so `CreateEventScreen` converts in
and out through `ZoneOffset.UTC` to preserve the calendar date the user tapped. That is correct
for the date itself — the real zone is applied when the `EventTarget` is built — but it is a
subtlety that would be easy to "fix" wrongly into the device zone, which would shift the date by
a day for users west of Greenwich.

Guarded by comment only; there is no test, because the conversion lives inside a composable.
Worth an instrumented test when Compose UI testing is set up.

---

## Platform limitations to design around

### LIM-001 — Glance has no determinate circular progress
**Verified in AndroidX source, Session 1**

`androidx.glance.appwidget.CircularProgressIndicator` takes only `(modifier, color)` and is
indeterminate. Only `LinearProgressIndicator` has a `progress: Float` overload.

**Consequence for Milestone 5.** Circular rings must be drawn to a `Bitmap` with `Canvas` and
supplied via `ImageProvider`, sized against `LocalSize`, capped by the widget bitmap budget, and
quantized to whole percent so the bitmap regenerates at most 100 times over an event's life.

---

### LIM-002 — `PeriodicWorkRequest` has a 15-minute minimum interval
**Consequence.** Minute-level countdown refresh is impossible with periodic work. Addressed by
D-008: the final 24 hours use a launcher-ticked `Chronometer` with zero app wakeups.

---

### LIM-003 — Widget bitmaps are capped at `6 × screenWidthPx × screenHeightPx` bytes
Taken from `AppWidgetServiceImpl`. **Exceeding it makes the host silently drop the widget** with
no error and no log. Every bitmap path in `:widget:engine` must budget against this.

---

### LIM-004 — Glance has no autosizing text
Google's sample works around it by measuring an offscreen `AppCompatTextView`. That utility
**inflates a real View and runs a measure pass on every call** — calling it from a composition
will blow the sub-100 ms widget update budget. It must be moved off the render path and cached
when adopted. It also uses the deprecated `displayMetrics.scaledDensity`, which is incorrect
under Android 14+ non-linear font scaling; replace with
`TypedValue.applyDimension(COMPLEX_UNIT_SP, …)`.

---

### LIM-005 — `GlanceAppWidget` cannot be injected by Hilt
`GlanceAppWidgetReceiver` is a `BroadcastReceiver`, so `@AndroidEntryPoint` works on it. But
`GlanceAppWidget` is not an Android component; inside `provideGlance` it must reach dependencies
through `EntryPointAccessors.fromApplication(...)`. Relevant from Milestone 4.

---

### LIM-006 — Emoji rendering in widgets is host-dependent
Glance `Text` renders through a `RemoteViews` `TextView` in the launcher's process, so emoji
glyph coverage and sizing vary by launcher and OEM. Needs verification on real devices during
Milestones 4 and 5, not just the emulator.

---

## Accepted warnings

Lint currently reports **0 errors and 11 warnings** on `:app:lintDebug` with
`abortOnError = true` and `checkDependencies = true`. All eleven are expected:

| Warning | Count | Why it stands |
|---|---|---|
| `OldTargetApi` | 4 | targetSdk 36 with compileSdk 37 is deliberate (D-012). Play requires 36 from 31 Aug 2026; 37 is not required. |
| `AndroidGradlePluginVersion` | 5 | A newer AGP exists. Upgrading is gated on TD-001. |
| `NewerVersionAvailable` | 1 | Newer library versions exist. Version bumps are a deliberate, verified activity, not a default. |
| `ObsoleteSdkInt` | 1 | The `-v26` mipmap qualifier is redundant at minSdk 31 but is the convention every Android tool generates and expects. Removing it triggered TD-004. |

---

## Resolved

### BUG-R008 — GLASS's translucent background could fail contrast over a light wallpaper *(found and fixed Session 7)*

`WidgetThemeResolver`'s GLASS background was a translucent overlay (`0x99101418`, 60% opaque),
composited by the launcher over whatever wallpaper sits behind the widget — the one style whose
effective background this app does not fully control, unlike OLED's fully opaque true black or
the five styles that inherit the system's own dynamic Material You surface. Over a fully
light/white wallpaper, the 60% overlay composites to roughly mid-gray, and the white text
`CountdownWidgetContent` draws on top of forced backgrounds measured at approximately 4.9:1
contrast against that worst case — barely above WCAG AA's 4.5:1 floor, with no margin.

Found during Session 7's UX review (§4 of `docs/WIDGET_REVIEW.md`), by computing the actual
composited contrast against the constant in the code rather than by observation — no device was
available this session (see TD-010). Fixed by raising the alpha to `0xCC` (80% opaque, ~10.8:1 in
the same worst case). See DECISIONS.md D-041; a regression test in `WidgetThemeResolverTest`
asserts the alpha never regresses below the floor this reasoning depends on.

### BUG-R006 — `WidgetTheme.isHighContrast` was computed but never applied *(found and fixed Session 6)*

`WidgetThemeResolver` had returned a correct `isHighContrast` value for OLED and Modern since the
theme resolver was first written, but `CountdownWidgetContent` never read the field — every color
came from the ambient `GlanceTheme` regardless of what the resolver had decided. Combined with a
related gap, forced-background themes (OLED, Glass) pulled on-colors tuned for the *dynamic*
Material You surface rather than the fixed background the theme itself forced, with no guarantee
the two agreed.

Found by re-reading the render model against the renderer while finishing this widget for
production quality — not by a failing test, since nothing asserted the field was read at all.
Fixed by resolving on-surface, muted, and progress-track colors explicitly against
`hasForcedBackground` and `isHighContrast` rather than unconditionally from `GlanceTheme`. See
DECISIONS.md D-039.

### BUG-R007 — `WidgetBinding.showPercentage` was persisted but never rendered *(found and fixed Session 6)*

The binding field controlling whether a widget's progress percentage should be shown as text has
existed since Milestone 2, flowing correctly through Room and the data-layer mappers, but
`WidgetRenderMapper` never read it and `CountdownWidgetContent` had no percent-text element to
draw at all — setting the field could never have had any visible effect. Currently inert in
practice, since no UI sets it to `true` yet (`WidgetBinding.inheriting()` defaults it to `false`),
but would have required a second investigation the day a settings screen finally did set it.

Fixed by adding `WidgetRenderModel.showPercentageText`, computed in the mapper as
`binding.showPercentage && progress.isVisible` so the renderer never has to re-derive the
conjunction. See DECISIONS.md D-040.

### BUG-R003 — Repository tests collided with two coroutine schedulers *(found and fixed Session 4)*

A `StandardTestDispatcher` created in `@Before` carries its own `TestCoroutineScheduler`, which
collides with the one `runTest` installs — "Detected use of different schedulers" — the moment
the code under test calls `withContext`. Fixed by using `Dispatchers.Unconfined` in the
repository tests, which exercise SQL rather than virtual time, and `UnconfinedTestDispatcher`
for `Dispatchers.setMain` in the ViewModel tests.

### BUG-R004 — Debounce would have delayed sort taps *(found and fixed Session 4)*

The first `EventsViewModel` debounced the whole input set, so changing the sort order while a
search was active waited 250 ms, and the search field itself lagged a keystroke behind. Fixed by
splitting the raw query and the list options into separate flows (D-031).

### BUG-R005 — Configuration crashed if the widget id could not resolve to a GlanceId *(found and fixed Session 5)*

`WidgetConfigurationActivity.onEventBound()` called `GlanceAppWidgetManager.getGlanceIdBy(appWidgetId)`
unconditionally to force an immediate redraw after a successful binding write. When the id did
not correspond to a real, host-registered widget — as happened during device testing, and could
plausibly happen in production if a widget were removed in the narrow window between the binding
write and the redraw — this threw and crashed the activity, stranding an already-successful
write instead of confirming and closing.

Found while device-testing the confirm path: the activity appeared to close cleanly, but the
database showed no binding row, and the crash log revealed why. Fixed by wrapping the redraw in
`runCatching` and moving `finish()` outside it — the write already succeeded by the time this
code runs, so `RESULT_OK` and closing must not depend on whether the immediate redraw succeeds.
A missed redraw is recovered by `GlanceWidgetRefreshScheduler`'s live observation shortly after.

### BUG-R001 — All-day events read as "starting soon" all day *(found and fixed Session 3)*

The imminent threshold was applied to every event, so an all-day event whose start instant had
passed satisfied it for the whole of its day and reported `IMMINENT` — meaning a widget would
have shown a live ticking countdown to a moment already gone.

Found by a test, not by inspection. Fixed by excluding all-day events from the imminent check
entirely (D-023), and pinned by `an all-day event is never imminent`.

### BUG-R002 — "Remaining" counted upward for an event in progress *(found and fixed Session 3)*

`remaining` was computed as the absolute distance to the target, so an all-day event twelve
hours into its day reported "12 hours" while `isPast` was false — reading as twelve hours still
to wait.

Fixed by splitting three quantities that had been conflated: `remaining` is forward-looking and
clamps to zero once the event starts, `gap` is the unsigned distance used for the breakdown and
totals, and `calendarDaysRemaining` is signed.

### TD-R001 — Missing data extraction rules *(resolved Session 2)*
Lint flagged the app as having no `android:dataExtractionRules`. Added
`res/xml/data_extraction_rules.xml` with a note that widget bindings must be excluded once they
exist in Milestone 4 — `appWidgetId` values are device-local and restoring them elsewhere would
point widgets at the wrong events.
