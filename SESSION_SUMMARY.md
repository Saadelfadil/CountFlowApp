# CountFlow

## Session 6

Date: 2026-08-09
Current Milestone: **Milestone 4 — Widget engine (COMPLETE, finishing pass)**

> **READ THIS FIRST:** Milestone 4 is now finished, not just architected. Session 5 built the
> engine and the first widget; Session 6's brief was explicit that the milestone was not done
> until that one 2×2 widget was production quality — and closed two real gaps where a value the
> engine already computed never reached the screen. 222 tests pass, `:core:domain` is unchanged
> at 97.0% line coverage. Do **not** start Milestone 5 without explicit approval, and read TD-010
> before assuming a real widget placement works — it is closer than ever but still unverified.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md` (single-file orientation),
> `ARCHITECTURE.md` (design, wins on conflict), `docs/WIDGET_ARCHITECTURE.md` (new this session —
> the widget system in one file), `PROJECT_STATUS.md`, `DECISIONS.md` (40 entries), then this
> file.
>
> Three items are open for Session 7 — see "Requires approval" at the end.

----------------------------------

## Objective

The brief for this session was explicit and narrow: **finish the first widget, do not start the
next milestone.** No new sizes, no new styles, no animations, no Live Updates, no lockscreen —
"quality over quantity." A second explicit constraint: the architecture is now considered stable,
and no new interface/manager/coordinator/provider/service/resolver/helper/wrapper/engine should
be introduced unless it solves a problem that exists today. Every change this session either
polishes the existing renderer in place or fixes a value that was already computed upstream and
silently never used — nothing here is a new abstraction.

----------------------------------

## Completed

**Visual and accessibility polish, `CountdownWidgetContent.kt`**
- One `contentDescription` on the whole clickable card (`GlanceModifier.semantics { … }`),
  built from exactly the fields actually visible — e.g. "Trip to Kyoto. In 12 days. 40% complete."
  — instead of a screen reader piecing together the emoji, title, number, and label as unrelated
  fragments. Mirrors the pattern `EventCard` already uses in the app's own list.
- Typography and spacing tightened to a consistent scale (a `SPACING_XS`/`SPACING_SM` pair
  instead of ad hoc 4dp/6dp literals; the day-count headline bumped to 34sp for stronger
  hierarchy; a 6dp progress-bar height instead of the Glance default).
- The unconfigured ("tap to set up") placeholder redesigned to look intentional: centered, a "+"
  mark, clearer copy ("Tap to choose a countdown"), and its own content description.
- The title `Text` now takes `GlanceModifier.defaultWeight()` inside its `Row`, so a long title
  cannot push the row's layout in an unbounded way.

**Two real defects found and fixed — both dead fields, neither caught by a failing test**
- `WidgetTheme.isHighContrast` had been correctly computed by `WidgetThemeResolver` since the
  theme resolver was written, but nothing in the renderer ever read it — every color came from
  the ambient `GlanceTheme` regardless of what the resolver had decided. Compounding this, forced
  backgrounds (OLED's true black, Glass's translucent dark surface) paired with on-colors tuned
  for the *dynamic* Material You surface, with no guarantee the two agreed. Fixed by resolving
  text and progress-track colors explicitly against `hasForcedBackground` and `isHighContrast` in
  the renderer (BUG-R006, D-039).
- `WidgetBinding.showPercentage` has been persisted through Room and mapped through `:core:data`
  since Milestone 2, but no code between the database and the screen ever read it — setting it
  could never have shown a percentage anywhere. Fixed by adding
  `WidgetRenderModel.showPercentageText`, computed in `WidgetRenderMapper` as
  `binding.showPercentage && progress.isVisible` so the renderer reads one boolean rather than
  re-deriving the conjunction (BUG-R007, D-040). Currently inert for every real binding, since no
  UI sets the field to `true` yet — its value is in being correct the day a settings screen does.

**Performance measurement**
- The pure-Kotlin compute path — `CountdownEngine.countdownAt` + `WidgetRenderMapper.map`,
  everything that decides what a widget should show, with no I/O — measured directly at
  **~505ns per call** (200,000 iterations, JIT-warmed, on the development machine). This is not a
  device benchmark; it is a real, reproducible number for the one part of "widget creation" that
  `:widget:engine`'s pure-Kotlin boundary makes measurable without an emulator at all. Confirms
  the compute cost is not a concern at any plausible widget count — the real cost, unmeasured this
  session, is the Room query and the RemoteViews round-trip through the launcher process.
- No on-device performance numbers (widget update latency, memory, battery) were obtained — see
  "Three problems found" below.

**`docs/WIDGET_ARCHITECTURE.md` — new document**
- The permanent, senior-engineer-oriented reference for the whole widget system: module boundary,
  data flow, render flow, refresh flow, theme resolution (with the exact reasoning behind the
  forced-background color fix), binding lifecycle, configuration lifecycle, Glance's sharp edges
  (with real file/line references and, where relevant, "verified by decompiling the AAR" rather
  than assumed), known limitations, and three explicitly-scoped forward-compatibility sections
  (multiple widgets, Android 16 Live Updates, lockscreen) that describe *why the current design
  does not block* each, without claiming any of them are built.

**Attempted, and partially achieved: real widget placement (TD-010)**
- See "Three problems found" below — this is the one planned piece of work that did not complete,
  for an environmental reason rather than a code reason.

**Verification**
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 222 tests, 0 failures (5 new: 3 in `:widget:glance` for percent-text visibility including the
  "requested but progress is off" edge case, 2 in `:widget:engine` for the same conjunction at
  the mapper level). Lint 0 errors, 10 accepted warnings, unchanged.
- `:core:domain` coverage unchanged at 97.0% lines — this session touched only `:widget:engine`
  and `:widget:glance`.

----------------------------------

## Three problems found, and one environment limitation

1. **`WidgetTheme.isHighContrast` was dead code for its entire life so far.** Found not by a
   failing test but by deliberately re-reading the render model's fields against what the
   renderer actually consumed, while auditing the widget for "production quality" as the brief
   asked. No test had ever asserted the field was read, so none failed when it wasn't. Fixed; see
   BUG-R006 above.

2. **`WidgetBinding.showPercentage` was dead code since Milestone 2 — three sessions.** Found the
   same way, during the same audit. A field can be perfectly persisted, perfectly mapped through
   every intermediate layer, and still never do anything if the one layer that would act on it
   never reads it — and nothing about the test suite passing says otherwise, because there was
   nothing to assert against. Fixed; see BUG-R007 above.

   **The pattern worth remembering from both of these:** a green test suite proves the tests that
   exist pass, not that every field has a reader. `AI_CONTEXT.md` now carries this as a permanent
   note — when a render model gains a field, verify something actually reads it before calling
   the work done, not just that the field compiles and is spelled correctly everywhere.

3. **The test device this session was materially better than Session 5's, then became
   unreachable mid-verification — an environment problem, not a code problem.** Unlike Session
   5's headless (`-no-window`) AVD, which failed `appwidget grantbind` outright with
   `IllegalStateException: User -2 must be unlocked`, this session's device was a genuine GUI-mode
   emulator: a real launcher rendered and was screenshotted (wallpaper, search bar, icons,
   navigation bar), `adb shell dumpsys user` reported `RUNNING_UNLOCKED`, and
   `adb shell appwidget grantbind --package com.countflow --user 0` **succeeded** (exit 0). The
   app installed successfully. But the `adb` connection required reconnecting between nearly
   every command, the device's own reported model changed mid-session (`Pixel_8` → `Pixel_9`),
   and a long-press on an empty home-screen area opened an unrelated pre-existing "Reminders" app
   with auto-generated data CountFlow never created — all consistent with an ephemeral or pooled
   test resource that can be reclaimed without notice, not a dedicated stable target. The
   connection was finally refused outright (`Connection refused`) partway through the
   long-press → widget picker → drag sequence, before a placed widget could be reached or
   screenshotted, and did not recover after several retries and an `adb kill-server` /
   `start-server` cycle.

**Net effect on TD-010.** Materially more promising than Session 5 — the permission failure that
blocked Session 5 outright is gone — but still open. The next session should treat device
*stability*, not widget-bind permissions, as the thing to verify first.

----------------------------------

## Files Created

One new file, several rewritten in place (no new modules, no new classes beyond what closing the
two dead-field gaps required).

```
docs/WIDGET_ARCHITECTURE.md                                    (new)
```

Rewritten: `widget/glance/…/CountdownWidgetContent.kt` (accessibility, color resolution,
percent-text element, restyled unconfigured state), `widget/engine/…/model/WidgetRenderModel.kt`
(+1 field), `widget/engine/…/mapper/WidgetRenderMapper.kt` (computes the new field),
`widget/glance/src/test/…/CountdownWidgetContentTest.kt` (+3 tests, model helper extended),
`widget/engine/src/test/…/mapper/WidgetRenderMapperTest.kt` (+2 tests, binding helper extended).

----------------------------------

## Architecture Decisions

Two new entries, D-039 and D-040, detailed in `DECISIONS.md`:

- **D-039 — Forced-background widget themes resolve their own text and progress-track colors**,
  rather than pulling them from `GlanceTheme`'s ambient, wallpaper-tuned scheme. Also the fix for
  `isHighContrast` having been computed but never consumed.
- **D-040 — `showPercentageText` is conjoined in the mapper, not left for the renderer to
  derive** — `binding.showPercentage && progress.isVisible`, computed once where the two facts
  are already in hand, so the renderer reads one boolean rather than re-deriving a business rule.

Both entries are explicit that neither is a new abstraction — the session's own architectural
rule — just correct application of fields that already existed.

----------------------------------

## Current Project Structure

Unchanged from Session 5 at the module level — no new modules, no new top-level classes. See
`PROJECT_STATUS.md` for the full module graph and status table; the only structural addition this
session is `docs/WIDGET_ARCHITECTURE.md` alongside the other permanent documents.

----------------------------------

## Dependencies Added

None.

----------------------------------

## Current Features Working

- Everything from Milestones 1–4 (Session 5) is unchanged and still passing.
- The countdown widget's accessibility, color correctness on forced-dark themes, and
  configuration-field fidelity (every field a binding can set now actually affects rendering) are
  new this session — see "Completed" above.

----------------------------------

## Pending Work

**P0 — blocks Session 7**
1. **Approval to begin Milestone 5** (multiple widgets, themes, sizes).
2. **Verify real widget placement on a *stable* GUI-mode emulator or physical device** (TD-010) —
   now the only unverified piece of Milestone 4, and likely blocked on device stability rather
   than app behavior. Open with a `grantbind` + `dumpsys user` check before investing more time.
3. **Confirm the countdown label policy** — still unanswered since Session 3.

**P1 — Milestone 5:** circular progress ring, per-style layout differentiation, additional sizes,
a settings surface for the visibility flags that are now fully wired but have no UI, accent-colour
picker and live widget preview deferred from Milestone 3, list gestures (TD-008).

Full breakdown in `TODO.md`.

----------------------------------

## Known Issues

No open runtime bugs as of this session. Full detail in `KNOWN_ISSUES.md`.

**Closed this session:** BUG-R006 (`isHighContrast` never applied), BUG-R007 (`showPercentage`
never rendered).

**Updated this session:** TD-010 — new evidence recorded (grantbind succeeded, device unlocked,
real launcher reached), severity assessment updated, but still open.

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-008, TD-009. LIM-001, LIM-003,
LIM-004, LIM-006.

**Testing gaps:** no Compose UI tests for the app's own screens; no direct unit test for
`WidgetConfigurationViewModel`; no real-device instrumented test for the full widget lifecycle
(blocked on TD-010).

**Lint:** 0 errors, 10 accepted warnings.

----------------------------------

## Next Session Plan

**Step 0 is a gate.** Resolve the three P0 items. Do not start Milestone 5 without approval.

1. Before anything else: confirm device stability. `adb shell appwidget grantbind --package
   com.countflow --user 0` and `adb shell dumpsys user` should both succeed and stay reachable for
   the whole verification — if the connection is unstable, treat that as the blocker to solve
   first, not a code problem to route around.
2. Drag CountFlow's widget from the picker onto a real home screen, confirm it renders, edit the
   bound event and confirm the widget updates, remove it and confirm the binding is cleaned up.
   Screenshot each step.
3. Circular progress ring: `Canvas` → `Bitmap` → `ImageProvider`, sized against `LocalSize`,
   quantized to whole percent, budgeted against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
4. Differentiate all seven `WidgetStyle` values by layout, not just color — the resolver and (as
   of this session) the renderer's color handling are both already correct per-style; only
   structural layout is still shared.
5. Sizes 2×1 and 4×2 using `SizeMode.Exact` and breakpoint ranges.
6. Accent-colour picker, live widget preview, and list gestures (TD-008), all deferred from
   earlier milestones specifically because the renderer they depend on now exists and is finished.
7. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   eight documents (`AI_CONTEXT.md` and `docs/WIDGET_ARCHITECTURE.md` included).

Suggested commits: `feat(widget-engine): circular progress ring`,
`feat(widget): style-differentiated layouts`, `feat(widget): additional widget sizes`,
`feat(events): accent colour picker and widget preview`, `feat(events): list gestures`.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 222 tests, 0 failures — 5 new this session (3 `:widget:glance`, 2 `:widget:engine`)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 10 warnings, all previously accepted
- Compute-path performance measured directly (not device-profiled): ~505ns/call for
  `CountdownEngine.countdownAt` + `WidgetRenderMapper.map`
- Runtime: app installed successfully on a GUI-mode test device; `appwidget grantbind` succeeded;
  full drag-onto-home-screen placement not completed — device became unreachable mid-session (see
  "Three problems found")

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

**222 written, 222 passing, 0 failing.**

| Module | Tests | What changed this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 32 | +2: `showPercentageText` is true only when the binding asks for it *and* progress is visible; false when progress itself is off even if requested |
| `:widget:glance` | 8 | +3: percent text shown when requested, hidden when not, hidden when progress is off even if requested |

**Coverage** — `:core:domain` 97.0% lines, unchanged; this session did not touch that module.

----------------------------------

## Git Status

Two commits this session, on `master`:

```
faacccc  fix(widget): apply forced-background/high-contrast colors and wire showPercentage
         docs: milestone 4 finishing-pass documentation        ← this commit
```

Twenty-nine commits total. No remote configured.

----------------------------------

## Developer Notes

- **A render model field with no reader is a bug, not a work-in-progress.** `WidgetTheme.isHighContrast`
  and `WidgetBinding.showPercentage` both compiled, both had correct values, and both did nothing
  for one to three sessions because nothing downstream read them. When adding a field to
  `WidgetRenderModel` or `WidgetBinding`, trace it all the way to the renderer before considering
  the work done.
- **Forced-background themes (OLED, Glass) cannot use `GlanceTheme`'s on-colors.** Those colors
  are tuned for the *dynamic* Material You surface; a theme that forces its own background needs
  its own fixed on-colors. See `ForcedBackgroundPalette` in `CountdownWidgetContent.kt` and
  `docs/WIDGET_ARCHITECTURE.md` §6.
- **Glance's `semantics { }` only exposes `contentDescription` and `testTag`** in 1.1.1 — there is
  no `clearAndSetSemantics` equivalent to suppress child nodes. A whole-card `contentDescription`
  is the best available accessibility fix within that surface, not a claim that it behaves
  identically to Compose UI's semantics model.
- **The compute cost of a `WidgetRenderModel` is not a performance concern** (~505ns/call,
  measured). If a future widget update ever feels slow, look at the Room query or the RemoteViews
  round-trip, not the engine.
- **`:widget:engine` needs no Robolectric for anything** — direct proof the pure-Kotlin boundary
  (D-033) keeps paying off: tests here run as plain JVM tests, no Android runtime, no emulator.
- **Build output is noisy** (TD-005). Filter with
  `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"`.
- **A test device that requires reconnecting between commands, or whose `model` property changes
  mid-session, is not reliable enough to trust for a definitive placement test.** Confirm
  stability first; don't spend a session's remaining time fighting a flaky connection.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`.

----------------------------------

## Requires approval before Session 7

1. **Milestone 5.**
2. **A stable device for TD-010 verification** — recommended to happen first, since it is now the
   only unverified piece of Milestone 4 and Session 5/6 both spent real time on environment
   problems rather than app problems.
3. **The countdown label policy**, still unanswered since Session 3: an event a week out reads
   "7 / Next week". Is that the wording and threshold set you want?

----------------------------------

## Estimated Progress

```
Overall Progress            47%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             85%   (gestures and colour picker outstanding)
Widget Engine                95%   (real launcher placement unverified — TD-010)
Widget Themes & Sizes         0%
Notifications                 0%
Billing                       0%
Testing                      75%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
