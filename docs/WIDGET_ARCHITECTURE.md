# CountFlow — Widget Architecture

**Audience:** a senior Android engineer who has never opened this codebase and needs to
understand the widget system without reading it. Every claim here names the real file and
function it describes; nothing is aspirational unless a section says so explicitly.

**Scope:** one widget type (countdown), one size (2×2), one instance shape (any number of
placements, each independently bound). This document describes what ships in Milestone 4, and is
explicit about what does not exist yet.

---

## 1. The one idea that explains the rest of this document

**A widget is a pure function of data it does not decide.** Everything upstream of
`CountdownWidgetContent` — what event, what countdown state, what colors, what layout flags —
is resolved before a single Glance composable runs. The composable's only job is turning an
already-complete `WidgetRenderModel` into pixels. If you are reading widget code and find
yourself asking "where does this widget decide X," the answer is always: it doesn't — X was
decided in `:widget:engine`, and the composable is reading the answer off a field.

This is enforced, not just followed: `:widget:engine` is a pure Kotlin/JVM Gradle module
(`countflow.jvm.library`, not an Android library), so it cannot import `android.*` even by
accident — the build fails before a reviewer would ever need to notice. `:widget:glance` is the
only module in the widget system allowed to know Android exists.

```
:widget:engine        pure Kotlin/JVM — data, rules, no Android
    ↑
:widget:glance         Android — Glance composables, Hilt, receivers, the configuration Activity
```

---

## 2. Module boundary and what lives where

### `:widget:engine`

| File | Role |
|---|---|
| `model/WidgetRenderModel.kt` | The entire contract between engine and renderer. |
| `model/WidgetTheme.kt`, `model/WidgetProgress.kt` | Two nested value types inside the render model. |
| `theme/WidgetThemeResolver.kt` | `WidgetStyle → WidgetTheme`. All seven named themes. |
| `progress/WidgetProgressEngine.kt` | `(CountdownResult, ProgressStyle) → WidgetProgress`. |
| `mapper/WidgetRenderMapper.kt` | `(Event, WidgetBinding, CountdownResult, ZoneId) → WidgetRenderModel`. |
| `provider/WidgetRenderModelProvider.kt` | Orchestrates repository + `CountdownEngine` + mapper into one `observe`/`get` API. |
| `lifecycle/WidgetLifecycleCoordinator.kt` | What happens to bindings when widgets are removed or found orphaned. |
| `refresh/WidgetRefreshScheduler.kt` | The interface the production scheduler implements (Session 12). |
| `refresh/WidgetRefreshPlanner.kt`, `refresh/WidgetRefreshCoordinator.kt`, `refresh/AlarmScheduler.kt`, `refresh/WidgetRedrawer.kt`, `refresh/RefreshOutcome.kt` | The real background-refresh system. See `docs/WIDGET_REFRESH_ARCHITECTURE.md` — its own permanent reference, not repeated here. |

Nothing in this list touches `Context`, `Bitmap`, `RemoteViews`, `GlanceId`, or any Glance type.
Every file above is tested with plain JUnit — no Robolectric, no Android runtime — which is the
practical, everyday payoff of the module boundary: these tests run in milliseconds and cannot be
broken by an Android SDK upgrade.

### `:widget:glance`

| File | Role |
|---|---|
| `CountdownGlanceWidget.kt` | The `GlanceAppWidget`. Loads a model, hands it to the renderer. |
| `CountdownWidgetContent.kt` | The renderer. A `@Composable` function, nothing else. |
| `CountdownGlanceWidgetReceiver.kt` | `GlanceAppWidgetReceiver`; delegates `onDeleted` to the lifecycle coordinator. |
| `configuration/WidgetConfigurationActivity.kt`, `WidgetConfigurationViewModel.kt`, `WidgetConfigurationUiState.kt` | Pick-an-event flow, standard Activity/ViewModel/UiState split. |
| `refresh/GlanceWidgetRefreshScheduler.kt` | The `WidgetRefreshScheduler` implementation — wires the reactive database subscription, the real `AlarmManager`, and the periodic safety net together. |
| `refresh/AndroidAlarmScheduler.kt`, `refresh/GlanceWidgetRedrawer.kt`, `refresh/WidgetRefreshReceiver.kt`, `refresh/WidgetRefreshSafetyNetWorker.kt` | The Android-specific mechanics — `AlarmManager`, `updateAll`, the one four-action `BroadcastReceiver`, the `WorkManager` backstop. See `docs/WIDGET_REFRESH_ARCHITECTURE.md`. |
| `action/WidgetActions.kt` | Click targets, as `ActionCallback`s. |
| `di/WidgetEntryPoint.kt`, `di/WidgetGlanceModule.kt` | The one Hilt bridge point Glance needs. |

---

## 3. Data flow — from a database row to a decision about what to show

```
Room (events, widget_bindings tables)
        │
        │  WidgetBindingRepository.observeBoundWidget(appWidgetId)
        │  — one query, already joined: BoundWidget(binding, event)
        ▼
WidgetRenderModelProvider
        │
        │  CountdownEngine.countdownAt(event, clock.instant(), clock.zone)
        ▼
    CountdownResult  ──────────────┐
                                    │
        WidgetBinding ─────────────┼──▶ WidgetRenderMapper.map(event, binding, countdown, zone)
                                    │            │
        Event ──────────────────────┘            │
                                                   ▼
                                          WidgetRenderModel
```

The join is the important detail: `WidgetRenderModelProvider` injects only
`WidgetBindingRepository`, not `EventRepository` as well, because
`WidgetBindingRepository.observeBoundWidget` already returns `BoundWidget(binding, event)` in one
query — built in Milestone 2 (`core/domain/…/repository/WidgetBindingRepository.kt`)
specifically anticipating this consumer. There is no N+1 here and never was one to fix.

`CountdownEngine.countdownAt(event, instant, zone)` is used instead of the clock-reading
`countdown(event)` convenience so the explicit `zone` passed to `WidgetRenderMapper.map` is
*provably* the same zone the countdown's day count was computed against — the same reasoning
`EventUiMapper.mapAll` takes `now` as a parameter for (D-030), applied here to one model instead
of a list.

`WidgetRenderMapper.map` is where `WidgetBinding.resolveWidgetStyle(event)` and
`resolveProgressStyle(event)` are called — the "override, else the event's default" precedence
rule (D-013) that lets two widgets on the same event look different. This is the only place in
the codebase that rule is exercised for widgets.

**A `WidgetRenderModel` costs about half a microsecond of CPU to produce** (measured directly:
`CountdownEngine.countdownAt` + `WidgetRenderMapper.map`, 200,000 iterations, JIT-warmed, on the
development machine — ~505ns/call). The entire non-I/O part of "what should this widget show" is
not a performance concern at any plausible widget count; the cost that matters is the Room query
and the RemoteViews round-trip through the launcher, neither of which this measurement includes.

---

## 4. Render flow — from a model to pixels

```
CountdownGlanceWidget.provideGlance(context, id)
        │
        │  1. Resolve AppWidgetId from the Glance id
        │  2. provider.get(appWidgetId)   ← BEFORE provideContent, off the composition
        │  3. provideContent { key(LocalSize.current) { Content(...) } }
        ▼
Content()
        │  val model by provider.observe(appWidgetId).collectAsState(initial = initialModel)
        ▼
CountdownWidgetContent(model)
        │
        ├─ model == null  →  UnconfiguredContent()   ("Tap to choose a countdown")
        └─ model != null  →  val sizeClass = classifyWidgetSize(LocalSize.current)
                              dispatch on (sizeClass, model.theme.style)
                              → one of 21 <Style>Layout[Compact|Wide] composables
                                (CountdownWidgetLayouts.kt, docs/WIDGET_SIZE_MATRIX.md)
```

Three details worth being deliberate about:

- **The first model is loaded before `provideContent` runs**, in a plain `suspend` call. The
  first frame the launcher ever draws already has content — there is no loading flash, and no
  window where the widget is empty because a `Flow` hasn't emitted yet. (Copied from Google's
  canonical Glance layouts for a stated reason, ARCHITECTURE.md D-001, not by habit.)
- **`key(LocalSize.current)` wraps the content, and is now load-bearing, not just future-proofing.**
  Milestone 5A left this line in place "since it costs nothing today" even though only one size
  existed yet; Milestone 5B is the reason it was there — `sizeMode = SizeMode.Exact` (D-053) means a
  real drag-resize genuinely changes `LocalSize` mid-session, and this `key` is what forces a full
  recomposition (not a diff) across that geometry change, the same reasoning Google's own canonical
  layouts use it for.
- **Size classification happens once, in the renderer, from a real measured value — not assumed.**
  `classifyWidgetSize(widthDp, heightDp)` (`WidgetSizeClass.kt`) buckets `LocalSize.current` into
  `COMPACT`/`STANDARD`/`WIDE`. Its thresholds were originally derived from Android's `dp =
  70×cells − 30` cell-size formula and were wrong by roughly 2× on a real device (BUG-R012,
  `docs/RESPONSIVE_WIDGET_REVIEW.md`) — recalibrated against real on-device measurements (D-055).
  Nothing in this render flow trusts the formula for anything beyond the manifest's own cell-count
  declaration (§9, `countdown_widget_info.xml`); the actual dp-to-size-class mapping is real,
  measured data.

### What the renderer decides versus what it is told

The renderer makes exactly one kind of decision: **how to lay out fields it was told are
visible**, never *whether* something is true. Concretely:

- `model.showTitle`, `model.showEmoji`, `model.showDaysValue`, `model.showPercentageText`,
  `model.progress.isVisible` are booleans the mapper already computed from the binding and the
  countdown. The renderer's job is `if (model.showTitle) { Text(model.title) }` — a layout
  branch, not a business decision.
- Colors are resolved once, near the top of `CountdownWidgetContent`, from
  `model.theme` — never re-derived per element. See §6 for why this resolution is more than a
  one-line `ColorProvider` wrap.
- The countdown label text itself is resolved at render time via
  `CountdownLabelFormatter.format(LocalContext.current.resources, model.label)` — the model
  carries a token (`CountdownLabel`), not a string, for the same locale-reactivity reason
  `EventCardUiModel` does in the app's own list (D-021, D-029). A background redraw the system
  triggers, outside any recomposition, still resolves the label in the locale active at that
  moment.

### Accessibility

The whole card is one clickable region carrying one `contentDescription`
(`GlanceModifier.semantics { contentDescription = … }`), built from exactly the fields that are
visible — `"Trip to Kyoto. In 12 days. 40% complete."` — rather than leaving a screen reader to
piece together the emoji, the number, and the label as unrelated fragments. This mirrors the
pattern `EventCard` already uses in the app's own list (`clearAndSetSemantics`, documented in
Session 4's developer notes) applied to Glance's own (narrower) semantics API — Glance 1.1.1
exposes only `contentDescription` and `testTag`, verified directly against the `glance` 1.1.1
AAR rather than assumed.

---

## 5. Refresh flow — how a widget finds out something changed

**Full detail lives in `docs/WIDGET_REFRESH_ARCHITECTURE.md` (Session 12) — this section is a
summary, not the source of truth.** The system described there replaces the Milestone 4 version
that used to be documented in this section (app-alive-only, no background wakeup — see that
document's own history note).

```
Event edited in the app                              System reboot / timezone / time / date change
        │  Room write                                          │
        ▼                                                       ▼
EventRepository.observeEventsWithWidgets()          WidgetRefreshReceiver (4 system broadcasts,
        │  Flow, re-emits on the joined                one manifest-registered receiver)
        │  tables changing                                      │
        ▼                                                       │
GlanceWidgetRefreshScheduler  ◄───────────────────────────────────┘
        │
        │  WidgetRefreshCoordinator.refreshAndReschedule()
        ▼
  1. redraw every placed widget from Room's current state (CountdownGlanceWidget().updateAll)
  2. CountdownEngine.nextTransitionAt(...) per bound event, coalesced to one global Instant
        (WidgetRefreshPlanner — dedupes by event, so N widgets on one event cost one computation)
  3. schedule exactly one AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...) for that instant
        (AndroidAlarmScheduler — a fixed PendingIntent request code, so this always replaces
        any previously-scheduled alarm rather than stacking a second one)
        │
        ▼
  alarm fires (WidgetRefreshReceiver, ACTION_REFRESH via an explicit PendingIntent) → back to
  WidgetRefreshCoordinator.refreshAndReschedule() → the loop repeats
```

Every real trigger — an event edited while the app is open, the alarm itself firing, a reboot, a
timezone/time/date change, or the periodic `WidgetRefreshSafetyNetWorker` backstop — funnels
through the identical `WidgetRefreshCoordinator.refreshAndReschedule()` call. That is what makes
"recalculate the schedule when relevant state changes" (the brief's own list of triggers — event
created/edited/deleted/completed, widget added/removed/reconfigured) need **zero new receivers**
for any of those triggers: `observeEventsWithWidgets()` already re-emits on every one of those
writes, exactly as it did in the Milestone 4 version of this section, and each emission now runs a
full reschedule cycle instead of only a redraw.

`GlanceWidgetRefreshScheduler` (`:widget:glance/refresh/GlanceWidgetRefreshScheduler.kt`), started
once from `CountFlowApplication.onCreate()`, still does the same `pruneOrphanedBindings()` this
section described since Milestone 4, unchanged.

Room remains the single source of truth throughout (D-002): a widget under this system is never
showing data it computed itself — every redraw reads Room fresh, and every scheduling decision is
computed from the same read. See `docs/WIDGET_REFRESH_ARCHITECTURE.md` for the next-transition
calculation itself (including a real plateau bug it was built to handle correctly, D-062),
coalescing (§4 there), the alarm mechanics (§6–7), reboot/timezone real-device evidence (§9), and
Force Stop's explicitly-unchanged behavior (§10, D-052).

---

## 6. Theme resolution — why the renderer cannot just read `GlanceTheme`

`WidgetThemeResolver.resolve(style, accentColor)` is an exhaustive `when` over all seven
`WidgetStyle` values, producing a `WidgetTheme` with four fields: a nullable accent, a nullable
background, a corner radius, and `isHighContrast`. Null accent/background means "derive it from
the dynamic Material You palette at render time" — the same null-means-dynamic convention used
throughout the domain. Two styles force a concrete background instead: OLED forces true black
(`0xFF000000`, burn-in prevention — an always-on-display concern, not an aesthetic choice a
dynamic tone could satisfy), and Glass forces a translucent dark surface (RemoteViews cannot blur
what's behind a widget, so "glass" is approximated rather than real backdrop blur).

The renderer cannot simply pair a forced background with `GlanceTheme.colors.onSurface` /
`onSurfaceVariant` / `surfaceVariant`, because those colors are tuned to pair with the *dynamic*
surface — the one that changes with wallpaper — not with a background the theme fixed itself.
Nothing guarantees the two agree; a phone with a light dynamic scheme could produce light
"on-surface" text over OLED's forced black background. `CountdownWidgetContent` resolves this
explicitly:

```kotlin
val hasForcedBackground = theme.backgroundColorArgb != null
val onSurface = if (hasForcedBackground) ForcedBackgroundPalette.onSurface else GlanceTheme.colors.onSurface
val onSurfaceMuted = when {
    hasForcedBackground -> ForcedBackgroundPalette.onSurfaceMuted
    theme.isHighContrast -> GlanceTheme.colors.onSurface   // skip the muted tone entirely
    else -> GlanceTheme.colors.onSurfaceVariant
}
```

`isHighContrast` (true for OLED and Modern) is applied the same way for themes that *keep* the
dynamic background but still ask for a stronger pass: it skips the muted `onSurfaceVariant` tone
in favor of full-emphasis `onSurface`, rather than introducing a third color set.

This closed a real gap found while finishing this milestone: `WidgetTheme.isHighContrast` had
been computed by the resolver since Milestone 4 began, but nothing downstream ever read it — the
renderer pulled every color from the ambient `GlanceTheme` regardless. See DECISIONS.md D-039.

---

## 7. Binding lifecycle

A `WidgetBinding` row (`appWidgetId → eventId + style overrides + visibility flags`) is the only
thing that connects a placed widget to the event it shows. Four transitions, and where each is
handled:

| Transition | Handled by | Effect |
|---|---|---|
| **Place** (first configuration) | `WidgetConfigurationViewModel.onEventSelected` | `WidgetBinding.inheriting(id, eventId, now)` is upserted — a fresh binding with no overrides. |
| **Reconfigure** (re-point an existing widget) | Same method, same code path | If the widget already has a binding, `onEventSelected` reuses it when the event is unchanged, otherwise writes a fresh `inheriting` binding for the new event — a reconfigure is not distinguished from a first-time bind at the data layer, only by whether a row already existed. |
| **Remove** (user deletes the widget) | `CountdownGlanceWidgetReceiver.onDeleted` → `WidgetLifecycleCoordinator.onWidgetsRemoved` | `WidgetBindingRepository.deleteBindings(ids)`. The event itself is never touched — removing a widget is not the same action as deleting the countdown it showed. |
| **Orphaned** (binding exists, widget doesn't — a missed callback, or a restored backup) | `GlanceWidgetRefreshScheduler`, once at startup → `WidgetLifecycleCoordinator.pruneOrphans` | `WidgetBindingRepository.pruneOrphanedBindings(liveIds)` discards anything not in the launcher's current id set. |

There is a fifth transition that is *not* a binding-lifecycle event but interacts with one:
**deleting the event a widget is bound to.** This is handled entirely at the database level — the
foreign key from `widget_bindings` to `events` cascades on delete (established in Milestone 2's
schema). `WidgetRenderModelProvider.observe` reads this as the bound-widget query returning null,
which `CountdownWidgetContent` renders as `UnconfiguredContent()` — the widget does not vanish
from the home screen (Android widgets never do that on their own), it reverts to the "tap to
choose a countdown" prompt, exactly as if it had never been configured.

### No-orphan-bindings guarantee

The mechanism is one line, and it is the entire guarantee:
`WidgetConfigurationActivity.onCreate` calls `setResult(RESULT_CANCELED)` **before any UI is
shown and before the ViewModel has written anything.** `WidgetConfigurationViewModel` only ever
writes a binding from inside `onEventSelected`, which only runs in direct response to the user
tapping a row. So: back out without picking anything (home button, back gesture, task switch) and
the default `RESULT_CANCELED` stands — the widget host removes the just-placed widget on its own,
and there was never a binding to clean up in the first place. `RESULT_OK` is set only from
`onEventBound()`, reached only once the ViewModel reports the write completed
(`uiState.isSaved`).

---

## 8. Configuration lifecycle

```
Launcher places a widget
        │
        │  android:configure="…WidgetConfigurationActivity"
        ▼
WidgetConfigurationActivity.onCreate
        │  setResult(RESULT_CANCELED)          ← the default; see §7
        │  read EXTRA_APPWIDGET_ID
        │  if invalid: finish() immediately
        ▼
WidgetConfigurationViewModel.load(appWidgetId)
        │  reads existing binding (if reconfiguring) + all events
        ▼
User taps an event  →  onEventSelected(eventId)
        │  upserts the binding
        │  uiState.isSaved = true
        ▼
onEventBound()
        │  setResult(RESULT_OK, id)             ← the write already succeeded; this just reports it
        │  runCatching { redraw immediately via GlanceAppWidgetManager.getGlanceIdBy + .update() }
        │  finish()                              ← unconditional, regardless of the redraw's outcome
```

The `runCatching` around the immediate redraw is deliberate and documents a real production bug
found and fixed this milestone (BUG-R005): `getGlanceIdBy(appWidgetId)` throws if the id does not
resolve to a widget the system's `AppWidgetManager` actually knows about. That can happen when
this Activity is exercised directly for testing rather than through a genuine placement, and —
more importantly — could plausibly happen in production in the narrow window between a widget
being removed and this code running. The binding write has *already succeeded* by the time
`onEventBound()` runs; `RESULT_OK` and `finish()` must not be made conditional on an optional
follow-up redraw, or a successful write gets stranded behind a crash. If the immediate redraw is
skipped, `GlanceWidgetRefreshScheduler`'s live observation (§5) catches up shortly after, since it
depends only on the write, which already happened.

The ViewModel takes the widget id through an explicit `load(AppWidgetId)` call from a
`LaunchedEffect(Unit)`, not a Hilt-injected `SavedStateHandle` populated from intent extras — a
plain `ComponentActivity` does not wire that automatically, and the explicit call is one fewer
thing to get subtly wrong.

---

## 9. Glance integration — the sharp edges specific to this framework

- **`GlanceAppWidget` cannot be constructor-injected.** It is instantiated by Glance's own
  runtime, not by Hilt. `CountdownGlanceWidget.provideGlance` reaches its one dependency
  (`WidgetRenderModelProvider`) through `EntryPoints.get(context.applicationContext,
  WidgetEntryPoint::class.java)` — the single bridge point in the whole widget system. Every
  other injectable class (`WidgetConfigurationViewModel`, `GlanceWidgetRefreshScheduler`,
  `CountdownGlanceWidgetReceiver`'s fields) is wired normally. (KNOWN_ISSUES.md LIM-005.)
- **`GlanceAppWidgetReceiver` *is* a real Android component**, so `@AndroidEntryPoint` with
  field injection works on `CountdownGlanceWidgetReceiver` — `BroadcastReceiver`s are
  system-instantiated, so field injection is the only option, the same as it would be for any
  other receiver.
- **`ColorProvider(Int)` compiles but is library-restricted** (`@RestrictTo`) in the actual
  Glance 1.1.1 AAR — confirmed by decompiling it, not assumed. The sanctioned path wraps a
  Compose `Color` first: `ColorProvider(Color(argbInt))`.
- **`actionRunCallback` lives in `androidx.glance.appwidget.action`**, not
  `androidx.glance.action` — an easy wrong guess since most other action types are in the latter
  package.
- **Glance's `LocalContext` has no default**, unlike regular Compose UI's. Any test that renders
  a Glance composable must call `setContext(...)` before rendering, or every `LocalContext.current`
  read throws `IllegalStateException("No default context")`.
- **`hasText` in `glance-testing` is always a substring match** — its second parameter is
  `ignoreCase`, not a switch for exact matching. `hasTextEqualTo` is the separate function for an
  exact match. (D-038; this cost a real debugging round trip in Milestone 4 before being
  understood and documented.)
- **`GlanceModifier.semantics { }` exposes only `contentDescription` and `testTag`** in 1.1.1 —
  there is no Compose-UI-style `clearAndSetSemantics` to explicitly suppress child nodes from the
  accessibility tree. The whole-card `contentDescription` in §4 is the best available fix within
  that surface, not a claim that Glance's accessibility model matches Compose UI's.
- **`SizeMode.Exact`** (Milestone 5B, D-053) reports the widget's real current size continuously
  rather than snapping to one of a declared set — the app classifies that size itself
  (`classifyWidgetSize`, §4) rather than asking Glance to pick the nearest of a hand-maintained
  list. Chosen over `SizeMode.Responsive` specifically to avoid keeping two representations of
  "what sizes this widget supports" in sync (the XML cell-count declaration, §9 below, and a
  literal `Set<DpSize>`) — see D-053 for the full reasoning.

---

## 10. Known limitations

Full detail and severity live in `KNOWN_ISSUES.md`; this section is the widget-specific subset,
stated plainly for someone who needs to know what *not* to trust yet. Substantially rewritten
Session 10 — most of what this section listed as unconfirmed through Session 7 (real launcher
placement, per-style layout differentiation, determinate progress) is now confirmed; what remains
open is narrower and newer.

- **`WidgetSizeClass` thresholds are calibrated against one emulator's one launcher, not confirmed
  portable.** Real on-device measurement (D-055) replaced formula-derived thresholds that were
  wrong by roughly 2× — but the real numbers this session found (172×224dp for 2×2, 172×104dp for
  2×1) are only confirmed for this project's one known-stable local emulator running Pixel
  Launcher. A different launcher's grid could measure differently again, the same way this
  session's numbers already disagreed with Android's own documented formula. (TD-016.)
- **4×2 (`WIDE`) has no real-device visual confirmation.** Every `WIDE` layout is confirmed
  correct by Robolectric (renders without duplicating or losing content, across all seven styles)
  but has never been seen rendering on an actual launcher — three genuine device-automation
  attempts this session did not succeed in getting a widget into a wide-resized state.
  (TD-017, `docs/RESPONSIVE_WIDGET_REVIEW.md` has the full account of what was tried.)
- **Emoji rendering is unverified on real hardware.** Glance `Text` renders through a
  `RemoteViews` `TextView` in the *launcher's* process, so glyph coverage and sizing vary by
  launcher and OEM — the emulator cannot stand in for this. (LIM-006.)
- **No second-level ticking for the final day.** The launcher-ticked `Chronometer` piece of D-008
  was never built; only the coalesced-alarm half was (Session 12). A widget in its final hours
  updates at its next computed transition, not once per second. See
  `docs/WIDGET_REFRESH_ARCHITECTURE.md` §12 for this system's own, narrower known limitations
  (the safety net's real-world necessity is unverified, `setAndAllowWhileIdle`'s worst-case
  Doze deferral was not specifically forced, only one emulator/launcher was tested).
- **No Compose UI test for `WidgetConfigurationViewModel` directly.** Its cancel/confirm/no-orphan
  behavior was verified on-device (which is how BUG-R005 was actually found), not by a unit test
  that would catch a regression automatically. Worth adding now that the exact behavior is
  understood.
- **Glance's Robolectric-based testing API cannot observe actual text wrapping/ellipsis or a
  composable's resolved `ColorProvider` value.** Two real bugs this project has found
  (BUG-R010, BUG-R011) were only verifiable visually, on a real device, for exactly this reason —
  worth remembering before assuming a green Robolectric suite means a layout looks right.

---

## 11. Forward compatibility

These three sections describe *why the current design does not block* each future capability —
none of them are built, and nothing below should be read as a promise of a specific
implementation, only of an open path.

### 11.1 Multiple widget support (Milestone 5 — 5A and 5B delivered, remainder still open)

Nothing in this architecture ever assumed one widget. `WidgetBinding` is keyed per `appWidgetId`;
`WidgetRenderModelProvider.observe` takes an `appWidgetId` and nothing global;
`GlanceWidgetRefreshScheduler.updateAll` redraws every placed instance independently. Milestone 5A
(Session 9) and 5B (Session 10) delivered most of what this section originally listed as future
work:

- ~~`SizeMode.Exact` with breakpoint ranges~~ — done (D-053), reporting the widget's real size
  rather than snapping to a declared breakpoint set.
- ~~Per-style *layout* differences~~ — done; all seven styles now have genuinely different
  compositions at all three sizes (`docs/WIDGET_DESIGN_GUIDE.md`, `docs/WIDGET_SIZE_MATRIX.md`),
  not just resolved color/radius.
- ~~The Canvas-drawn progress ring~~ — done (§10's own note; LIM-001), now size-responsive across
  `STANDARD` and `WIDE`, deliberately absent at `COMPACT`.
- ~~A settings surface for the visibility flags~~ — done; `WidgetConfigurationActivity`'s
  customize step (Session 9) sets `showTitle`/`showEmoji`/`showTargetDate`/`showPercentage`
  independently of the `inheriting()` defaults, with a live preview.

**Still genuinely open:** confirming two widgets on the exact same event with different style
overrides through real UI (unit-tested via `WidgetBinding.resolveWidgetStyle`, not yet driven
end-to-end on a device — Session 10 confirmed the *different-events* case across two size classes,
which is a different scenario); a real `WIDE` measurement and screenshot (TD-017); confirming this
session's size thresholds hold on a device/launcher other than the one they were measured on
(TD-016).

### 11.2 Android 16 Live Updates

The seam is `WidgetRenderModel` itself: it is already a pure function's output —
`(Event, WidgetBinding, CountdownResult, ZoneId) → WidgetRenderModel` — with no Glance type
anywhere in it. A Live Updates adapter would consume the same `WidgetRenderModelProvider` this
document describes and translate the model into whatever API surface Live Updates exposes,
without touching `:core:domain`, `:core:data`, or anything in `:widget:engine`. Nothing in this
milestone was built *for* Live Updates specifically — the point is that nothing here needs to
change to make room for it either.

### 11.3 Lockscreen / Always-On Display

`android:widgetCategory="home_screen|keyguard"` is already declared in
`countdown_widget_info.xml` (present since this milestone, not deferred), so the capability
declaration exists even though no lockscreen-specific surface or layout has been built. The same
render-model seam in §11.2 applies here too: a keyguard surface is another thin adapter over
`WidgetRenderModelProvider`, not a reason to touch the engine.

---

## 12. Where to look for proof, not just claims

| Claim | Where it's verified |
|---|---|
| Engine has zero Android dependency | `widget/engine/build.gradle.kts` applies `countflow.jvm.library`; try importing `android.*` there and watch it fail to compile. |
| Theme resolution is exhaustive | `WidgetThemeResolverTest.kt` — one case per `WidgetStyle`. |
| Progress math is correct per style | `WidgetProgressEngineTest.kt`. |
| Override-else-default precedence | `WidgetRenderMapperTest.kt`, "a binding override beats the event default style/progress style". |
| Percent text only appears when both the binding asks for it *and* progress is visible | `WidgetRenderMapperTest.kt` + `CountdownWidgetContentTest.kt`, both sides of the conjunction (D-040). |
| No-orphan-bindings | `WidgetConfigurationActivity`'s device verification (SESSION_SUMMARY.md Session 5) plus `WidgetLifecycleCoordinatorTest.kt`. |
| The renderer draws nothing it wasn't told to draw | `CountdownWidgetContentTest.kt` — title/emoji/day-count visibility toggles. |
