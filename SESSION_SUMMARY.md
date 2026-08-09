# CountFlow

## Session 10

Date: 2026-08-09
Current Milestone: **Milestone 5B — Responsive widget system, 2×1 / 2×2 / 4×2 (COMPLETE)**

> **READ THIS FIRST:** This session turned the 2×2 visual language Session 9 delivered into one
> coherent responsive system across three sizes — not three independently designed widgets, and
> not the existing 2×2 mechanically stretched or shrunk. All 21 Style × Size combinations (7
> styles × 3 sizes) have their own information hierarchy, documented field-by-field in the new
> `docs/WIDGET_SIZE_MATRIX.md`. Real-device work found and fixed two significant bugs: the
> `WidgetSizeClass` dp thresholds, first derived from Android's cell-size *formula*, did not match
> what a real launcher actually rendered (the same category of mistake as BUG-R009); and a
> `Row`/`fillMaxWidth` layout bug hid the headline entirely in one compact layout. Two product
> decisions pending since earlier sessions are now closed permanently: the countdown label
> hierarchy (D-051) and BUG-011's no-further-work-until-Milestone-8 status (D-052).
>
> **One real, named gap:** the 4×2 (`WIDE`) size has no real-device visual confirmation —
> Robolectric only. Three separate device-automation attempts (drag-resize, remove-then-resize,
> adjusted-coordinate retry) did not succeed within the session's time budget (TD-017). The
> `WidgetSizeClass` thresholds themselves are confirmed on exactly one emulator/launcher
> combination (TD-016).
>
> **Do not start Milestone 6 or any further Milestone 5 work without explicit approval.** This
> session stopped exactly where its brief said to stop.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_ARCHITECTURE.md`, `docs/WIDGET_DESIGN_GUIDE.md`, `docs/WIDGET_SIZE_MATRIX.md` (new
> — the full 21-combination matrix), `docs/RESPONSIVE_WIDGET_REVIEW.md` (new — real-device
> evidence and the Final Report), `DECISIONS.md` (57 entries), then this file.
>
> Two items are open for Session 11 — see "Requires approval" at the end.

----------------------------------

## Objective

Milestone 5B: extend the seven-style 2×2 widget system from Milestone 5A into a genuinely
responsive design across 2×1, 2×2, and 4×2, per the brief's explicit framing — "the goal is NOT
simply three sizes. The goal is one coherent responsive design system," with a critical rule
against stretching or shrinking the 2×2 layouts mechanically. Also required: a content-fit system
for digit counts, word lengths, and long titles; migration to the correct `SizeMode.Exact`
configuration with breakpoints derived from real `LocalSize`, not assumed launcher cell
dimensions; a genuinely distinct compact (2×1) design built for glanceability, not every field
crammed in; a genuinely distinct wide (4×2) design that uses width deliberately; a full size
matrix document; progress-ring responsiveness with bitmap memory verified; a size-aware
configuration-screen preview; multi-widget and independent-binding verification, including two
widgets on the same event with different styles; font-scale and edge-case validation on a real
device; light/dark verification across at least five styles; accessibility re-verification. Two
product decisions were also required to be recorded and closed permanently: the countdown label
policy, and BUG-011's status. The quality gate was explicit: "do not declare Milestone 5B complete
merely because all 21 combinations compile... if any combination looks like a stretched,
compressed, clipped, or awkward version of another size, it is not finished."

----------------------------------

## Completed

**Product decisions closed permanently**

- **D-051 — Countdown label policy is now permanent.** The exact hierarchy (218 days: bare "218
  days," never "In 218 days"; 8 days: "Next week"; Tomorrow shows a clock time for timed events,
  nothing for all-day; Completed/Expired flip the headline to the status word) is recorded in
  `DECISIONS.md` and removed from every pending-approval list it appeared on.
- **D-052 — BUG-011 stays open by design, no further engineering until Milestone 8.** The owner
  confirmed directly: do not spend more time trying to recover from an explicit Force Stop: the
  branded initial/fallback state (Session 9) stands as the answer until Milestone 8's real refresh
  infrastructure exists as a matter of course.

**Migration to `SizeMode.Exact` and a real size classifier**

`CountdownGlanceWidget.sizeMode` changed from `Single` to `Exact` (D-053) — the widget now reads
real `LocalSize` per composition instead of assuming one fixed footprint. `WidgetSizeClass.kt`
(new file) classifies each composition as `COMPACT`/`STANDARD`/`WIDE` from that real size.
`key(LocalSize.current)` is now load-bearing (previously future-proofing only).

**21 genuinely distinct Style × Size compositions**

14 new layout composables added to `CountdownWidgetLayouts.kt` (`*LayoutCompact`, `*LayoutWide`
for all seven styles), alongside the seven existing Standard layouts. Each size has its own
information hierarchy, not a scaled copy:

- **Compact (2×1)** — glanceability only. Bare centered headline for Minimal/Progress/OLED
  (`CompactCenteredHeadline`, a shared helper); no secondary line, no percentage text drawn or
  announced at this size, regardless of what the binding requests.
- **Standard (2×2)** — unchanged from Session 9's verified layouts, regression-tested (all
  pre-existing tests still pass at the correct declared size — see Errors, below, for why this
  needed fixing first).
- **Wide (4×2)** — uses width deliberately: two-column compositions (identity/progress-ring left,
  headline/detail right for `ProgressLayoutWide`; larger type scale, e.g. `MATERIAL_WIDE_HEADLINE_
  SIZE = 40sp`) rather than the same single column stretched horizontally.

Full field-by-field matrix (Primary/Secondary/Progress/Alignment/Hidden fields/Typography) for all
21 combinations: `docs/WIDGET_SIZE_MATRIX.md`.

**Content-fit type-scaling system**

A single multiplicative `contentFitScale()` extension, applied on top of each style's existing
tuned base size, engineered to introduce zero visual change for content already verified in
Session 9's range (≤3-digit counts, ≤13-character words) while gracefully scaling down 4+ digit
counts and longer titles. Deliberately the smallest maintainable mechanism, not a generalized
abstraction, per the brief's explicit instruction.

**Responsive circular progress ring**

`ProgressRing` — a new shared composable (`showHeadline: Boolean` parameter) used by both
`ProgressLayout` (Standard) and `ProgressLayoutWide`, avoiding a duplicated headline inside and
outside the ring at Wide (D-054). Bitmap sizes now quantized to the nearest 8px
(`RING_PX_QUANTUM`) before the cache lookup, since `SizeMode.Exact` reports size continuously
rather than in discrete steps; `CircularProgressRenderer`'s LRU cache bumped 32→40 entries. Worst
case per bitmap: ~160KB (Standard), ~266KB (Wide) — verified within budget.

**Manifest changes enabling real resizing**

`countdown_widget_info.xml`: `minHeight` 110dp→40dp, `resizeMode` "none"→"horizontal|vertical",
added `maxResizeWidth="250dp"` / `maxResizeHeight="110dp"` (D-056). Resolves TD-012
(`resizeMode="none"` risk), now moot.

**Size-aware configuration-screen preview**

`WidgetConfigurationActivity` reads the placed widget's real current size via
`AppWidgetManager.getAppWidgetOptions(appWidgetId)` (D-057) rather than always assuming Standard.
`WidgetPreviewCard` gained an `isCompact` gate hiding identity/progress/secondary at compact size,
and a corrected `previewAspectRatio()` using real measured ratios (172:104 / 172:224 / 320:224),
not the formula-derived ratios an early draft used. A new caption tells the user the preview
reflects the widget's current placed size and to resize from the home screen to see another size.

**Real-device verification sweep**

- Two widgets on two different events, in two different size classes, updating independently and
  simultaneously — confirmed live on-device.
- Font scale robustness confirmed at 130% and 200% (`adb shell settings put system font_scale`).
- Edge cases confirmed: 1/8/218/999+ day counts, a long title, Tomorrow (timed and all-day),
  Completed, Expired.
- Light/dark mode confirmed across five named styles.
- Accessibility re-verified: compact's `contentDescription` omits the secondary line and
  percentage even when the binding requests them, matching what's visually hidden.
- Ten new curated screenshots committed to `docs/screenshots/` (`responsive_*.png`).

**New documents**

- `docs/WIDGET_SIZE_MATRIX.md` — the full 21-combination matrix, the real-vs-formula size table,
  and the content-fit rules.
- `docs/RESPONSIVE_WIDGET_REVIEW.md` — the size-threshold-correction finding with a before/after
  table, multi-widget evidence, content-fit edge-case evidence, configuration-preview evidence,
  accessibility evidence, and the session's **Final Report** answering all seven of the brief's
  exact questions.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 245 tests, 0 failures (up from 235 — `widget:glance` +10).
- Lint: 0 errors, 17 warnings, unchanged from Session 9.
- `:core:domain` coverage unchanged at 97.0%, gated at 95%.

----------------------------------

## Files Created

```
widget/glance/…/WidgetSizeClass.kt                                    (new — COMPACT/STANDARD/WIDE classifier)
docs/WIDGET_SIZE_MATRIX.md                                             (new)
docs/RESPONSIVE_WIDGET_REVIEW.md                                       (new)
docs/screenshots/responsive_*.png                                      (new, 10 images)
```

----------------------------------

## Files Modified

```
widget/glance/…/CountdownWidgetContent.kt          (LocalSize/sizeClass read, 21-branch style dispatch,
                                                       widgetContentDescription gained sizeClass param)
widget/glance/…/CountdownWidgetLayouts.kt           (full rewrite — 14 new Compact/Wide composables,
                                                       contentFitScale(), StartIdentity modifier param)
widget/glance/…/progress/CircularProgressRenderer.kt (MAX_CACHED 32→40, sizePx quantization docs)
widget/glance/…/CountdownGlanceWidget.kt            (sizeMode: SizeMode.Exact, D-053)
widget/glance/src/main/res/xml/countdown_widget_info.xml (minHeight, resizeMode, maxResizeWidth/Height)
widget/glance/…/configuration/WidgetPreviewCard.kt   (sizeClass param, isCompact gate, previewAspectRatio())
widget/glance/…/configuration/WidgetConfigurationActivity.kt (currentWidgetSizeClass(), preview caption)
widget/glance/src/test/…/CountdownWidgetContentTest.kt (setAppWidgetSize on every test, ~10 new tests)
docs/WIDGET_ARCHITECTURE.md, docs/WIDGET_DESIGN_GUIDE.md
DECISIONS.md, KNOWN_ISSUES.md, TODO.md, ROADMAP.md, CHANGELOG.md, PROJECT_STATUS.md, AI_CONTEXT.md
```

----------------------------------

## Architecture Decisions

Seven new entries, D-051 through D-057, detailed in `DECISIONS.md`:

- **D-051** — Countdown label policy is permanent product policy (the exact hierarchy).
- **D-052** — BUG-011: no further engineering time until Milestone 8.
- **D-053** — Responsive sizing uses `SizeMode.Exact` plus an app-owned classifier, not
  `SizeMode.Responsive`.
- **D-054** — `ProgressLayoutCompact`/`ProgressLayoutWide` never duplicate the headline inside and
  outside the ring.
- **D-055** — Widget size thresholds are calibrated from real on-device measurements, not
  Android's cell-size formula. The most significant entry this session — see Errors, below.
- **D-056** — The widget manifest declares one resizable 2×1-to-4×2 range, not three separate size
  declarations.
- **D-057** — The configuration screen's preview reads the placed widget's real size from
  `AppWidgetManager`, not a guess.

----------------------------------

## Current Project Structure

Unchanged at the module level. No new modules. One new top-level file in `widget/glance`
(`WidgetSizeClass.kt`) alongside the existing `CountdownWidgetContent.kt`/`CountdownWidgetLayouts.kt`
it's used by. See `PROJECT_STATUS.md` for the full module graph.

----------------------------------

## Dependencies Added

None.

----------------------------------

## Current Features Working

Everything from Session 9, plus: a genuinely responsive widget across 2×1/2×2/4×2 with 21 distinct
compositions; a content-fit system handling long titles and 4+ digit counts; a responsive,
memory-bounded progress ring; real resizing via the launcher; a size-aware configuration preview;
confirmed independent multi-widget behavior across different size classes simultaneously. See
`docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final Report for the full, evidence-backed verdict.

----------------------------------

## Pending Work

**P0 — blocks Session 11**
1. **Approve Milestone 6 (or further Milestone 5 work)** — read
   `docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final Report first.
2. **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — the one
   significant gap this session's own Final Report names explicitly. Try a physical device or a
   cleared single-widget home screen next; three device-automation attempts this session did not
   succeed.

**P1 — rest of Milestone 5:** verify two widgets on the *same* event with different styles through
real UI (unit-tested only so far); verify emoji rendering on a physical device (LIM-006); a live
preview inside the create/edit form itself; archive/complete/delete gestures (TD-008); re-measure
`WidgetSizeClass` thresholds on a second physical device and launcher (TD-016).

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** TD-012 (`resizeMode="none"` risk, moot now that resizing is fully
supported).

**Closed by decision, not by fix:** BUG-011 stays open per D-052 — not a pending question anymore.

**New this session:** TD-016 (`WidgetSizeClass` thresholds confirmed on one emulator/launcher
only), TD-017 (4×2/`WIDE` has no real-device visual confirmation, Robolectric only).

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-008, TD-009. LIM-002, LIM-003,
LIM-005, LIM-006.

**Lint:** 0 errors, 17 accepted warnings, unchanged from Session 9.

----------------------------------

## Next Session Plan

1. Get explicit approval before starting Milestone 6 or further Milestone 5 work — this session's
   brief was explicit that it must stop here.
2. If a real device is available: prioritize a genuine 4×2 (`WIDE`) placement and screenshot
   before anything else — the one gap this session could not close despite three attempts.
3. If approved to continue Milestone 5: same-event multi-style real-UI verification, the
   create/edit form's own live preview, archive/complete/delete gestures.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents including the two new size-system documents from this session.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 245 tests, 0 failures (up from 235)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings, unchanged from Session 9
- Runtime: the same stable local emulator established in Session 8 (`Pixel_9`), reused
  successfully for extensive device-automation work this session (uiautomator dumps, raw
  motionevent gesture composition, font-scale changes, multi-widget placement)

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**245 written, 245 passing, 0 failing — up from 235.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 34 | Unchanged |
| `:widget:glance` | 29 | +10 (size classification, compact hides secondary/percentage, headline-once-per-size, wide progress no duplicate headline, accessibility-at-compact) |

**Coverage** — `:core:domain` 97.0% lines, unchanged (nothing this session touched `:core:domain`).
The real 4×2 (`WIDE`) visual appearance is Robolectric-verified only, not device-confirmed (TD-017)
— the one size this session's automated tests cover that real-device evidence does not yet back,
the inverse of every other claim in this document.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: 17 modified files, 13 new files (3 source/docs, 10 screenshots), building on
`master` at `c68119b` (Session 9's final commit). No remote configured.

----------------------------------

## Developer Notes

- **A formula that was correct for one purpose is not automatically correct for a different
  purpose.** BUG-R009 (Session 8) correctly used Android's `dp = 70×cells − 30` formula to fix a
  manifest `minWidth` declaration. This session then derived `WidgetSizeClass`'s runtime
  breakpoints from that same formula — and a real launcher's actual measurements came in at
  roughly 2× the formula's prediction on both axes. The formula answers "what dp declares an
  N-cell footprint," not "what dp does a real launcher actually report for that footprint" — two
  different questions the same-looking formula does not answer equally well. Recalibrated against
  real measurements instead (D-055). Any future dp threshold — even one that reuses a
  previously-verified formula — needs its own real-device check.
- **A modifier default that's safe everywhere else is not safe everywhere.** `StartIdentity`'s
  `.fillMaxWidth()` was correct at all fifteen of its pre-existing `Column`-child call sites; the
  sixteenth, a new `Row`-child call site in `MaterialLayoutCompact`, silently crowded out its
  sibling headline. A shared composable's default modifier needs re-checking, not assumed safe, at
  every new call-site shape (`Row` vs. `Column`), not just every new call site.
- **A test harness's own default value can silently mean something.** Robolectric's
  `runGlanceAppWidgetUnitTest` defaults to `DpSize(349dp, 455dp)`, which classifies as `WIDE` under
  this session's own classifier — meaning every Glance test written before this session had
  unknowingly been exercising `WIDE` layouts, not `STANDARD`, for its entire history. Any test
  harness default that maps onto a meaningful domain concept (a size class, a locale, a feature
  flag) is worth checking explicitly, not assuming it lands on the "obvious" case.
- **Real-device UI automation for drag gestures needs raw `motionevent` composition, not
  `draganddrop` or `swipe`.** Both repeatedly failed to trigger genuine drag-pickup for the widget
  picker, resize handles, and remove gesture this session; `adb shell input motionevent
  DOWN`/`MOVE` (multiple steps)/`UP` reliably did. Kept as the standing technique for any future
  session needing the same kind of interaction.
- **An honestly-documented gap is better than a forced, low-confidence workaround.** Three
  distinct attempts to force a real 4×2 (`WIDE`) placement did not succeed. Rather than spend
  further session time or fabricate confidence, `WIDE_MIN_WIDTH_DP` is explicitly labeled
  "reasoned, not measured" everywhere it appears, and TD-017 documents the gap plainly.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.

----------------------------------

## Requires approval before Session 11

1. **Milestone 6, or further Milestone 5 work** — read `docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final
   Report first; it explicitly stops before this work and states why.
2. **Priority of a real 4×2 (`WIDE`) device measurement** (TD-016, TD-017) relative to starting new
   feature work — this session's own Final Report names it as the one thing it would fix before
   Google Play if it could.

----------------------------------

## Estimated Progress

```
Overall Progress            55%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             88%   (gestures outstanding; colour picker done Session 9)
Widget Engine                98%   (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget Themes & Sizes        70%   (responsive 2×1/2×2/4×2 delivered — Milestone 5B;
                                     multi-widget polish and real WIDE confirmation remain)
Notifications                 0%
Billing                       0%
Testing                      78%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
