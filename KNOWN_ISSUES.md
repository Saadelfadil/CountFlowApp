# CountFlow — Known Issues, Technical Debt, and Limitations

Living document. Add entries as they are discovered; mark them resolved rather than deleting,
so the history of what bit us stays readable.

**Legend:** `BUG-nnn` defects · `TD-nnn` technical debt · `LIM-nnn` platform limitations we
must design around · `WARN-nnn` accepted warnings

---

## Open bugs

None. No runtime defects are known as of Session 4.

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
