# CountFlow — Architecture Proposal

**Status:** Awaiting approval. No production code written yet.
**Baseline studied:** [`android/platform-samples` → `samples/user-interface/appwidgets`](https://github.com/android/platform-samples/tree/main/samples/user-interface/appwidgets) at HEAD (sparse-cloned and read in full: 54 Kotlin files, ~8,900 LOC, plus manifest, widget-info XML, and the shared Gradle version catalog).

---

## 1. What architecture the sample actually uses

It is a **single Android *library* module** (`com.example.platform.ui.appwidgets`), not an app, and it contains two generations of code that follow different philosophies.

### Generation 1 — `glance/weather/` (2023)

The original Glance demo. `WeatherGlanceWidget : GlanceAppWidget` uses `SizeMode.Responsive` with four hardcoded `DpSize` constants and dispatches layout by matching `LocalSize.current` against them by equality. State lives in `object WeatherRepo`, a Kotlin singleton wrapping a `MutableStateFlow<WeatherInfo>`. The receiver kicks off a refresh in `onEnabled` with a bare `CoroutineScope(Dispatchers.IO).launch`. Refresh cadence is `updatePeriodMillis="1800000"` declared in XML.

This generation is instructive but **not production shaped**, and I do not propose carrying any of it forward.

### Generation 2 — `glance/layout/` (canonical layouts, 2024–2026)

This is the valuable half, and it is genuinely well designed. Every widget is split into three files with strictly enforced direction of dependency:

| File | Responsibility | Depends on |
|---|---|---|
| `layout/XLayout.kt` | Pure stateless Glance composables, plus an `XData` model, a size-breakpoint enum, an `XDimens` constants object, and `@Preview`s | nothing |
| `data/FakeXRepository.kt` | Supplies `Flow<XData?>`, one instance per widget | the data model only |
| `XAppWidget.kt` | The `GlanceAppWidget` + its `GlanceAppWidgetReceiver`; wires repository to layout | both |

The `layout/` directories are explicitly documented as copy-into-your-project components. Notable techniques the sample gets right:

- **`SizeMode.Exact` over `SizeMode.Responsive`**, with the reasoning written down in a comment: `Responsive` keeps one `RemoteViews` tree per declared size resident in the launcher's memory; `Exact` re-renders instead, trading CPU for host memory.
- **`key(LocalSize.current) { … }`** wrapping the content so a resize resets the composition subtree rather than trying to diff across geometry changes.
- **Data is loaded *before* `provideContent`**, inside `withContext(Dispatchers.Default)`, so the first emitted frame already has content and the user never sees a loading flash.
- **`ImageUtils.getMaxWidgetMemoryAllowedSizeInBytes()`** encodes the real platform limit — the widget bitmap budget is `6 × screenWidthPx × screenHeightPx` bytes, taken from `AppWidgetServiceImpl`. Exceed it and the host silently drops your widget.
- **`FontUtils.calculateFontSizeAndMaxLines()`** works around Glance having no autosizing text, by measuring an offscreen `AppCompatTextView` configured with `TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration`.
- **`appWidgetInnerCornerRadius()`** does the correct nested-radius math against `android.R.dimen.system_app_widget_background_radius`.
- **`PreviewAnnotations.kt`** defines multi-preview annotations at real widget cell sizes for small phone, large phone, and tablet, plus min/max resize bounds.
- **Manifest hygiene**: every receiver is `exported="false"`, gated on `@bool/glance_appwidget_available`, declares `initialLayout="@layout/glance_default_loading_layout"`, and listens for `LOCALE_CHANGED` so widgets re-render when the user switches language.

---

## 2, 3, 4. Reuse, replace, extend

### Keep — adopt close to verbatim

| Asset | Why |
|---|---|
| The `layout` / `data` / `widget` three-file split | The strongest idea in the repo. It is what makes widgets previewable and testable, and it is what will let CountFlow's widget layer be reused by a second widget app later. |
| `ImageUtils` memory budget math | We need it directly — our circular progress rings are bitmaps, and this is the ceiling. |
| `FontUtils.calculateFontSizeAndMaxLines` | A 2×1 widget must render both `7` and `1,203` attractively. Glance cannot autosize text; this is the only workaround. |
| `PreviewAnnotations` size set | Free design QA across the sizes that actually ship. |
| `appWidgetInnerCornerRadius()`, `Scaffold`, `TitleBar` | Correct Material 3 widget chrome. |
| Manifest hygiene, `SizeMode.Exact` + `key(LocalSize.current)`, preload-before-`provideContent` | All correct, all worth copying. |
| `onDeleted` → clean up per-widget state | Right hook, wrong storage (see below). |

### Replace — must not survive into production

| Sample approach | Problem | CountFlow approach |
|---|---|---|
| `object WeatherRepo` with in-memory `MutableStateFlow` | **This is the single most important defect.** In-memory state does not survive process death. The launcher will call `provideGlance` again after Android kills your process, and the widget renders empty. | Room as the source of truth, plus a persisted per-widget snapshot. |
| `mutableMapOf<GlanceId, Repository>` for per-widget state | Same defect — a widget's identity and configuration vanish on process death or reboot. | A `widget_binding` Room table keyed by `appWidgetId`, plus a per-widget `GlanceStateDefinition`. |
| `CoroutineScope(Dispatchers.IO).launch` inside a receiver | Unstructured, and the process can be killed the moment `onReceive` returns. | `goAsync()` for short work, WorkManager for anything longer, injected `@ApplicationScope` for app-process work. |
| `updatePeriodMillis` in widget XML | Minimum 30 minutes, not adaptive, and wakes the device on the system's schedule rather than when the display would actually change. | Removed entirely. Replaced by the scheduler in §4.2. |
| `SizeMode.Responsive` + `DpSize` equality matching (weather widget) | Brittle; breaks on any launcher whose cell math differs. | Breakpoint *ranges*, the way the canonical layouts already do it. |
| `GlanceKtx.stringResource(id, vararg args)` | Genuinely buggy — it passes the `args` array as a single vararg element to `getString`, so any format placeholder beyond the first is wrong. | Not copied. |
| Everything else | Coil, all `sample_*` drawables, both demo activities, both fake-repo families, the RemoteViews weather widget | Deleted. |

### Extend — what the sample has no answer for at all

1. **A widget configuration activity.** The sample has none, and CountFlow's central interaction is *place widget → choose which event it shows*. This needs `android:configure`, correct `EXTRA_APPWIDGET_ID` handling, `RESULT_OK` echoing the id back, a clean path for the user cancelling out (no orphaned rows), and `widgetFeatures="reconfigurable|configuration_optional"` so the widget can be re-pointed at a different event later without being deleted and re-added.

2. **A real update scheduler.** Covered in §4.2 — this is where most of the engineering value lives.

3. **Determinate circular progress.** I checked the Glance source: `androidx.glance.appwidget.CircularProgressIndicator` takes only `(modifier, color)` and is **indeterminate only**. Only `LinearProgressIndicator` has a `progress: Float` overload. The circular ring in the spec therefore has to be drawn to a `Bitmap` with `android.graphics.Canvas` and handed to `ImageProvider(bitmap)`.

4. **A theming system.** `GlanceTheme` gives you the system dynamic scheme and nothing else. Seven named widget themes × per-event accent × light/dark requires building `ColorProviders` per widget.

5. **A domain layer.** The sample has no business logic whatsoever. The countdown engine is entirely ours.

6. **Tests.** The sample has zero. `androidx.glance:glance-appwidget-testing` and its `GlanceAppWidgetUnitTest` exist and should be used.

7. **Multi-module Gradle, an Application class, navigation, ViewModels, DI, persistence.** None of it exists in the sample.

---

## 5. Limitations of the sample, stated plainly

- It is a **catalog, not an application**. There is no architecture above the widget layer to inherit.
- **All state is in-memory** and dies with the process.
- **No DI, no persistence, no domain layer, no ViewModels, no tests, no configuration activity.**
- The build file comments claim *"Recommended to use WorkManager to load data for widgets"* — but **WorkManager is never actually used anywhere in the module.** I grepped; there are no references.
- `minSdk 23` forces `@RequiresApi(O)` annotations throughout. At our `minSdk 31` all of that disappears.
- **`FontUtils` inflates a real `AppCompatTextView` on every call.** That is view inflation plus a measure pass. Called naively from a composition it will blow the sub-100 ms widget update budget. It must be moved off the render path and cached.
- `FontUtils` uses `displayMetrics.scaledDensity`, which is **deprecated** and wrong under Android 14+ non-linear font scaling. Should use `TypedValue.applyDimension(COMPLEX_UNIT_SP, …)`.
- **Version risk:** the latest *stable* Glance is **1.1.1, from October 2024**. The sample builds against `1.3.0-alpha02` (July 2026) with `compileSdk 37`. This is a real decision, not a detail — see §4.6.
- Accessibility stops at content descriptions on images. No semantics on composed text.

---

## Proposed architecture for CountFlow

### 4.0 Three corrections to the specification

I want these agreed before any code is written, because each one is expensive to retrofit.

**a. Widget style belongs on the widget, not the event.** The spec puts `Widget Style` and `Progress Style` on the event model. But the spec *also* says one event can appear as several widgets — and the whole point of that is showing the same event two different ways. Style must live on the **binding** (`appWidgetId → event`), with the event carrying only a *default* that a new widget inherits.

**b. Store the target as an instant plus a zone, not a local date-time.** `targetEpochMillis: Long` + `targetZoneId: String` + `isAllDay: Boolean`. A countdown app that stores a naive `LocalDateTime` breaks the moment the user flies somewhere or a DST boundary falls between now and the target. "New Year's Eve" is all-day and should follow the *device's* zone; "my flight at 14:05" is an instant pinned to the *departure* zone. These are different behaviours and the model has to express both.

**c. "Today" and "Tomorrow" are calendar comparisons, not arithmetic on milliseconds.** Dividing a duration by 86,400,000 gives you the wrong answer roughly half the time — 23 hours can span two calendar days, and 25 hours can span one. The engine compares `LocalDate`s in the target zone.

### 4.1 Module graph

```
:app                      Application, MainActivity, NavHost, DI aggregation, Baseline Profile

:core:common              Result, dispatcher qualifiers, Clock abstraction, logging facade
:core:domain              Pure Kotlin. Event, Countdown, CountdownEngine, use cases,
                          repository *interfaces*. No Android dependency at all.
:core:data                Repository implementations, DataStore, mappers
:core:database            Room: entities, DAOs, migrations, type converters
:core:designsystem        M3 theme, color/type/shape tokens, shared composables
:core:notifications       Reminder scheduling and channels
:core:analytics           Interface + no-op impl; Firebase impl behind a flag
:core:billing             Interface + stub impl (premium is architecture-only for now)

:feature:events           Home, create/edit, search, sort, categories
:feature:settings         Settings, backup/restore, about
:feature:premium          Paywall shell

:widget:engine            Domain-agnostic. Snapshot contract, update scheduling,
                          render-model mapping, progress-ring rendering.
:widget:glance            Glance layouts, GlanceAppWidgets, receivers, config activity
```

Two deliberate deviations from the suggested module list. I collapsed `core` and `common` into `:core:common` — two modules for the same concern is churn without benefit. And I split `widgets` into `:widget:engine` and `:widget:glance`, because you said you want this **modular for future widget apps**: `:widget:engine` knows about scheduling, snapshots, and battery policy but nothing about countdowns, so a future weather or habit widget app can take it wholesale. `:widget:glance` is the CountFlow-specific presentation.

If build times become a problem in Milestone 1, `:core:analytics`, `:core:billing`, and `:feature:premium` can start as packages inside `:app` and be extracted later — they are behind interfaces either way.

### 4.2 The refresh strategy — the most important design decision

**The schedule in the spec cannot be implemented as written, and I would not want to implement it even if it could.**

Two hard constraints. `PeriodicWorkRequest` has a **15-minute minimum interval**, so "every minute" is impossible with periodic work. And waking the app process every 60 seconds for the final 24 hours, once per widget, is precisely the battery drain the spec forbids elsewhere.

Here is what I propose instead. It is strictly better on both battery and correctness.

**Layer 1 — Zero-wakeup ticking for the final day.**

For any widget inside 24 hours of its target, do not schedule anything at all. Embed a native `Chronometer` into the Glance tree via `AndroidRemoteViews`, with `setChronometerCountDown(true)` and `base = SystemClock.elapsedRealtime() + millisRemaining`. **The launcher process ticks it. Your app is never woken.** This covers the "last day" tier at zero cost and gives second-level precision for free rather than minute-level.

Caveats I will handle: the format string is limited to `H:MM:SS` shapes, so this tier only applies where that reads naturally; and because the base is `elapsedRealtime`, it must be re-based on `ACTION_BOOT_COMPLETED`.

**Layer 2 — One coalesced alarm for the whole app, not one per widget.**

For every other tier, compute *the next instant at which any widget's displayed string would change*, take the minimum across all widgets, and set exactly one alarm for that moment. When it fires, redraw what changed and schedule the next one. This is **O(1) wakeups regardless of how many widgets exist**, where the spec's per-widget tiers are O(N).

And for day-granularity widgets — which is most of them — the displayed number only changes at **local midnight**. So the correct schedule for the ">30 days" tier is not "every 6 hours." It is *once, at 00:00 local*. That is one wakeup a day instead of four, and it is also **more accurate**, because the day count flips exactly when the date does instead of up to six hours late.

Mechanism: `AlarmManager.setAndAllowWhileIdle()`, which needs **no permission**, survives Doze, and is inexact by at most a few minutes — irrelevant, since the screen is off. It broadcasts to a manifest receiver that uses `goAsync()`, updates, and re-arms. A low-frequency (6-hour, flexed) `PeriodicWorkRequest` runs alongside purely as a **self-healing safety net** in case an alarm is ever lost to a force-stop.

**Layer 3 — Event-driven invalidation.** `ACTION_TIME_SET`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED`, `ACTION_LOCALE_CHANGED`, `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, plus a Room `Flow` in the app process so edits reflect immediately.

| Time to target | What actually changes on screen | Mechanism | App wakeups |
|---|---|---|---|
| > 48 h | the day number | one alarm at next local midnight | 1 / day |
| 24–48 h | "Tomorrow", then hours | midnight alarm + one at T−24h | ~2 total |
| < 24 h | H:MM:SS | native `Chronometer`, ticked by the launcher | **0** |
| target passed | "Completed" | one alarm at the target instant | 1 |

### 4.3 Where widget state lives

Room is the single source of truth for events. On top of that:

- A **`widget_binding` table** maps `appWidgetId → eventId + style overrides`. Persisting this in Room (not just in Glance state) means the app can show a "Widget Preview" screen listing every placed widget, and it survives reboot and backup.
- Each widget **also** persists a small immutable `CountdownSnapshot` through a custom `GlanceStateDefinition`, serialized with Kotlin Serialization.

The snapshot earns its place for two reasons. Primarily **modularity**: `:widget:glance` depends only on the snapshot contract, never on Room, which is what makes the widget layer liftable into a future app. Secondarily **resilience**: if the database is mid-migration or the process is cold, the widget paints the last known good value instead of an error state.

The cost is honest and worth stating: it is a second copy of display data, and it demands strict one-way flow — Room → snapshot → pixels, never backwards. If you would rather not carry that, the simpler alternative is reading Room directly in `provideGlance`, and I will build it that way instead.

### 4.4 Rendering the progress ring

Since Glance cannot draw a determinate circle, `:widget:engine` gets a `ProgressRingRenderer` that draws to a `Bitmap` via `Canvas`. It sizes against `LocalSize`, caps against `getMaxWidgetMemoryAllowedSizeInBytes()`, and **quantizes progress to whole percent** so the bitmap is regenerated at most 100 times over an event's entire life and can be cached on `(sizeBucket, percentBucket, colorKey)`.

### 4.5 Hilt and Glance — a known sharp edge

`GlanceAppWidgetReceiver` *is* a `BroadcastReceiver`, so `@AndroidEntryPoint` works on it. But `GlanceAppWidget` is **not** an Android component and cannot be injected — inside `provideGlance` it must reach dependencies through `EntryPointAccessors.fromApplication(context.applicationContext, …)`. Workers use `@HiltWorker` + `HiltWorkerFactory`, which requires removing the default `WorkManagerInitializer` from the manifest and implementing `Configuration.Provider` on the Application.

### 4.6 Dependency versions

Verified against Google's current version catalog and the AndroidX stable channel:

| | Version | Note |
|---|---|---|
| AGP / Kotlin | 9.2.1 / 2.4.0 | from Google's catalog at HEAD |
| compileSdk / targetSdk / minSdk | 37 / 36 / 31 | see deadline note below |
| Compose BOM | 2026.06.01 | |
| **Glance** | **1.1.1 (stable)** | **decision point — see below** |
| Room | 2.8.4 | |
| WorkManager | 2.11.2 | |
| Hilt (Dagger) / androidx.hilt | 2.60.1 / 1.4.0 | |
| DataStore | 1.2.1 | |
| Navigation Compose | 2.9.8 | |

**Time-sensitive:** from **31 August 2026** — three weeks from today — Google Play requires new apps and updates to target **API 36 or higher**. `targetSdk = 36` is therefore the floor, not a preference.

**The Glance decision.** Stable is 1.1.1 and it is nearly two years old; Google's own sample builds against `1.3.0-alpha02`. Everything CountFlow needs — `Scaffold`, `TitleBar`, `CircleIconButton`, `GlanceTheme`, `SizeMode.Exact`, `GlanceStateDefinition`, `AndroidRemoteViews` — shipped in 1.1.0. **My recommendation is to ship on 1.1.1** and keep the alpha as a second catalog entry for experimentation. An alpha dependency in a Play Store release is a support burden I would not take on for APIs we do not need.

### 4.7 Forward compatibility for lockscreen and Live Updates

The refactor-proofing that matters is a single seam: everything that decides *what to show* lives in `:widget:engine` as a pure function `CountdownSnapshot → WidgetRenderModel`. Each surface — home screen, lockscreen, and later Android 16 Live Updates — is a thin adapter over that same render model. Adding Live Updates then means writing one adapter and touching nothing in domain, data, or engine.

Concretely for lockscreen: declare `widgetCategory="home_screen|keyguard"` now (the canonical layouts already do), and if we ever want to opt out on newer platforms, that is a `not_keyguard` override in `res/xml-36/`.

### 4.8 One product note on ads and Firebase

AdMob and Firebase together add meaningful cold-start cost, and the spec sets a **sub-700 ms cold start** target. Those two goals are in tension. My recommendation: keep both behind the `:core:analytics` / `:core:billing` interfaces with **no-op implementations through Milestone 8**, wire the real SDKs in Milestone 9, then measure and defer initialization off the critical path. That also keeps every other module testable without Firebase on the classpath. And regardless of where ads land, they should stay well away from the widget creation flow — that is the premium surface.

---

## Milestone plan

Mostly as specified. One reordering, flagged.

| # | Deliverable | Notes |
|---|---|---|
| 1 | Gradle convention plugins, module graph, Hilt, navigation, M3 theme, version catalog | Convention plugins first or 13 modules become unmaintainable |
| 2 | Room + migrations, DataStore, repositories, **`CountdownEngine` + its test suite** | Engine pulled forward from M4 — everything downstream depends on it, and it is pure Kotlin so it is testable on day one |
| 3 | Event CRUD, home screen, search, sort, emoji picker |  |
| 4 | Widget engine: snapshot, render model, config activity, first Glance widget | |
| 5 | Multi-widget, all seven themes, all three sizes, progress ring renderer | |
| 6 | Settings, backup/restore | |
| 7 | Notifications and reminder scheduling | |
| 8 | The scheduler from §4.2, Baseline Profiles, macrobenchmarks, accessibility pass | |
| 9 | Firebase, AdMob, billing shell, Play Store assets | |

Each milestone ends with a written note covering why it was built that way, the architecture decisions taken, the tradeoffs accepted, and what was deliberately left for later.

---

## What I need from you before writing code

1. **Approve or reject the three spec corrections in §4.0** — especially moving widget style onto the binding.
2. **Approve the refresh strategy in §4.2**, which deliberately replaces the tier table in the spec.
3. **Snapshot layer, or read Room directly in `provideGlance`?** I recommend the snapshot; the simpler option is entirely reasonable.
4. **Glance 1.1.1 stable, or 1.3.0-alpha02?** I recommend stable.
5. **Package name** — is `com.countflow` final, or do you need a domain-style id like `com.yourcompany.countflow` for Play?
6. **Is `targetSdk 36` acceptable?** It is effectively mandatory from 31 August 2026.
