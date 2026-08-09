# CountFlow

## Session 9

Date: 2026-08-09
Current Milestone: **Milestone 5A — Visual redesign of the existing 2×2 widget (COMPLETE)**

> **READ THIS FIRST:** This session redesigned the one 2×2 widget that already existed —
> deliberately **not** a new size, not multiple widgets, not Live Updates, not lockscreen, not
> billing. Seven named styles now have genuinely different layout philosophies (not just
> different colors), the two content-redundancy bugs the brief named by example are closed, the
> first working determinate circular progress ring in this project's history now renders on a
> real device, the widget-picker preview the brief called "mandatory" is done, and the
> configuration screen has a live, no-save-required preview. The session's own Final Report
> (`docs/WIDGET_DESIGN_REVIEW.md`) answers **YES** to "would this look professionally designed
> beside Google's own widgets" — with the honest caveats stated in that report, not hidden. BUG-011
> (stuck loading spinner after Force Stop) is **not** fixed — only its initial-state layout is now
> branded; the underlying recovery gap is left open on purpose, per instruction not to defeat
> Android's force-stop semantics.
>
> **Do not start 2×1/4×2 sizes or any other Milestone 5 work without explicit approval.** This
> session stopped exactly where its brief said to stop.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_ARCHITECTURE.md`, `docs/WIDGET_DESIGN_GUIDE.md` (new — per-style design philosophy),
> `docs/WIDGET_DESIGN_REVIEW.md` (new — before/after evidence and the Final Report verdict),
> `docs/PRODUCT_REVIEW.md`, `docs/SCREENSHOT_GUIDE.md`, `DECISIONS.md` (50 entries), then this file.
>
> Three items are open for Session 10 — see "Requires approval" at the end.

----------------------------------

## Objective

Milestone 5A, explicitly scoped narrower than the rest of Milestone 5: make the existing 2×2
widget "beautiful enough that a user sees it in a screenshot and immediately wants it on their
home screen," per the brief's own words — not by introducing new architecture, but by fixing eight
named design problems (unused vertical space, near-identical styles, incoherent hierarchy,
"Tomorrow/Tomorrow" and "7/Next week" redundancy, progress bars that feel bolted on, no picker
preview, no configuration preview). Seven styles each needed a genuinely different layout
philosophy. Target date rendering had to be wired for the first time. Circular progress had to
actually work. TD-011 (corner radius) needed a real decision. BUG-011 needed honest investigation,
not a fragile workaround. Real device screenshots were required as the source of truth throughout
— "do not declare the milestone complete merely because the code builds."

----------------------------------

## Completed

**All seven `WidgetStyle` values now have genuinely different layouts, verified on a real device**

- **Minimal** — typography-first, no progress bar at all, the largest centered type of any
  "no-opinion" style (46sp).
- **Material** — the safe, balanced default; left-aligned (was centered); headline and unit
  inline on one row; shows everything the model can show.
- **Progress** — a real determinate circular ring (see below) now fills most of the card; the
  headline sits inside it. Previously a bare number, pixel-identical to three other styles.
- **OLED** — true black (confirmed `(0, 0, 0)`), no identity row at all, the largest type of any
  style (50sp) — the starkest possible read.
- **Glass** — normal type weight (not bold), more generous spacing, deliberately lighter than
  Material while keeping D-041's WCAG-checked contrast floor untouched.
- **Rounded** — 28dp radius (largest, unchanged) plus a new structural element: the secondary
  line now sits in a pill-shaped chip, not bare on the background.
- **Modern** — top-left anchored like a masthead (every other style is centered), the densest
  stack of any style (title, number, unit, secondary, date, percentage, bar all at once).

Full per-style philosophy, hierarchy, and "when to choose it" reasoning: `docs/WIDGET_DESIGN_GUIDE.md`.
Before/after screenshots and the pixel-level evidence behind each claim above:
`docs/WIDGET_DESIGN_REVIEW.md`.

**Content hierarchy fixed at the type level, not per-fixture**

A new `WidgetHeadline` model (`CountdownWidgetContent.resolveHeadline`) is computed once, before
any style renders, and decides what the headline (`primary`), its unit caption, and its
supporting line (`secondary`) should say:

- **"Tomorrow / Tomorrow" cannot recur.** A near-term countdown's `secondary` is now the event's
  clock time (if timed) or nothing (if all-day) — never the label word restated.
- **"7 / Next week" now only appears when it adds real information.** `secondary` is populated
  for `CountdownLabel.NextWeek` specifically (it names *which* calendar week, a fact the number
  doesn't state); every other in-range count, whose label restates the number, correctly shows no
  second line.
- Completed/expired events flip `primary` to the status word and demote the title into
  `secondary`, suppressing the identity row so nothing is drawn twice.

**Target date rendering wired for the first time**

New `TargetDateFormatter` (`core/designsystem`), locale-aware via
`java.time.format.DateTimeFormatter.ofLocalizedTime/ofLocalizedDate`, no hardcoded English. Drawn
in Material and Modern when `showDate` is enabled.

**Determinate circular progress — a first for this project (LIM-001 closed)**

`CircularProgressRenderer`: `Canvas`/`Paint.Style.STROKE` drawn to a `Bitmap`, quantized to whole
`percent`, cached in a 32-entry LRU (`LinkedHashMap`, access-order). Worst case ≈7.2MB,
comfortably inside the `6 × screenWidthPx × screenHeightPx` budget (LIM-003). Verified rendering
correctly on the real emulator — `docs/screenshots/after_progress.png`.

**TD-011 resolved: corner radius is per-style, not one hand-picked constant**

`WidgetTheme.cornerRadiusDp` is now `Int?`. Four styles (Minimal, Material, Progress, OLED) track
`android.R.dimen.system_app_widget_background_radius`; three (Glass 20dp, Rounded 28dp, Modern
8dp) keep a deliberate fixed override, each because the override is part of that style's design.
See DECISIONS.md D-045.

**TD-014 resolved: the widget-picker preview, called "mandatory" in the brief**

`android:previewLayout` (a plain Android XML layout, `res/layout/widget_preview.xml`, over
`res/drawable/widget_preview_background.xml`) replaces the old blank-icon placeholder. Confirmed
on-device: expanding CountFlow's entry in the Pixel Launcher widget tray now shows a realistic
"✈️ Trip to Kyoto / 7 days / Next week" card with a progress bar —
`docs/screenshots/after_widget_picker.png`.

**TD-015 resolved as a side effect of the redesign, not a separate pass**

Larger type scales (46–50sp for the styles that had no other differentiator) and genuinely
space-filling elements (the circular ring, Modern's dense stack) close the "roughly a third of
the card is empty" finding from Session 8.

**Configuration screen upgraded to a two-step, live-preview flow**

Step one (pick an event) unchanged; step two is new — style, progress-style, and four show/hide
toggles as chips/switches, an accent-colour override, and a live `WidgetPreviewCard` that redraws
on every change via a new `WidgetRenderModelProvider.preview(event, binding)` — pure, synchronous,
no database write, preserving the existing no-orphan-bindings guarantee (D-048). Verified
interactively on-device: selecting OLED instantly turned the preview card pure black —
`docs/screenshots/after_config_preview.png`.

**Accent-colour picker delivered (deferred since Milestone 3)**

`AccentColorPicker` (`core/designsystem`): one Dynamic Material You swatch (a plain "A" text
glyph — `material-icons-extended` is deliberately excluded from this project) plus eight curated
presets, deliberately not a free-form RGB picker. Wired into both the create/edit form and the
configuration screen's per-widget override. See DECISIONS.md D-050.

**BUG-011 investigated honestly, partially addressed, deliberately left open**

Glance's generic loading spinner is replaced with a branded `res/layout/widget_initial_layout.xml`
("CountFlow / Tap to refresh"). This changes what a stuck widget communicates; it does not fix the
underlying gap — Android cancels scheduled work on Force Stop by design, and defeating that was
explicitly out of scope. `KNOWN_ISSUES.md` BUG-011 remains open, severity unchanged.

**One real bug found and fixed within the session it was introduced**

BUG-R011: `MINIMAL_HEADLINE_SIZE` (and its siblings) were tuned for a 1–3 digit day count; the
first on-device screenshot of a completed event showed "Completed" wrapped mid-word into
"Compl"/"eted" (Glance has no autosizing text, LIM-004). Fixed via `WidgetHeadline.isNumeric` and
a per-style `headlineSize()` selector, plus `maxLines = 1` everywhere so anything still too long
ellipsizes cleanly. Verified fixed via re-screenshot; regression-tested for the classification
logic (the visual wrapping itself isn't observable through Glance's testing API).

**Two lint warnings fixed during the session, not left as debt**

`UseKtx` (`CircularProgressRenderer`, `Bitmap.createBitmap` → the `androidx.core.graphics`
extension) and `LocalContextResourcesRead` ×2 (`WidgetPreviewCard`, now reads
`LocalResources.current` instead of `LocalContext.current.resources`).

**New documents**

- `docs/WIDGET_DESIGN_GUIDE.md` — design philosophy, information hierarchy, and "when to choose
  it" for all seven styles, plus the corner-radius, picker-preview, configuration-preview, accent
  color, and BUG-011 decisions.
- `docs/WIDGET_DESIGN_REVIEW.md` — before/after screenshots for every style (backed by pixel
  sampling where color is the relevant claim), the content-hierarchy fixes, the word-wrap bug
  found-and-fixed, the picker and configuration previews, light/dark mode, and the session's
  **Final Report** answering the brief's explicit YES/NO question.
- Fourteen new curated screenshots in `docs/screenshots/` (`after_*.png`), captured on the same
  stable local emulator Session 8 established.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 235 tests, 0 failures (up from 223 — `widget:engine` +1, `widget:glance` +11).
- Lint: 0 errors, 17 warnings (10 pre-existing + 7 new `HardcodedText`, all in the two new plain
  Android XML layouts and documented as expected — see `KNOWN_ISSUES.md`).
- `:core:domain` coverage unchanged at 97.0%, gated at 95%.

----------------------------------

## Files Created

```
core/designsystem/…/format/TargetDateFormatter.kt                    (new)
core/designsystem/…/component/AccentColorPicker.kt                   (new)
widget/glance/…/CountdownWidgetLayouts.kt                             (new — the 7 style layouts)
widget/glance/…/progress/CircularProgressRenderer.kt                  (new)
widget/glance/…/configuration/WidgetPreviewCard.kt                    (new)
widget/glance/src/main/res/drawable/widget_preview_background.xml     (new)
widget/glance/src/main/res/layout/widget_preview.xml                  (new)
widget/glance/src/main/res/layout/widget_initial_layout.xml           (new)
docs/WIDGET_DESIGN_GUIDE.md                                            (new)
docs/WIDGET_DESIGN_REVIEW.md                                           (new)
docs/screenshots/after_*.png                                          (new, 14 images)
```

----------------------------------

## Files Modified

```
widget/glance/…/CountdownWidgetContent.kt                 (full rewrite — WidgetColors, WidgetHeadline,
                                                             resolveHeadline, style dispatch)
widget/engine/…/model/WidgetTheme.kt                       (cornerRadiusDp: Int → Int?)
widget/engine/…/theme/WidgetThemeResolver.kt                (per-style corner radius, new MODERN case)
widget/engine/…/provider/WidgetRenderModelProvider.kt        (added preview())
widget/glance/src/main/res/xml/countdown_widget_info.xml    (initialLayout, previewLayout)
widget/engine/src/test/…/WidgetThemeResolverTest.kt          (nullable-radius tests)
widget/glance/src/test/…/CountdownWidgetContentTest.kt        (~10 new tests, style/hierarchy coverage)
feature/events/…/edit/EditEventUiState.kt                    (+ accentColor)
feature/events/…/edit/EditEventViewModel.kt                   (+ onAccentColorChange)
feature/events/…/edit/CreateEventScreen.kt                    (+ AccentColorPicker section)
widget/glance/…/configuration/WidgetConfigurationUiState.kt   (full rewrite — two-step state)
widget/glance/…/configuration/WidgetConfigurationViewModel.kt (full rewrite — two-step flow)
widget/glance/…/configuration/WidgetConfigurationActivity.kt  (full rewrite — customize step UI)
core/designsystem/src/main/res/values/strings.xml             (+ countdown_day_unit plural)
KNOWN_ISSUES.md, DECISIONS.md, TODO.md, ROADMAP.md, CHANGELOG.md, PROJECT_STATUS.md, AI_CONTEXT.md
```

----------------------------------

## Architecture Decisions

Six new entries, D-045 through D-050, detailed in `DECISIONS.md`:

- **D-045** — Corner radius is per-style: four styles track the system value, three
  (Glass/Rounded/Modern) keep a deliberate fixed override.
- **D-046** — Countdown content hierarchy: a shared `WidgetHeadline` model, computed once, before
  any style renders.
- **D-047** — Determinate circular progress via a cached `Canvas`/`Bitmap` renderer, quantized to
  whole percent.
- **D-048** — `WidgetRenderModelProvider.preview()`: a pure, no-I/O render path for the
  configuration screen's live preview.
- **D-049** — The configuration screen's live preview is a simplified plain-Compose card, not a
  pixel-identical Glance reproduction.
- **D-050** — Accent color picker: Dynamic Material You plus eight curated presets, no free-form
  RGB picker.

All six are framed as scoped design decisions with named alternatives and tradeoffs, consistent
with every prior entry in this file.

----------------------------------

## Current Project Structure

Unchanged at the module level. No new modules. Two new files in `widget/glance`'s existing
`progress/` and `configuration/` sub-packages (both already existed); one new top-level file
(`CountdownWidgetLayouts.kt`) alongside the existing `CountdownWidgetContent.kt` it's dispatched
from. See `PROJECT_STATUS.md` for the full module graph.

----------------------------------

## Dependencies Added

None. `androidx.core.graphics.createBitmap` (used to fix a lint warning) comes from
`androidx-core-ktx`, already a `widget:glance` dependency before this session.

----------------------------------

## Current Features Working

Everything from Session 8, plus: seven genuinely distinct widget styles verified on-device; a
working determinate circular progress ring (first in this project's history); locale-aware target
date rendering; a widget-picker preview; a two-step configuration screen with a live, no-save
preview; an accent-colour picker in both the create/edit form and the configuration screen; a
branded (not fixed) initial-loading state. See `docs/WIDGET_DESIGN_REVIEW.md`'s Final Report for
the full, evidence-backed verdict on whether this now reads as a professionally designed product.

----------------------------------

## Pending Work

**P0 — blocks Session 10**
1. **Approve starting 2×1/4×2 size work** (the rest of Milestone 5) — read
   `docs/WIDGET_DESIGN_REVIEW.md`'s Final Report first.
2. **Decide on BUG-011** — still open; a real fix needs Milestone 8's refresh infrastructure or a
   deliberate "tap to retry" affordance.
3. **Confirm the countdown label policy** — unanswered since Session 3, now more visible with
   seven distinct rendering styles.

**P1 — rest of Milestone 5:** 2×1/4×2 sizes with `SizeMode.Exact`, verifying two widgets on the
same event with different styles through real UI, emoji rendering on a physical device (LIM-006),
a live preview inside the create/edit form itself (the configuration screen has one; the form
doesn't), archive/complete/delete gestures (TD-008).

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** TD-011 (corner radius), TD-014 (widget-picker preview), TD-015 (unused
vertical space), BUG-R011 (word-wrap, found and fixed same session).

**Partially addressed, deliberately left open:** BUG-011 (branded initial layout; underlying
force-stop recovery gap unchanged by design).

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-008, TD-009, TD-012. LIM-002,
LIM-003, LIM-005, LIM-006. (LIM-001 closed this session — see Completed.)

**Lint:** 0 errors, 17 accepted warnings (10 pre-existing + 7 new, all documented).

----------------------------------

## Next Session Plan

1. Get explicit approval before starting any 2×1/4×2 or multi-widget work — this session's brief
   was explicit that it must stop here.
2. If approved: start with `SizeMode.Exact` and breakpoint ranges for the two new sizes, reusing
   the now-genuinely-distinct per-style layouts rather than redesigning them again per size.
3. Decide on BUG-011's priority relative to Milestone 8's refresh infrastructure.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents including the two new widget-design ones from this session.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 235 tests, 0 failures (up from 223)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings (10 pre-existing + 7 new `HardcodedText`, both documented as
  expected in `KNOWN_ISSUES.md`); two other warnings introduced mid-session (`UseKtx`,
  `LocalContextResourcesRead` ×2) were fixed, not left as debt
- Runtime: same stable local emulator Session 8 established (`Pixel_9`), reused successfully for
  the whole session — fast boot, zero reconnects, full session stability confirmed a second time

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**235 written, 235 passing, 0 failing — up from 223.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 34 | +1 (nullable corner-radius tests) |
| `:widget:glance` | 19 | +11 (style/hierarchy coverage, `isNumeric` regression tests) |

**Coverage** — `:core:domain` 97.0% lines, unchanged (nothing this session touched `:core:domain`).
The visual wrapping fix (BUG-R011) and all seven styles' exact on-screen appearance are verified
on-device (`docs/WIDGET_DESIGN_REVIEW.md`), not by automated test — Glance's Robolectric-based
testing API cannot observe actual text wrapping or resolved colors, a previously-documented gap
(BUG-R010) this session hit again in a new place.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: 20 modified files, 24 new files (10 source/resource, 2 docs, 14 screenshots),
building on `master` at `7ab27bd` (Session 8's final commit). Thirty-four commits total before
this session's. No remote configured.

----------------------------------

## Developer Notes

- **A font size tuned for one content shape doesn't generalize to another, and Glance cannot
  autosize to catch the gap.** BUG-R011 existed because a headline size tuned for a 1–3 digit
  count was applied unconditionally to word-shaped headlines too. Any future fixed-`sp` constant
  applied to more than one content shape needs the same `isNumeric`-style check.
- **A live preview that must never risk an orphan write needs a pure, no-I/O render path of its
  own**, not a shortcut through the real write-then-read path. `WidgetRenderModelProvider.preview()`
  exists specifically so the configuration screen's new customize step could redraw instantly
  without reopening the orphan-binding risk Milestone 4 was built to close (D-048).
- **`android:previewLayout` and `android:initialLayout` are both plain Android XML, not Glance** —
  Glance cannot render a live composition into either the widget picker or the pre-first-render
  state. Any future "what does this look like before real data exists" surface needs the same
  non-Glance XML approach.
- **Five styles sharing one background color is not evidence of insufficient differentiation** —
  it was Session 8's finding when combined with identical *layout* too. Once layout, type scale,
  alignment, and progress presentation all differ, shared background color for styles with no
  design reason to override it is the deliberate, correct outcome, not a residual gap.
  `docs/WIDGET_DESIGN_REVIEW.md` states this plainly rather than presenting a color table that
  would otherwise mislead a skimming reader.
- **Investigate honestly before declaring a bug fixed.** BUG-011 could have been marked "closed"
  by only fixing what's visible (the spinner). It wasn't, because the underlying recovery gap
  genuinely requires either new infrastructure or defeating platform semantics — both correctly
  out of scope this session.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.

----------------------------------

## Requires approval before Session 10

1. **2×1/4×2 sizes and the rest of Milestone 5** — read `docs/WIDGET_DESIGN_REVIEW.md`'s Final
   Report first; it explicitly stops before this work and states why.
2. **BUG-011** — pull a "tap to retry" fix forward, or continue waiting for Milestone 8's refresh
   infrastructure to resolve it as a side effect.
3. **The countdown label policy**, still unanswered since Session 3.

----------------------------------

## Estimated Progress

```
Overall Progress            52%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             88%   (gestures outstanding; colour picker now done)
Widget Engine                98%   (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget Themes & Sizes        35%   (2×2 visually redesigned and verified — Milestone 5A;
                                     sizes/multi-widget remain)
Notifications                 0%
Billing                       0%
Testing                      76%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
