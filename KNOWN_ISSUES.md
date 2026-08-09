# CountFlow — Known Issues, Technical Debt, and Limitations

Living document. Add entries as they are discovered; mark them resolved rather than deleting,
so the history of what bit us stays readable.

**Legend:** `BUG-nnn` defects · `TD-nnn` technical debt · `LIM-nnn` platform limitations we
must design around · `WARN-nnn` accepted warnings

---

## Open bugs

### BUG-011 — Widget stays stuck on a loading spinner after Force Stop until the app reopens
**Severity:** High · **Opened:** Session 8 · **Partially addressed Session 9 — see below**

Confirmed directly on a real device: `adb shell am force-stop com.countflow`, then the widget
shows `@layout/glance_default_loading_layout` — a generic spinner — and never clears on its own.
Nothing currently running on the device retriggers a redraw; the widget stays stuck until the
user reopens the app by any means (tapping it, tapping the widget's own "unconfigured" tap target,
etc.), at which point `GlanceWidgetRefreshScheduler`'s startup subscription immediately fixes it.

**Session 9 update — investigated per the Milestone 5A brief, deliberately not closed.** The brief
asked whether the generic spinner could be replaced with something branded and whether the
underlying gap could be fixed outright. Only the first is done:
`res/layout/widget_initial_layout.xml` (a plain Android XML layout, "CountFlow" / "Tap to
refresh") now replaces Glance's generic spinner as `android:initialLayout`, which is shown both
before the first `provideGlance` completes and — confirmed by this session's own reasoning, not
re-tested empirically — whatever a widget falls back to after Force Stop with no trigger to redraw
it. This changes what the stuck state communicates; it does not make the widget self-heal. Android
cancels an app's scheduled work on Force Stop by design, and defeating that was explicitly out of
scope ("do not attempt to defeat Android's force-stop semantics"). Left open, severity unchanged —
see `docs/WIDGET_DESIGN_GUIDE.md`'s "Force Stop / BUG-011" section for the full reasoning.

**Scoped precisely.** This was tested against Force Stop specifically — Android documents this as
more aggressive than an ordinary background process reclaim, since it also cancels the app's
scheduled work. This device's Play-Store system image could not be rooted (`adb root` refused:
"cannot run as root in production builds"), so an ordinary low-memory-style kill could not be
tested for comparison; RemoteViews are documented to be cached by the host independent of the
app's process for that gentler case, so this may be Force-Stop-specific rather than a general
process-death problem. Force Stop is nonetheless a real, common, user-reachable action (Settings →
Apps → Force Stop; some battery/storage-management flows trigger it too).

**Why not fixed this session.** No mechanism currently exists to self-heal without either a new
background trigger (which belongs with the Milestone 8 refresh-strategy work, not a same-session
polish fix) or a "tap to retry" affordance layered onto the existing render states (a real UI
addition, not the kind of small fix this session's brief scoped to allow).

**Resolution path.** Revisit once Milestone 8's alarm-based scheduler exists — a periodic or
event-driven trigger would also naturally recover from this. Alternatively, a lightweight "tap to
refresh" hint in the loading state would be a self-contained Milestone 5-scale fix if this proves
important enough to prioritize sooner.

Two other defects were found and fixed this session (BUG-R009, BUG-R010), and one prior session's
finding (TD-013) turned out to be incorrect on real device evidence — see Resolved, below.

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

### TD-013 — CORRECTED Session 8, was never a real gap

Session 7 concluded, from reading Glance 1.1.1's `Text` API surface (`javap` on the AAR showed
only text, modifier, style, and `maxLines` — no overflow parameter), that a long widget title
would clip mid-character with no ellipsis. Session 8's first real device render disproved this
directly: a 61-character title rendered as "A Genuinely Very Lon…" with a genuine ellipsis,
screenshotted in `docs/SCREENSHOT_GUIDE.md`. The Kotlin API surface reading was accurate as far as
it went — there is still no explicit overflow parameter to set — but the underlying `RemoteViews`
`TextView` apparently applies ellipsis by default regardless, something no amount of API-surface
inspection could have caught. Left here, marked corrected rather than deleted, specifically as a
reminder that reading a library's public API is not a substitute for one real render — see
`AI_CONTEXT.md`'s defects list.

---

### TD-016 — `WidgetSizeClass` thresholds are calibrated against one emulator's one launcher, not portable
**Severity:** Medium · **Opened:** Session 10

`COMPACT_MAX_HEIGHT_DP` (164f) and `WIDE_MIN_WIDTH_DP` (300f) are correct for this session's Pixel
Launcher on this session's `Pixel_9` AVD, confirmed by real on-device measurement (D-055) after the
original formula-derived thresholds turned out to be wrong. Nothing about those measurements
generalizes: a different launcher, a different screen density, or a different grid row/column
count could report meaningfully different real dp values for the same 2×1/2×2/4×2 cell counts,
the same way this session's real numbers already disagreed by roughly 2× with Android's own
documented cell-size formula.

**Why not fixed.** No public, launcher-agnostic Android API reports "how many dp is one grid row
on this specific host" — `SizeMode.Exact` plus an app-owned threshold (D-053) is the mechanism
available at all, and getting it right requires real measurement on whatever device/launcher
combination is being verified, which this session did for exactly one.

**Resolution path.** Re-measure on a physical device and, ideally, a second launcher (Samsung One
UI or another OEM skin) before treating these thresholds as broadly correct rather than
"correct for the one environment this project has ever had stable device access to." If a future
session finds different real numbers, recalibrate the same way this session did — a real
measurement replacing a not-directly-verified one — rather than widening the thresholds
speculatively to cover an untested range.

---

### TD-017 — 4×2 (`WIDE`) has no real-device visual confirmation
**Severity:** Medium · **Opened:** Session 10

Every `WIDE` layout (`CountdownWidgetLayouts.kt`'s seven `<Style>LayoutWide` composables) is
confirmed correct by Robolectric (renders without duplicating or losing content, across all seven
styles) but has never been seen rendering on an actual launcher. Three genuine attempts to get a
real device into a wide-resized state this session did not succeed — two placed widgets side by
side left no free grid column for either to grow into, and freeing space by removing one first did
not complete via device automation either (`docs/RESPONSIVE_WIDGET_REVIEW.md` has the full
account, including the specific `adb` techniques tried).

**Why not fixed.** Getting a real `WIDE` placement needs either more automation-technique iteration
than this session's time budget allowed, a physical device where manual interaction is trivial
where `adb`-scripted interaction was not, or a fresh home screen layout with more free grid space
than this session's two-widgets-side-by-side test setup left.

**Resolution path.** Retry on a physical device, or clear the home screen to a single widget
before attempting the resize, so a free column reliably exists. `WIDE_MIN_WIDTH_DP` (TD-016) should
be re-confirmed at the same time, since both gaps share the same root cause (no real `WIDE`
measurement yet).

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

### LIM-001 — Glance has no determinate circular progress — WORKED AROUND Session 9
**Verified in AndroidX source, Session 1 · Worked around, Session 9 · Made size-responsive, Session 10**

`androidx.glance.appwidget.CircularProgressIndicator` takes only `(modifier, color)` and is
indeterminate. Only `LinearProgressIndicator` has a `progress: Float` overload — still true, and
still will be for as long as this project depends on Glance 1.1.1; this remains a real library
limitation, not something a future Glance version is guaranteed to fix.

**How it's worked around.** `CircularProgressRenderer` (`widget/glance/…/progress/`) draws a
determinate ring to a `Bitmap` with `Canvas`, supplied via `ImageProvider`, sized against
`LocalSize`, capped by the widget bitmap budget (LIM-003), and quantized to whole percent so the
bitmap regenerates at most 100 times over an event's life — exactly the approach this entry
originally anticipated. Session 10 extended it across all three size classes: the ring appears at
`STANDARD` and `WIDE` (sized differently for each — `docs/WIDGET_SIZE_MATRIX.md`) and is
deliberately absent at `COMPACT`, where no diameter above the ring's own legibility floor
(`MIN_RING_DP`) fits (D-054). Confirmed rendering correctly on a real device both sessions
(`docs/WIDGET_DESIGN_REVIEW.md`, `docs/RESPONSIVE_WIDGET_REVIEW.md`).

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

Lint currently reports **0 errors and 17 warnings** on `:app:lintDebug` with
`abortOnError = true` and `checkDependencies = true` (10 pre-existing, corrected to their actual
per-category counts below; 7 new this session). All seventeen are expected:

| Warning | Count | Why it stands |
|---|---|---|
| `AndroidGradlePluginVersion` | 4 | A newer AGP exists. Upgrading is gated on TD-001. |
| `NewerVersionAvailable` | 4 | Newer library versions exist. Version bumps are a deliberate, verified activity, not a default. |
| `OldTargetApi` | 1 | targetSdk 36 with compileSdk 37 is deliberate (D-012). Play requires 36 from 31 Aug 2026; 37 is not required. |
| `ObsoleteSdkInt` | 1 | The `-v26` mipmap qualifier is redundant at minSdk 31 but is the convention every Android tool generates and expects. Removing it triggered TD-004. |
| `HardcodedText` | 7 · **new Session 9** | All seven are in `res/layout/widget_initial_layout.xml` (2) and `res/layout/widget_preview.xml` (5) — the two plain Android XML layouts added this session for `android:initialLayout` and `android:previewLayout`. Both are inherently static, non-localized display surfaces: the preview layout shows fixed example content ("Trip to Kyoto") the launcher renders before any real data exists, and the initial layout is a brief loading-state placeholder. Neither reads a user-facing string that would otherwise go through the app's normal string-resource path (`TD-007` tracks that path; these two files are outside it by nature, not by oversight). Extracting them to string resources would not add real localizability, since the content itself is a fixed mockup, not user data. |

Two warnings introduced earlier this session were fixed rather than accepted: `UseKtx`
(`CircularProgressRenderer.kt`, `Bitmap.createBitmap(...)` → the `createBitmap(...)` KTX
extension) and `LocalContextResourcesRead` ×2 (`WidgetPreviewCard.kt`,
`LocalContext.current.resources` → `LocalResources.current`, so the preview recomposes correctly
if the device configuration changes while the screen is open).

---

## Resolved

### BUG-R012 — A widget silently drew the wrong size's layout instead of the size it was actually resized to *(found and fixed Session 10)*

`WidgetSizeClass.kt`'s original `COMPACT_MAX_HEIGHT_DP` (75f) was derived from Android's `dp =
70×cells − 30` cell-size formula rather than a real measurement — the same shape of mistake as
BUG-R009 (Session 8). A real widget resized down to a genuine, launcher-confirmed 2×1 (172×104dp,
measured directly) stayed classified as `STANDARD` (the threshold expected anything below 75dp to
be compact; the real 2×1 height was 104dp), so it kept drawing the full `MaterialLayout` — identity
row, headline row, and a progress bar — simply compressed into a now-too-short card, rather than
the dedicated single-row `MaterialLayoutCompact`. No crash, no visible error — the card just quietly
rendered the wrong composition for its actual size.

Found by cross-checking two independent size readings against each other: the configuration
screen's `AppWidgetManager`-based size caption correctly said "(2×1)" while the real Glance render
kept showing standard-shaped content, a discrepancy that had no other explanation once both
readings were confirmed individually correct. Fixed by recalibrating both thresholds against real
on-device measurements instead of the formula (D-055) — 164dp for `COMPACT_MAX_HEIGHT_DP`, the
midpoint of the real 104dp and 224dp height measurements. A second, related bug was found in the
same investigation and fixed alongside it: `StartIdentity`'s internal `fillMaxWidth()` had been
correct at every existing call site (each one a sole child of a `Column`) but crowded out its
sibling `Text` nodes once reused inside `MaterialLayoutCompact`'s `Row` — given a `modifier`
parameter, defaulting to the prior `fillMaxWidth()` behavior everywhere except the one `Row`
context that needed `defaultWeight()` instead. Both fixes verified on the same real, previously
misrendering widget — before/after screenshots in `docs/RESPONSIVE_WIDGET_REVIEW.md`.

### TD-012 — `resizeMode="none"` is not guaranteed to be honored by every launcher *(resolved Session 10)*

Open since Session 7. The premise no longer applies: `resizeMode` is now `"horizontal|vertical"`
(D-056), and `sizeMode` is `SizeMode.Exact` (D-053), so a launcher resizing the widget — whether
via the standard resize-handle gesture or, hypothetically, an OEM launcher's own resize behavior —
is exactly the case this session built the whole responsive system to handle correctly, not a gap
with no adaptive fallback. Confirmed directly: a real resize on this session's Pixel Launcher
produced a real, correctly-classified `WidgetSizeClass.COMPACT` render (after BUG-R012's fix).
Whether every OEM launcher's resize behavior matches this one launcher's is still unverified — see
the new TD-016 for that narrower, honestly-scoped remaining question.

### BUG-R011 — A word-shaped headline ("Completed", "Expired") wrapped mid-word instead of ellipsizing *(found and fixed Session 9)*

Introduced and fixed within the same session, not carried over. This session's headline type
scales (`MINIMAL_HEADLINE_SIZE = 46.sp`, etc.) were tuned for a 1–3 digit day count; the first
on-device screenshot of a completed event showed "Completed" wrapped mid-word into "Compl" /
"eted" — Glance has no autosizing text (`LIM-004`), so a size tuned for digits does not shrink for
a longer word automatically.

Found by looking at a real device screenshot, the same way Session 8's BUG-R009/R010 were found —
not by code review, which had no reason to flag a hardcoded `sp` constant as wrong. Fixed by adding
`WidgetHeadline.isNumeric` and a `headlineSize()` selector in every one of the seven layouts,
picking a smaller size for word-shaped headlines, plus `maxLines = 1` at every headline call site
so anything still too long ellipsizes cleanly instead of wrapping — confirmed to actually ellipsize
on-device (`widget_completed_fixed.png` / `widget_expired_fixed.png` in the Session 9 working
scratchpad; curated versions in `docs/screenshots/after_completed.png` and `after_expired.png`),
consistent with TD-013's finding that Glance's underlying `RemoteViews` `TextView` ellipsizes by
default. Regression-tested for the testable half of the fix (`isNumeric`'s classification logic);
the visual wrapping itself is not observable through Glance's Robolectric-based testing API, the
same previously-documented gap BUG-R010 hit.

### TD-011 — Widget corner radii were hand-picked constants, not tied to the system's actual clip radius *(resolved Session 9)*

Open since Session 7. `WidgetTheme.cornerRadiusDp` is now nullable: `null` means "track
`android.R.dimen.system_app_widget_background_radius`" (resolved via Glance's `cornerRadius(Int
resId)` overload, confirmed to accept a resource id directly), applied in
`CountdownWidgetContent.widgetCornerRadius()`. Four styles (Minimal, Material, Progress, OLED) now
track the system value, each for a style-specific reason ("no opinion about shape," documented
inline in `WidgetThemeResolver`); three (Glass 20dp, Rounded 28dp, Modern 8dp) intentionally
override it, each because the override *is* part of that style's design, not an arbitrary
leftover constant. See `DECISIONS.md` D-045 and `docs/WIDGET_DESIGN_GUIDE.md`'s corner-radius
section for the full per-style reasoning.

### TD-014 — No preview image in the widget picker *(resolved Session 9)*

Open since Session 8, and the brief for this session called closing it "mandatory."
`res/layout/widget_preview.xml` (a plain Android XML layout, not Glance) over
`res/drawable/widget_preview_background.xml` now approximates the Material style with realistic
content, wired via `countdown_widget_info.xml`'s `android:previewLayout` (API 31+) — the correct
mechanism, since Glance cannot render a live composition into the picker itself. Confirmed on a
real device: expanding CountFlow's entry in the Pixel Launcher widget tray now shows a styled
"✈️ Trip to Kyoto / 7 days / Next week" card instead of a blank icon. See
`docs/screenshots/after_widget_picker.png` and `docs/WIDGET_DESIGN_REVIEW.md`.

### TD-015 — Significant unused vertical space in every widget state *(resolved Session 9)*

Open since Session 8. Addressed as part of the same per-style redesign that closed the "styles
look identical" finding, not as a separate pass — the two were always going to be cheaper solved
together (`TODO.md` said so explicitly when this was opened). Every style now either uses
significantly larger type (Minimal 46sp, OLED 50sp, versus the ~32sp shared scale before), adds a
genuinely space-filling element (Progress's circular ring, Modern's dense multi-line stack), or
both. No longer treated as open technical debt; if a future session finds a specific style's
spacing wanting, it should be filed as a new, narrower finding rather than reopening this one.

### TD-010 — No widget had been placed through a real launcher flow *(resolved Session 8)*

Open since Session 5. Session 8 had the first stable, self-controlled device in this project's
history — a locally-launched emulator (AVD `Pixel_9`, Android 16, real Pixel Launcher), not a
remote or pooled one. Every remaining piece was verified directly: the widget listed correctly in
the real system widget picker; drag-to-place worked and produced a rendered, then configured,
widget on a real home screen; the widget updated live across eleven rebind cycles; it survived an
app reinstall (update) and a **full device reboot** with no manual intervention (Android's
platform-default post-boot widget refresh fired correctly, confirming the reasoning in
`docs/WIDGET_ARCHITECTURE.md` §10 without a custom `ACTION_BOOT_COMPLETED` receiver); reconfiguring
to a different event worked through the real system configuration flow; and removing a placed
widget's binding was confirmed via `WidgetLifecycleCoordinatorTest` (the literal drag-to-remove
launcher gesture could not be scripted via `adb input` — a tooling limitation, not new evidence
either way). Screenshots for every state are in `docs/SCREENSHOT_GUIDE.md`. This same device access
is what surfaced BUG-R009 below — the widget had never actually been the 2×2 every prior session
believed it was.

**What remains genuinely unverified**, now scoped much more narrowly: the exact system
`ACTION_APPWIDGET_DELETED` broadcast reaching `onDeleted` (still unit-test-only), and rendering on
any launcher other than stock Pixel Launcher (Samsung One UI, other OEMs — never attempted, no
access to one).

### BUG-R009 — The widget occupied a 3×2 footprint, not the 2×2 every session had assumed *(found and fixed Session 8)*

`countdown_widget_info.xml` declared `minWidth="180dp"` alongside `targetCellWidth="2"`. Android's
documented cell-size formula (`dp = 70×cells − 30`) makes `180dp` the *3-cell* value, not 2-cell
(`110dp`) — a real launcher's own widget picker confirmed this directly, labeling the widget
"3 × 2" before the fix. Every design decision in Sessions 6 and 7 (typography, spacing, the
vertical-whitespace finding in `docs/PRODUCT_REVIEW.md`) was made against a 2×2 assumption that had
never actually been true, and could not have been caught before this session because no earlier
session reached a real widget picker to see the size label at all. Fixed by changing `minWidth` to
`110dp`; verified empirically — the same launcher's picker relabeled the widget "2 × 2" with no
other change. See DECISIONS.md D-043.

### BUG-R010 — Completed/expired events showed a full-strength progress bar next to a muted label *(found and fixed Session 8)*

The countdown label correctly dims to a muted color once an event is completed or expired
(`CountdownWidgetContent`'s `labelColor` logic, present since Milestone 4), but the progress bar
beside it kept drawing at full accent-color strength regardless — found by looking at a real
on-device screenshot, not by inspection. Fixed by having the bar reuse `labelColor` instead of the
unconditional `accent`. See DECISIONS.md D-044.

**Testing gap.** No automated regression test — Glance 1.1.1's `glance-testing` library exposes
text/content-description/testTag matchers but nothing to assert a composable's resolved
`ColorProvider` value, so this fix is verified visually (`docs/SCREENSHOT_GUIDE.md`) rather than
by an automated test.

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
