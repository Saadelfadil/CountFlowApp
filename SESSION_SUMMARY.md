# CountFlow

## Session 5

Date: 2026-08-09
Current Milestone: **Milestone 4 — Widget engine (COMPLETE)**

> **READ THIS FIRST:** Milestone 4 is done. 217 tests pass, `:core:domain` is at 97.0% line
> coverage, and the widget engine's own device verification (configuration activity, binding
> writes, cancel, and startup pruning) was driven directly against the database on a real
> emulator. One real crash was found and fixed before it could ship. Do **not** start Milestone 5
> without explicit approval — and read TD-010 before trusting a real widget placement works.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md` (new this session — single-file
> orientation for an AI picking this up cold), `ARCHITECTURE.md` (design, wins on conflict),
> `PROJECT_STATUS.md` (permanent overview), `DECISIONS.md` (38 entries), then this file.
>
> Three items are open for Session 6 — see "Requires approval" at the end.

----------------------------------

## Objective

Build the Widget Engine the brief specified, not widgets: `Room → Repository → CountdownEngine →
Widget Engine → Widget Render Model → Glance Widget`, with business logic kept entirely out of
the render layer so every future widget type (habit, battery, fasting, weather, calendar) can
reuse the same pipeline. One basic 2×2 widget only, to prove the architecture — not to look good.

----------------------------------

## Completed

**Step 0 — Two presentation rules moved into `:core:domain` first**
- `CountdownResult.showsMeaningfulDayCount` and `EventCategory.defaultEmoji`, both private
  helpers inside Session 4's `EventUiMapper`, moved to live beside `CountdownLabel` and
  `EventCategory`. Done *before* the widget mapper existed, so there was never a moment where two
  copies of either rule could drift apart (D-034).

**Step 1 — `:widget:engine` converted to pure Kotlin/JVM**
- Reversed the Milestone 1 scaffold's `countflow.android.library` for `countflow.jvm.library`
  (D-033), mirroring D-003. An accidental Android import in the engine is now a compile error,
  not a review comment.

**Step 2 — `WidgetRenderModel`**
- Pure Kotlin data class, zero Android dependency: title, emoji, day count, label token,
  completion/expiry flags, plus two nested value types rather than the brief's flat field list —
  `WidgetProgress` (style, fraction, percent, percentText, visibility) and `WidgetTheme` (style,
  accent, background, corner radius, contrast). Nesting groups fields that are always computed
  and consumed together; documented inline as a deliberate deviation from the brief's example.

**Step 3 — `WidgetRenderMapper`**
- `Event + WidgetBinding + CountdownResult + ZoneId → WidgetRenderModel`, applying
  `binding.resolveWidgetStyle(event)` / `resolveProgressStyle(event)` — the override-else-default
  precedence rule from D-013, exercised for the first time by real code.

**Step 4 — `WidgetThemeResolver`**
- All seven `WidgetStyle` values resolved through one exhaustive `when`: Minimal, Material, OLED
  (forces true black regardless of accent), Glass (translucent dark surface, larger corner
  radius), Rounded (largest corner radius), Progress, Modern. The renderer never branches on
  style itself — only on the resolved `WidgetTheme`.

**Step 5 — `WidgetProgressEngine`**
- One function, `calculate(countdown, style) -> WidgetProgress`, covering Linear, Circular,
  Percentage, Days Remaining, and Completed. No UI: fraction, whole-percent, and pre-formatted
  percent text only, so both today's linear bar and Milestone 5's circular ring read from the
  same computed values.

**Step 6 — Configuration Activity**
- `WidgetConfigurationActivity` + `WidgetConfigurationViewModel`: pick an event, bind it, request
  an immediate redraw, close. `RESULT_CANCELED` is set before any UI shows, so a binding is only
  ever written in direct response to a selection — the entire mechanism behind "no orphan
  bindings." Verified against the database directly (see below), not just by unit test.

**Step 7 — First Glance widget**
- `CountdownGlanceWidget`, one 2×2 size, reaching Hilt through an `EntryPoint`
  (`GlanceAppWidget` cannot be constructor-injected — LIM-005). `CountdownWidgetContent`
  displays title, emoji, day count, and a linear progress bar. Click actions are `ActionCallback`s
  rather than `actionStartActivity<MainActivity>()`, since `:widget:glance` cannot depend on
  `:app` without inverting the module graph (D-035). `GlanceWidgetRefreshScheduler` redraws every
  widget whenever `EventRepository.observeEventsWithWidgets()` emits, and prunes orphaned
  bindings once at app startup (D-036 — explicitly the Milestone 4 seam, not Milestone 8's final
  alarm-based strategy).

**Step 8 — Verification**
- `assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 217 tests, 0 failures. Lint 0 errors, 10 accepted warnings.
- On an API 36 emulator: launched the configuration activity directly with controlled widget ids
  and inspected `countflow.db` after each step. Confirmed: selecting an event writes exactly one
  `widget_bindings` row with the correct `event_id`; pressing back before selecting writes
  nothing; force-stopping and relaunching runs `pruneOrphanedBindings` and correctly discards a
  binding whose `appWidgetId` the (synthetic, in this environment) launcher does not report live.
- **Not verified:** a widget dragged onto a real home screen through the actual
  `AppWidgetHost`/launcher flow. See "Three problems found" below and TD-010.

----------------------------------

## Three problems found, and one environment limitation

1. **Real production crash, found by device testing.**
   `WidgetConfigurationActivity.onEventBound()` called
   `GlanceAppWidgetManager.getGlanceIdBy(appWidgetId)` unconditionally to force an immediate
   redraw after a successful binding write. When the id did not resolve to a real,
   host-registered widget, this threw and crashed the activity — *after* the binding write had
   already durably succeeded, stranding good data behind a crash instead of confirming and
   closing. Found because the activity appeared to close cleanly on one test pass but the
   database showed no row, and the crash log explained why. Fixed by wrapping the redraw in
   `runCatching` and moving `finish()` outside it: a write that already succeeded must not be
   undone by an optional follow-up step failing. Recorded as BUG-R005.

2. **A real lint defect in Session 4 code, not a false positive.** `CountdownLabelFormatter`
   read `LocalConfiguration.current` as a bare recomposition trigger and then manually pulled
   `LocalContext.current.resources` — the `LocalContextResourcesRead` lint check correctly
   flagged this as insufficient. Fixed by switching to `stringResource()` /
   `pluralStringResource()` directly, which are the properly recomposition-safe APIs. Not
   suppressed; the underlying code was actually wrong.

3. **`ColorProvider(Int)` compiles but fails lint.** Used the `Int`-taking overload for `OLED`'s
   forced true-black and `GLASS`'s translucent surface; compiled cleanly, then failed
   `:app:lintDebug` with two `RestrictedApi` errors, since that overload is `@RestrictTo`-marked
   in the actual Glance 1.1.1 AAR (confirmed by decompiling it, not guessed). Fixed with
   `ColorProvider(Color(argb))`.

**The environment limitation is worth recording as clearly as the bugs.** `adb shell appwidget
grantbind` — the standard scriptable path for testing a widget bind without full launcher UI
automation — failed on this headless (`-no-window`) test AVD with `IllegalStateException: User -2
must be unlocked for widgets to be available`, confirmed by process/PID inspection to originate
from the shell's own `appwidget` binary, not from CountFlow. A retry hung rather than erring.
Sending the system's real `ACTION_APPWIDGET_DELETED` broadcast was separately blocked with
`SecurityException: not allowed to send broadcast ... uid=2000`, since it is a protected system
broadcast — expected Android behaviour, not a bug. Both read as genuine environment limitations of
headless emulation. Tracked as **TD-010**, first item scheduled for Session 6.

----------------------------------

## Files Created

24 new files this session; 120 Kotlin files across the codebase.

```
core/domain/…/model/EventCategory.kt                        (defaultEmoji added)
core/domain/…/countdown/CountdownLabel.kt                    (showsMeaningfulDayCount added)
core/domain/…/model/Ids.kt                                   (AppWidgetId.INVALID added)
core/domain/src/test/…/countdown/CountdownLabelPresentationTest.kt

widget/engine/build.gradle.kts                                (converted to jvm.library)
widget/engine/…/model/{WidgetTheme,WidgetProgress,WidgetRenderModel}.kt
widget/engine/…/theme/WidgetThemeResolver.kt
widget/engine/…/progress/WidgetProgressEngine.kt
widget/engine/…/mapper/WidgetRenderMapper.kt
widget/engine/…/provider/WidgetRenderModelProvider.kt
widget/engine/…/lifecycle/WidgetLifecycleCoordinator.kt
widget/engine/…/refresh/WidgetRefreshScheduler.kt             (interface)
widget/engine/src/test/…/{theme,progress,mapper,provider,lifecycle}/*Test.kt
widget/engine/src/test/…/testing/FakeWidgetBindingRepository.kt

widget/glance/build.gradle.kts                                (hilt + test deps added)
widget/glance/…/di/{WidgetGlanceModule,WidgetEntryPoint}.kt
widget/glance/…/CountdownWidgetContent.kt
widget/glance/…/action/WidgetActions.kt
widget/glance/…/CountdownGlanceWidget.kt
widget/glance/…/CountdownGlanceWidgetReceiver.kt
widget/glance/…/refresh/GlanceWidgetRefreshScheduler.kt
widget/glance/…/configuration/{WidgetConfigurationUiState,WidgetConfigurationViewModel,
                                WidgetConfigurationActivity}.kt
widget/glance/src/main/AndroidManifest.xml
widget/glance/src/main/res/xml/countdown_widget_info.xml
widget/glance/src/main/res/values/{themes,strings}.xml
widget/glance/src/test/…/CountdownWidgetContentTest.kt
widget/glance/src/test/resources/robolectric.properties

app/…/CountFlowApplication.kt                                 (widgetRefreshScheduler.start())

AI_CONTEXT.md                                                  (new document type this session)
```

Rewritten, not new: `core/designsystem/…/format/CountdownLabelFormatter.kt`,
`feature/events/…/model/EventUiMapper.kt` (private copies removed in favour of the moved rules),
`app/src/main/res/xml/data_extraction_rules.xml` (comment documents D-037).

----------------------------------

## Architecture Decisions

Six new entries, D-033 to D-038, detailed in `DECISIONS.md`:

- **D-033 — `:widget:engine` becomes pure Kotlin/JVM**, the same structural argument as D-003:
  "widgets should only render" is enforced as a compile error, not a convention.
- **D-034 — Two presentation rules moved from `:feature:events` into `:core:domain`** so the app
  and the widget can never disagree about whether a day count is worth showing, or what emoji a
  category defaults to.
- **D-035 — Click targets are `ActionCallback`s, not `actionStartActivity<MainActivity>()`**,
  since `:widget:glance` cannot see `:app` without inverting the module graph.
- **D-036 — The refresh scheduler is a seam, not Milestone 8's strategy.** App-alive live
  observation now; the full coalesced-alarm design from D-008 lands in Milestone 8. A widget
  under this scheme is never *wrong* between redraws, only stale while backgrounded, because Room
  is always the source of truth (D-002).
- **D-037 — Widget bindings cannot be excluded from backup at the table level**, since Android's
  data-extraction-rules operate on whole files, not tables, and `widget_bindings` shares a
  database file with `events` and `reminders`. Mitigated exactly (not probabilistically) by
  `WidgetLifecycleCoordinator.pruneOrphans()` discarding any restored binding whose
  `appWidgetId` is not live on the current device.
- **D-038 — Glance's `hasText` test matcher is always a substring match**, documented after it
  cost a debugging round trip; `hasTextEqualTo` is the exact-match function.

----------------------------------

## Current Project Structure

```
CountFlow App/
├── 9 markdown documents (AI_CONTEXT.md new)
├── build-logic/               7 convention plugins
├── app/                       Application (starts widget refresh scheduler), MainActivity, NavHost
├── core/
│   ├── common/                dispatchers, scope, logging, Clock          [implemented]
│   ├── designsystem/          theme + token-to-text formatting            [implemented]
│   ├── domain/                model, engine, validation, contracts        [COMPLETE]
│   ├── database/               Room, 3 entities, 3 DAOs, schema v1        [COMPLETE]
│   ├── data/                  repositories, mappers, DataStore            [COMPLETE]
│   ├── notifications/                                                     [empty — M7]
│   ├── analytics/                                                         [empty — M9]
│   └── billing/                                                           [empty — M9]
├── feature/
│   ├── events/                home list, create/edit, 2 ViewModels        [COMPLETE]
│   ├── settings/               placeholder                                [nav done]
│   └── premium/                placeholder                                [nav done]
└── widget/
    ├── engine/                render model, theme resolver, progress      [COMPLETE — pure Kotlin]
    │                          engine, mapper, provider, lifecycle coordinator
    └── glance/                first widget, config activity, scheduler    [COMPLETE — one size]
```

----------------------------------

## Dependencies Added

No new external dependencies. `:widget:engine` **removed** `androidx.work.runtime.ktx` (no
longer needed once it stopped being an Android library). `:widget:glance` gained
`countflow.android.hilt`, core-ktx, activity-compose, lifecycle-viewmodel-compose,
hilt-navigation-compose, plus Robolectric and androidx-test-core-ktx as test dependencies.

----------------------------------

## Current Features Working

- Everything from Milestones 1–3 is unchanged and still passing.
- A countdown widget exists: 2×2, showing title, emoji, day count, and a linear progress bar.
- Placing a widget through configuration writes the correct binding; cancelling writes nothing —
  both verified against the database, not assumed.
- The widget redraws while the app is alive whenever its underlying event changes.
- Force-stopping and relaunching the app prunes any binding not backed by a live widget id.
- Widget style and progress style resolve per-binding with an override-else-default fallback to
  the event's own default (D-013), now actually exercised by real code for the first time.

----------------------------------

## Pending Work

**P0 — blocks Session 6**
1. **Approval to begin Milestone 5** (multiple widgets, themes, sizes).
2. **Verify real widget placement on a GUI emulator or physical device** (TD-010) — the one piece
   of Milestone 4 the headless environment could not confirm. Do this before more widget styles
   are built on an unconfirmed rendering path.
3. **Confirm the countdown label policy** — carried over unanswered from Sessions 3 and 4.

**P1 — Milestone 5:** circular progress ring (Canvas + bitmap budget, LIM-001/LIM-003), all seven
widget styles rendered distinctly (not just themed), sizes 2×1 and 4×2, the accent-colour picker
and live widget preview deferred from Milestone 3, archive/complete/delete gestures (TD-008).

Full breakdown in `TODO.md`.

----------------------------------

## Known Issues

No open runtime bugs. Full detail in `KNOWN_ISSUES.md`.

**Closed this session:** BUG-R005 (configuration crash on redraw failure, found and fixed before
shipping).

**Open, new this session:**
- **TD-010 (Medium, new)** — no widget verified through a real `AppWidgetHost`/launcher flow;
  headless AVD limitation, not a code defect. First item for Session 6.

**Open, unchanged:**
- **TD-001 (High)** — AGP built-in-Kotlin migration still pending.
- **TD-007 (Medium)** — some UI strings still not localised. Scheduled for Milestone 6.
- **TD-002, TD-005, TD-006, TD-008, TD-009** unchanged.
- **LIM-005 resolved** this session (Hilt `EntryPoint` bridge). **LIM-001, LIM-003, LIM-006**
  still open, relevant from Milestone 5.

**Testing gaps:** no Compose UI tests for the app's own screens; no direct unit test for
`WidgetConfigurationViewModel` (its behaviour was verified on-device instead, which is how
BUG-R005 was found).

**Lint:** 0 errors, 10 accepted warnings.

----------------------------------

## Next Session Plan

**Step 0 is a gate.** Resolve the three P0 items. Do not start Milestone 5 without approval, and
do not build more widget styles before TD-010 is closed.

1. Verify one real widget placement on a GUI-mode emulator or physical device: drag from the
   widget picker onto a home screen, confirm it renders, edit the bound event and confirm the
   widget updates, remove it and confirm the binding is cleaned up.
2. Circular progress ring: `Canvas` → `Bitmap` → `ImageProvider`, sized against `LocalSize`,
   quantized to whole percent, budgeted against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
   `WidgetProgress.percent` is already the correct cache-key shape.
3. Differentiate all seven `WidgetStyle` values by layout, not just colour — the resolver already
   produces correct tokens for each; the renderer does not yet vary structure.
4. Sizes 2×1 and 4×2 alongside the existing 2×2, using `SizeMode.Exact` and breakpoint ranges.
5. Accent-colour picker and live widget preview in the create/edit form — both deferred from
   Milestone 3 specifically because the renderer they preview now exists.
6. Archive/complete/delete gestures on the home list (TD-008).
7. Verify emoji rendering on real hardware, not just the emulator (LIM-006).
8. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   eight documents (including `AI_CONTEXT.md`).

Suggested commits: `feat(widget-engine): circular progress ring`,
`feat(widget): style-differentiated layouts`, `feat(widget): additional widget sizes`,
`feat(events): accent colour picker and widget preview`, `feat(events): list gestures`.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 217 tests, 0 failures — 38 new this session (30 `:widget:engine`, 5 `:widget:glance`, 3
  `:core:domain`)
- Coverage gate passed: `:core:domain` 97.0% lines, against a 95% line minimum
- Lint: 0 errors, 10 warnings, all previously accepted
- Runtime: configuration activity's select/cancel/prune paths verified against the database on an
  API 36 emulator; one real crash found and fixed. Real launcher placement unverified (TD-010).

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

**217 written, 217 passing, 0 failing.**

| Module | Tests | What they cover |
|---|---|---|
| `:core:domain` | 91 | Everything from prior milestones, plus 3 new: `showsMeaningfulDayCount` across near-term, far, completed, and expired cases |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 30 | Theme resolution for all seven styles, progress calculation for all five styles, the render mapper's override-else-default precedence, the render-model provider joining event/binding/countdown, and lifecycle/pruning logic — all plain JUnit, no Robolectric needed |
| `:widget:glance` | 5 | `CountdownWidgetContent` rendering against a fabricated `WidgetRenderModel`, using Glance's own unit-test framework |

**Coverage** — `:core:domain` 97.0% lines, enforced by `koverVerify`. `:widget:engine` has no
gate (not yet added to the convention) but its 30 tests exercise every branch of the theme
resolver's exhaustive `when` and every progress style.

Technique worth keeping: `:widget:engine`'s tests need no Robolectric at all, unlike every other
module with Android-adjacent logic — direct proof that the pure-Kotlin boundary (D-033) is
earning its cost in faster, simpler tests, not just architectural tidiness.

----------------------------------

## Git Status

Five commits this session so far, on `master`:

```
65d55f2  refactor(domain): move shared presentation rules out of the app-only mapper
0a92e26  feat(widget-engine): render model, theme resolver, and progress engine
50dd641  fix(designsystem): use stringResource/pluralStringResource for correct recomposition
9267619  feat(widget): first countdown widget end to end
85043bc  feat(app): start the widget refresh scheduler at launch
         docs: milestone 4 documentation        ← this commit
```

Twenty-seven commits total. No remote configured.

----------------------------------

## Developer Notes

- **The widget engine is pure Kotlin — keep it that way.** If a future change to
  `:widget:engine` needs `Context`, `Bitmap`, or anything from `android.*`, that logic belongs in
  `:widget:glance` instead. This is now a compile error, not a review comment (D-033).
- **`WidgetRenderModel` nests `WidgetProgress` and `WidgetTheme` rather than flattening every
  field**, deliberately deviating from the brief's flat example list. Read the KDoc on
  `WidgetRenderModel` before "simplifying" it back to flat fields.
- **A binding write is durable before any redraw is attempted.** Never make `RESULT_OK` or
  `finish()` in the configuration activity depend on an optional follow-up step succeeding — that
  is exactly what BUG-R005 was.
- **`ColorProvider(Int)` is `@RestrictTo`-marked in Glance 1.1.1.** Use `ColorProvider(Color(argb))`.
  Confirmed by decompiling the AAR, not by trial and error alone.
- **`actionRunCallback` lives in `androidx.glance.appwidget.action`, not `androidx.glance.action`.**
- **`hasText` in Glance's testing library is always a substring match** (D-038). Use
  `hasTextEqualTo` for exact assertions.
- **Every `runGlanceAppWidgetUnitTest` block needs `setContext(...)` before `provideComposable`**,
  since Glance's `LocalContext` has no default, unlike regular Compose.
- **`adb shell appwidget grantbind` is unreliable on a headless (`-no-window`) AVD.** Use a
  GUI-mode emulator or physical device for real widget-placement testing (TD-010).
- **Build output is noisy** (TD-005). Filter with
  `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"`.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`.

----------------------------------

## Requires approval before Session 6

1. **Milestone 5.**
2. **Real widget placement verification (TD-010)** — recommended to happen first, before
   Milestone 5 adds more widget styles on top of a rendering path only proven by unit tests and
   direct database inspection, not by an actual launcher.
3. **The countdown label policy**, still unanswered since Session 3: an event a week out reads
   "7 / Next week". Is that the wording and threshold set you want? One line in `CountdownConfig`
   today; two surfaces and their tests once Milestone 5 adds more widget styles that render it.

----------------------------------

## Estimated Progress

```
Overall Progress            46%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             85%   (gestures and colour picker outstanding)
Widget Engine                90%   (real launcher placement unverified — TD-010)
Widget Themes & Sizes         0%
Notifications                 0%
Billing                       0%
Testing                      75%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
