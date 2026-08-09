# CountFlow

## Session 8

Date: 2026-08-09
Current Milestone: **Milestone 4.9 — Real product validation (COMPLETE)**

> **READ THIS FIRST:** This session had a real, stable, self-controlled device for the first time
> in this project's history, and it changed the answer to several questions no amount of
> reasoning could settle. TD-010 (real widget placement) is closed after three sessions. A
> Critical bug was found and fixed: the widget's actual footprint had been 3×2, not the 2×2 every
> document since Milestone 4 claimed. 223 tests still pass, `:core:domain` unchanged at 97.0%. Do
> **not** start Milestone 5 without explicit approval, and read `docs/PRODUCT_REVIEW.md` before
> assuming the widget looks finished — it doesn't, yet, and the document says exactly why.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_ARCHITECTURE.md`, `docs/PRODUCT_REVIEW.md` (new — read before `docs/WIDGET_REVIEW.md`,
> which Session 7 wrote with no device and is now largely superseded), `docs/SCREENSHOT_GUIDE.md`
> (new), `PROJECT_STATUS.md`, `DECISIONS.md` (44 entries), then this file.
>
> Four items are open for Session 9 — see "Requires approval" at the end.

----------------------------------

## Objective

Milestone 4.9, explicitly a validation sprint, not a coding sprint: "pretend CountFlow is
shipping to Google Play tomorrow... find every reason not to ship. Be critical. Do not defend the
implementation." First priority, ahead of any code work: confirm device stability before relying
on one. Verify the widget lifecycle exhaustively (create, delete, update, reconfigure, reboot,
process death, app update, rotation, wallpaper change, theme change) on a real device or a stable
emulator. Review UX, accessibility, visual quality. Take real screenshots. Measure what can
honestly be measured; never invent a number. Rank every finding. Answer, plainly: would you ship
this today?

----------------------------------

## Completed

**Found a stable device — the actual first priority, done first**

Sessions 5–7 depended on a remote device at `127.0.0.1:6555` of steadily worsening reliability.
Session 7 concluded no local emulator existed on this machine, based on `which emulator` failing.
That conclusion was wrong: `~/Library/Android/sdk/emulator/emulator` exists directly (just not on
`PATH`), alongside an AVD (`Pixel_9`) already referenced in `PROJECT_STATUS.md` since Session 2.
Launched directly in GUI mode, it booted in seconds and stayed reachable for the entire session —
dozens of `adb` commands, zero reconnects, zero instability. This is now recorded prominently in
`AI_CONTEXT.md` and `PROJECT_STATUS.md` so no future session repeats Session 7's mistake.

**Real widget placement — TD-010 closed after three sessions**

- The widget listed correctly in the real Pixel Launcher's widget picker.
- Drag-to-place worked; the widget rendered and, via `configuration_optional`, showed the
  unconfigured "Tap to choose a countdown" state correctly before configuration.
- The real system configuration Activity opened, listed events, and bound correctly.
- The widget updated live across eleven direct rebind cycles (used to reach every countdown state
  and every named style without placing eleven separate widget instances — see the reproduction
  recipe in `docs/SCREENSHOT_GUIDE.md`).
- Reconfiguration (pointing an already-placed widget at a different event) worked through the
  real system flow.
- The widget **survived a full device reboot** with no manual intervention — first time ever
  tested, in any session. Android's platform-default post-boot widget refresh fired correctly,
  confirming `docs/WIDGET_ARCHITECTURE.md` §10's reasoning without needing a custom
  `ACTION_BOOT_COMPLETED` receiver.
- The widget survived an app reinstall (`adb install -r`, simulating a Play Store update) with no
  reconfiguration needed.
- Dark/light theme switching (`adb shell cmd uimode night no/yes`) produced a correctly re-themed
  widget in both directions, confirmed by screenshot.
- Rotation: the Pixel Launcher home screen does not rotate to landscape on this phone form
  factor — confirmed, not a widget-side gap.
- Widget deletion via the launcher's drag-to-remove gesture could not be triggered through `adb
  input` automation (every attempt was interpreted as a tap, opening configuration instead) — a
  tooling limitation, not new evidence about the app; `WidgetLifecycleCoordinator.onWidgetsRemoved`
  remains unit-tested only.
- GLASS's contrast fix (BUG-R008, Session 7) was confirmed via exact pixel sampling — RGB
  (16, 19, 24), distinct from OLED's RGB (0, 0, 0) and the dynamic surface's RGB (26, 27, 32) —
  but not screenshotted specifically over a light wallpaper; the wallpaper-swap and
  widget-repositioning gestures needed to force that exact framing were not reliable via `adb`
  automation in the time available.

**Two real defects found and fixed, verified on-device before and after**

- **BUG-R009 (Critical).** The widget's real footprint was 3×2, not 2×2. Android's own cell-size
  formula (`dp = 70×cells − 30`) makes `minWidth="180dp"` the 3-cell value; the real launcher's
  picker confirmed it directly, labeling the widget "3 × 2" before the fix and "2 × 2" after, with
  no other change. Fixed by correcting `minWidth` to `110dp`. See DECISIONS.md D-043.
- **BUG-R010.** Completed and expired events showed a full-strength, vivid progress bar next to an
  already muted label — found by looking at a real screenshot. Fixed by having the bar reuse the
  same `labelColor` the label already computes. See DECISIONS.md D-044.

**One real defect found, deliberately left open**

- **BUG-011 (High).** After `adb shell am force-stop com.countflow`, the widget sticks on a
  generic loading spinner and does not recover until the app is reopened by any means. Scoped
  precisely: confirmed against Force Stop specifically, which Android documents as more
  aggressive than ordinary background reclaim; this device's Play-Store system image could not be
  rooted (`adb root` refused) to test the gentler case for comparison. Not fixed this session by
  design — a real fix needs either Milestone 8's eventual refresh infrastructure or a new "tap to
  retry" affordance, both bigger than this session's allowed scope of small, self-contained fixes.

**One prior finding corrected**

- **TD-013.** Session 7 concluded from Glance's API surface (`javap` on the AAR) that long titles
  clip with no ellipsis. A real render disproved this: a 61-character title rendered as "A
  Genuinely Very Lon…" with a genuine ellipsis. The API reading was accurate as far as it went —
  there is still no explicit overflow parameter — but the underlying `RemoteViews` `TextView`
  apparently ellipsizes by default regardless. Marked corrected in `KNOWN_ISSUES.md`, not deleted,
  specifically as a reminder that API-surface reading isn't a substitute for one real render.

**Rigorous, pixel-level visual verification, not eyeballing**

All seven `WidgetStyle` values were bound and screenshotted; the six with no forced background
color needed pixel sampling (via a locally-installed Pillow) to tell apart accurately, since
compressed screenshots of very dark grays look nearly identical to the eye. Result: Minimal,
Material, Progress, and Modern resolved to the **exact same** background RGB (26, 27, 32) in every
state tested — only OLED (pure black, confirmed), Glass (measurably distinct translucent dark),
and Rounded (larger corner radius) actually look different. This is real, quantified evidence for
work `TODO.md` already had scheduled for Milestone 5, not a new discovery of scope — but it
materially strengthens the case for prioritizing it.

**New documents**

- `docs/PRODUCT_REVIEW.md` — the full critical assessment: strengths, weaknesses ranked
  Critical/High/Medium/Low, UX, visual quality, accessibility, performance, battery, store and
  launch readiness, and an overall verdict.
- `docs/SCREENSHOT_GUIDE.md` — thirteen real, curated, on-device screenshots (cropped from full
  captures, committed to `docs/screenshots/`, ~1.1MB total) covering Home Screen, Widget Added,
  Widget Empty, Widget Loading, Widget Config, Completed, Expired, Tomorrow, Next Week, Dark Mode,
  Light Mode, OLED, Material, and Glass, plus the exact SQL/adb recipe used to reach each state
  without placing a separate widget instance per state.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 223 tests, 0 failures — unchanged from Session 7; neither fix this session had an automatable
  regression test (BUG-R009 is a manifest XML value with no JVM-testable surface; BUG-R010 hit a
  real gap in Glance 1.1.1's testing API, which has no way to assert a resolved `ColorProvider`
  value — noted in `KNOWN_ISSUES.md` as a testing-capability gap, not worked around).
- Lint 0 errors, 10 accepted warnings, unchanged. `:core:domain` coverage unchanged at 97.0%.

----------------------------------

## Three problems found, and how each was scoped precisely

1. **The widget had been the wrong size since Milestone 4, and nothing short of a real widget
   picker could have caught it.** See BUG-R009 above. The mathematical reasoning (`70×cells−30`)
   is simple in hindsight; the actual catch was empirical — a real launcher's own UI reporting
   "3 × 2" — not a code review insight. Worth remembering for any future dp value asserted to
   correspond to a specific unit count.

2. **Force Stop leaves the widget stuck, and the device's own security model limited how
   precisely this could be characterized.** `am force-stop` reliably reproduces the stuck-spinner
   state; a gentler, more representative "ordinary background kill" could not be tested for
   comparison because this AVD uses a Play-Store (non-rootable) system image and `adb root` is
   refused on it ("cannot run as root in production builds"). The finding is real and correctly
   scoped to what was actually tested, not overstated to cover a case that couldn't be verified.

3. **A previously "confirmed via javap" finding turned out to be wrong.** See TD-013 above. The
   pattern worth generalizing: an API surface reading is evidence about what a library's authors
   expose, not proof of what the underlying platform does with it. This is now recorded as a
   general lesson in `AI_CONTEXT.md`'s defects list, not just a one-off correction.

----------------------------------

## Files Created

Two new documents, one new asset directory, no new modules or classes beyond the two small fixes.

```
docs/PRODUCT_REVIEW.md                                          (new)
docs/SCREENSHOT_GUIDE.md                                        (new)
docs/screenshots/*.png                                          (new, 13 images, ~1.1MB total)
```

Modified: `widget/glance/…/res/xml/countdown_widget_info.xml` (`minWidth` 180dp → 110dp),
`widget/glance/…/CountdownWidgetContent.kt` (progress bar color follows `labelColor`).

----------------------------------

## Architecture Decisions

Two new entries, D-043 and D-044, detailed in `DECISIONS.md`:

- **D-043 — Widget `minWidth` corrected from 180dp to 110dp**, so it actually agrees with the
  declared 2×2 `targetCellWidth`/`targetCellHeight`, verified empirically against a real
  launcher's own size label.
- **D-044 — The progress bar reuses the label's already-computed muted color** for
  completed/expired events, rather than staying at full accent strength regardless of state.

Both are framed explicitly as correcting real defects, not new design work — consistent with this
session's "no new features, only fixes required for production quality" scope.

----------------------------------

## Current Project Structure

Unchanged at the module level. Two production files touched (one XML, one Kotlin), both
one-line-scale fixes. See `PROJECT_STATUS.md` for the module graph.

----------------------------------

## Dependencies Added

None to the app. `pillow` was installed into the local Python environment (`pip3 install
--break-system-packages pillow`) purely as a development-time tool for pixel-sampling screenshots
and cropping the images now in `docs/screenshots/` — it is not a project dependency and does not
appear in any Gradle file.

----------------------------------

## Current Features Working

Everything from Session 7, plus: a widget genuinely placed, configured, and verified through the
real system flow for the first time; the widget confirmed to survive reboot and app update; two
visual defects fixed. See `docs/PRODUCT_REVIEW.md` "Strengths" for the full, honest account —
paired directly with "Weaknesses" in the same document, not separated into a rosier standalone
section here.

----------------------------------

## Pending Work

**P0 — blocks Session 9**
1. **Use the local emulator** (`~/Library/Android/sdk/emulator/emulator -avd Pixel_9`) — now a
   known-stable option, prefer it over the remote device.
2. **Approve starting Milestone 5** — informed by `docs/PRODUCT_REVIEW.md`'s ranked findings,
   two of which (style differentiation, missing picker preview) are exactly what Milestone 5
   already plans to address.
3. **Decide on BUG-011** — fix now with a small, scoped change, or accept it as a known gap until
   Milestone 8's refresh infrastructure naturally resolves it.
4. **Confirm the countdown label policy** — still unanswered since Session 3.

**P1 — Milestone 5:** style differentiation (now with pixel-verified evidence of exactly how
identical four styles currently are), a widget-picker preview image (TD-014), deliberate use of
the widget's vertical space (TD-015), system-matched corner radius (TD-011), circular progress
ring, additional sizes, and everything else already in `TODO.md`.

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Open, new this session:** BUG-011 (High — stuck loading spinner after Force Stop), TD-014
(Medium — no widget-picker preview), TD-015 (Medium — unused vertical space).

**Closed this session:** TD-010 (real widget placement, after three sessions), BUG-R009 (Critical
— wrong widget size), BUG-R010 (progress bar color inconsistency).

**Corrected this session:** TD-013 (title truncation does have an ellipsis; prior finding was
based on incomplete evidence, not wrong reasoning from what evidence existed).

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-008, TD-009, TD-011, TD-012.
LIM-001, LIM-003, LIM-004, LIM-006.

**Lint:** 0 errors, 10 accepted warnings.

----------------------------------

## Next Session Plan

1. Launch the local emulator directly; confirm stability before relying on it for anything else.
2. If Milestone 5 is approved: start with the two highest-leverage `docs/PRODUCT_REVIEW.md`
   findings — style differentiation and the widget-picker preview — both already scoped in
   `TODO.md` with concrete implementation notes (`android:previewLayout`, per-style layout work).
3. Attempt the GLASS-over-light-wallpaper screenshot this session didn't complete, if a spare
   cycle allows — a genuine wallpaper swap via the launcher's own picker UI, not `adb` automation,
   is likely more reliable given this session's gesture-automation difficulties.
4. Consider a scripted (not manual) device test for the widget lifecycle now that a stable local
   emulator is a known, repeatable resource — see the testing-gaps note in `TODO.md`.
5. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents including the two new ones from this session.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 223 tests, 0 failures — unchanged; neither fix this session had an automatable test surface
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 10 warnings, all previously accepted
- Runtime: **first fully stable device session** — local emulator, AVD `Pixel_9`, Android 16,
  Pixel Launcher. Real widget placement, reboot survival, app-update survival, theme-switch
  survival all confirmed by direct observation and screenshot.

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode, not
`-no-window`) rather than relying on any pre-existing remote connection.

----------------------------------

## Tests

**223 written, 223 passing, 0 failing — unchanged from Session 7.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 33 | Unchanged |
| `:widget:glance` | 8 | Unchanged |

**Coverage** — `:core:domain` 97.0% lines, unchanged. Both fixes this session were verified
on-device (`docs/SCREENSHOT_GUIDE.md`) rather than by automated test — one has no testable surface
(a manifest XML value), the other hit a real gap in Glance 1.1.1's testing API (no way to assert a
resolved `ColorProvider` value). Both gaps are documented in `TODO.md`, not silently accepted.

----------------------------------

## Git Status

Two commits this session, on `master`:

```
d8aa41c  fix(widget): correct 2x2 footprint and de-emphasize completed/expired progress
         docs: milestone 4.9 real product validation    ← this commit
```

Thirty-three commits total. No remote configured.

----------------------------------

## Developer Notes

- **Check for a local emulator before assuming you need a remote one.** `ls
  ~/Library/Android/sdk/emulator/emulator`, not `which emulator` — the latter only checks `PATH`
  and gave Session 7 a false negative.
- **A dp value asserted to mean a specific cell/unit count needs checking against the platform's
  actual formula for it**, not just against what a comment claims. `dp = 70×cells − 30` is
  Android's documented widget cell-size formula; BUG-R009 existed because `minWidth="180dp"` was
  never checked against it until a real launcher's picker exposed the mismatch directly.
- **An API surface reading is not proof of runtime behavior.** TD-013 was accurate as far as
  `javap` output went and still wrong about what actually renders. Verify against one real render
  before writing a rendering claim down as settled fact.
- **Any widget color that composites over content the app doesn't draw itself** (so far, only
  GLASS's translucent background over a launcher wallpaper) needs its worst case reasoned through
  explicitly — carried forward from Session 7, reconfirmed this session via pixel sampling rather
  than assumed correct.
- **Glance 1.1.1's `glance-testing` library cannot assert a resolved color value** — text,
  content-description, and testTag matchers only. Any future color-logic fix in the widget layer
  will hit the same gap BUG-R010's fix did; verify visually and say so plainly, don't fabricate
  test coverage that doesn't exist.
- **This device's Play-Store system image cannot be rooted** (`adb root` refused). Ordinary
  background process death cannot be precisely simulated here — only `am force-stop`, which
  Android documents as more aggressive. Scope any process-death finding to what was actually
  tested.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.

----------------------------------

## Requires approval before Session 9

1. **Milestone 5** — read `docs/PRODUCT_REVIEW.md`'s ranked findings first; two of its High items
   are already Milestone 5's planned work, now backed by real evidence instead of assumption.
2. **BUG-011** — fix now (small, scoped) or accept as a known gap pending Milestone 8.
3. **The countdown label policy**, still unanswered since Session 3.
4. **Whether to pursue a scripted device-lifecycle test**, now that a stable local emulator is a
   known, repeatable resource rather than an open question every session.

----------------------------------

## Estimated Progress

```
Overall Progress            49%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             85%   (gestures and colour picker outstanding)
Widget Engine                98%   (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget Themes & Sizes         0%
Notifications                 0%
Billing                       0%
Testing                      75%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
