# CountFlow

## Session 7

Date: 2026-08-09
Current Milestone: **Milestone 4.5 — Widget stabilization (COMPLETE)**

> **READ THIS FIRST:** This was an audit session, not a feature session — the brief was "treat
> CountFlow as if it were shipping tomorrow, no new features, only improve quality." No device
> was reachable at all this session. Everything below is a static architecture audit, a UX/
> contrast review done by computing real numbers from the code, and one genuine High-severity
> defect found and fixed that way (GLASS's contrast over light wallpapers). 223 tests pass,
> `:core:domain` unchanged at 97.0%. Do **not** start Milestone 5 without explicit approval, and
> read `docs/WIDGET_REVIEW.md` before assuming anything about the widget is device-verified —
> most of it still isn't, honestly and explicitly.
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `docs/WIDGET_ARCHITECTURE.md`, `docs/WIDGET_REVIEW.md` (new this session), `PROJECT_STATUS.md`,
> `DECISIONS.md` (42 entries), then this file.
>
> Three items are open for Session 8 — see "Requires approval" at the end. The first is now a
> device, not a decision.

----------------------------------

## Objective

Milestone 4.5, explicitly not Milestone 5: verify the widget lifecycle exhaustively, measure
performance, review accessibility/typography/spacing/Material 3/dynamic color, review resizing
behavior, test across multiple launchers, run a simplification pass over every Milestone 4 class,
audit the technical architecture (public APIs, naming, package structure, SOLID, dependency
graph, injection graph), and produce `docs/WIDGET_REVIEW.md` covering all of it plus a
production-readiness and risk assessment. No new sizes, styles, the circular renderer, Live
Updates, lockscreen, settings, premium, or notifications.

----------------------------------

## Completed

**Environment reality check, first** — the Session 6 emulator (`127.0.0.1:6555`) was unreachable
from the first command of this session onward: `Connection refused`, unchanged after `adb
kill-server`/`start-server`, and no local `emulator` binary or AVD exists to start a replacement.
This eliminated every device-dependent task in the brief (live lifecycle verification,
performance/memory/battery measurement, multi-launcher testing) before the session properly
started. Rather than skip those sections or fabricate plausible-sounding results, every one of
them is answered honestly in `docs/WIDGET_REVIEW.md` as "not verifiable this session," mapped to
whatever the *strongest actually-existing* evidence is instead (mostly Session 5's device work,
one milestone old).

**Technical/architecture audit** (`docs/WIDGET_REVIEW.md` §2)
- Re-traced the full dependency graph and injection graph by hand from every `build.gradle.kts`
  and Hilt module touching widgets: no cycle, no back-reference from `:widget:engine` or
  `:widget:glance` toward `:app` (confirmed by `grep`, zero hits).
- Read every class added in Milestone 4 against SOLID. All held up. One real finding: three
  `:widget:engine` types (`WidgetThemeResolver`, `WidgetProgressEngine`, `WidgetRenderMapper`)
  were `public` with no consumer outside the module. Tightened to `internal`, verified empirically
  (not just reasoned) — both the module's own test compilation and `:widget:glance`'s compilation
  still succeed unchanged. See D-042.

**Simplification pass** (`docs/WIDGET_REVIEW.md` §3)
- Reviewed every class added since Milestone 4 against "does this solve a problem that exists
  today." Nothing was found that should be removed structurally — the one real finding was excess
  *visibility* (above), not excess *structure*. Three specific extractions were considered and
  explicitly rejected (a color-resolution helper, a generalized color-palette abstraction, a
  formal typography/spacing object) with the reasoning kept in the review rather than silently
  discarded, since "considered and rejected" is exactly the kind of judgment call worth being
  able to check later.

**UX and accessibility review, done by computing real numbers** (`docs/WIDGET_REVIEW.md` §§4–5)
- Read every layout dimension, color constant, and string-length constraint against the review
  checklist in the brief (hierarchy, readability, contrast, progress visibility, emoji placement,
  title truncation, long titles, small widgets, dark/light wallpapers, OLED). One High-severity
  defect found this way — see "Three problems found" below. Two Medium/Low findings recorded as
  technical debt rather than fixed, since fixing them would have meant new engineering outside
  this session's explicit "no new features" scope (title ellipsis has no clean fix within Glance
  1.1.1's `Text` API at all; corner-radius system-matching needs an Android resource read plus a
  product decision on which styles should track it).
- Re-verified Glance 1.1.1's semantics API surface (only `contentDescription`/`testTag`, no
  `clearAndSetSemantics` equivalent) — unchanged from Session 6's finding, re-confirmed rather than
  assumed to still be true.

**Performance** (`docs/WIDGET_REVIEW.md` §6)
- Re-measured the pure-Kotlin compute path (`CountdownEngine.countdownAt` + `WidgetRenderMapper.map`,
  200,000 iterations, JIT-warmed): ~505ns/call, matching Session 6's number exactly — the mapper's
  logic didn't change in a way that would move it.
- Everything requiring a device (creation/update/refresh latency, memory, battery) is explicitly
  marked not measured, not guessed at.

**`docs/WIDGET_REVIEW.md` — new document**
- The full Milestone 4.5 audit record: architecture review, simplification pass, UX/accessibility
  findings with actual computed contrast ratios, performance, battery reasoning, a
  scenario-by-scenario lifecycle table naming the strongest real evidence for each of the eleven
  scenarios the brief asked about, technical debt opened this session, and an explicit "what this
  session could not verify" list rather than a vague disclaimer.

**Verification**
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 223 tests, 0 failures (1 new: GLASS's background alpha must stay at or above the contrast-safe
  floor). Lint 0 errors, 10 accepted warnings, unchanged.
- `:core:domain` coverage unchanged at 97.0% — untouched this session.

----------------------------------

## Three problems found, and one environment limitation

1. **GLASS's translucent background could fail WCAG AA contrast over a light wallpaper — a real,
   High-severity defect, found by computing the actual composited color, not by seeing it.**
   `TRANSLUCENT_DARK_SURFACE` was `0x99101418` (60% opaque). Composited over a fully light/white
   wallpaper — the one thing about a widget's background this app has never fully controlled,
   unlike every other style — that works out to roughly mid-gray, and the white text
   `ForcedBackgroundPalette.onSurface` draws over forced backgrounds measured at approximately
   4.9:1 contrast: barely above WCAG AA's 4.5:1 floor for normal text, with zero margin for a
   wallpaper any lighter or a panel any less accurate than assumed. Fixed by raising the alpha to
   `0xCC` (80% opaque; ~10.8:1 in the same worst case, past WCAG AAA). A regression test now
   asserts the alpha can't silently regress below the floor this reasoning depends on. BUG-R008,
   D-041.

2. **Three `:widget:engine` types had a wider public surface than any real caller needed.**
   Not a defect — nothing was broken — but a genuine finding under the session's explicit "review
   public APIs" task. `WidgetThemeResolver`, `WidgetProgressEngine`, and `WidgetRenderMapper` were
   all `public object`s consumed only by `WidgetRenderModelProvider`, itself inside the same
   module. Tightened to `internal`, verified by rebuilding rather than assumed correct. D-042.

3. **The environment problem, stated as plainly as the code problems.** No device was reachable
   at any point this session. This is not new in kind — Session 5 had a headless AVD that failed
   outright, Session 6 had an unstable GUI-mode device that disappeared mid-verification — but
   this session is the first with literally zero device access, which meant literally zero live
   verification of anything the brief asked for under "verify widget lifecycle completely,"
   "measure widget update latency," "measure widget memory usage," "measure battery impact," or
   "review widget on Pixel/Samsung/third-party launchers." `docs/WIDGET_REVIEW.md` §§10 and 12
   are the honest record of exactly what that leaves unconfirmed, scenario by scenario, rather
   than a single blanket disclaimer standing in for eleven different unanswered questions.

----------------------------------

## Files Created

One new file. No new modules, no new classes — this session's brief explicitly asked for fewer
moving parts, not more, and the one structural change (§below) is a visibility narrowing.

```
docs/WIDGET_REVIEW.md                                          (new)
```

Modified: `widget/engine/…/theme/WidgetThemeResolver.kt` (`internal`, alpha constant, new
documented floor constant), `widget/engine/…/progress/WidgetProgressEngine.kt` (`internal`),
`widget/engine/…/mapper/WidgetRenderMapper.kt` (`internal`),
`widget/engine/src/test/…/theme/WidgetThemeResolverTest.kt` (+1 test).

----------------------------------

## Architecture Decisions

Two new entries, D-041 and D-042, detailed in `DECISIONS.md`:

- **D-041 — GLASS's translucent background alpha raised from 0x99 to 0xCC**, with the reasoning
  (a wallpaper-composited worst case, computed explicitly) and the alternative considered and
  rejected (wallpaper-color sampling — real new engineering, out of scope for a stabilization
  milestone).
- **D-042 — `WidgetThemeResolver`, `WidgetProgressEngine`, `WidgetRenderMapper` are `internal`**,
  verified empirically rather than just reasoned about, closing a public-API-surface finding from
  this session's technical audit.

----------------------------------

## Current Project Structure

Unchanged at the module level — no new modules, no new top-level classes, three visibility
changes. See `PROJECT_STATUS.md` for the module graph; `docs/WIDGET_REVIEW.md` is the only new
permanent document this session, alongside `docs/WIDGET_ARCHITECTURE.md` from Session 6.

----------------------------------

## Dependencies Added

None.

----------------------------------

## Current Features Working

Unchanged from Session 6, with one contrast defect fixed (see "Three problems found" above). No
feature work occurred this session by design.

----------------------------------

## Pending Work

**P0 — blocks Session 8**
1. **Get a stable device before anything else** — the single biggest blocker now, ahead of any
   remaining code work, after three sessions of escalating device trouble (unavailable → unstable
   → fully unreachable).
2. **Verify real widget placement** (TD-010) once a device is confirmed stable.
3. **Approval to begin Milestone 5.**
4. **Confirm the countdown label policy** — still unanswered since Session 3.

**P1 — Milestone 5:** everything from Session 6's plan, plus TD-011 (system-matched corner
radius, opened this session) and wiring `showDate`/`target`/`targetZone` (identified this session
as a dead-field gap like `showPercentage` was, but deliberately not fixed — it needs new
date-formatting logic this session's scope excluded).

Full breakdown in `TODO.md`.

----------------------------------

## Known Issues

No open runtime bugs as of this session. Full detail in `KNOWN_ISSUES.md`.

**Closed this session:** BUG-R008 (GLASS contrast over light wallpapers).

**Opened this session:** TD-011 (corner radius not system-matched), TD-012 (no adaptive fallback
if a launcher ignores `resizeMode="none"`), TD-013 (title truncation has no ellipsis) — all Medium
or Low, all documented with why they weren't fixed immediately.

**Updated this session:** TD-010 — no new evidence either way; explicitly noted that this
session's total lack of device access means the Session 6 finding (permission problem resolved,
stability problem open) remains the most recent signal.

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-008, TD-009. LIM-001, LIM-003,
LIM-004, LIM-006.

**Lint:** 0 errors, 10 accepted warnings.

----------------------------------

## Next Session Plan

**Step 0 is a gate, and it's a device, not a decision this time.** Confirm `adb devices -l` shows
a device, then `appwidget grantbind` and `dumpsys user` both succeed, then confirm the connection
survives several minutes idle — *before* relying on it for anything else this session.

1. Real widget placement end to end: drag from the picker, confirm render, edit the event and
   confirm the widget updates, remove and confirm binding cleanup, reconfigure and confirm the
   binding changes to the new event. Screenshot every step this time.
2. If time and device stability allow: a first rough widget update-latency and memory measurement
   — no session has produced either number yet, and Milestone 5 will be easier to scope
   accurately with at least one real data point instead of zero.
3. Once TD-010 is closed: approval to begin Milestone 5 — circular progress ring, per-style
   layout differentiation, additional sizes, TD-011's corner-radius fix, the settings surface for
   the visibility flags, accent-colour picker, live widget preview, list gestures (TD-008).
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents (`AI_CONTEXT.md`, `docs/WIDGET_ARCHITECTURE.md`, `docs/WIDGET_REVIEW.md` included).

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 223 tests, 0 failures — 1 new this session (`:widget:engine`)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 10 warnings, all previously accepted
- Compute-path performance re-measured (not device-profiled): ~505ns/call, unchanged from Session 6
- Runtime: **no device reachable at any point this session** — see "Three problems found"

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

**223 written, 223 passing, 0 failing.**

| Module | Tests | What changed this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged |
| `:core:database` | 38 | Unchanged |
| `:core:data` | 31 | Unchanged |
| `:feature:events` | 22 | Unchanged |
| `:widget:engine` | 33 | +1: GLASS's resolved background alpha stays at or above the contrast-safe floor |
| `:widget:glance` | 8 | Unchanged |

**Coverage** — `:core:domain` 97.0% lines, unchanged; this session did not touch that module.

----------------------------------

## Git Status

Two commits this session, on `master`:

```
ec79e90  fix(widget): raise GLASS contrast floor, tighten engine internals
         docs: milestone 4.5 widget stabilization review    ← this commit
```

Thirty-one commits total. No remote configured.

----------------------------------

## Developer Notes

- **GLASS is the one style whose background this app doesn't fully control.** Any future change
  to its alpha, or a new style that also forces a translucent background, needs the same
  worst-case contrast reasoning D-041 did — check `WidgetThemeResolver.MIN_ALPHA_FOR_RELIABLE_CONTRAST`
  and the regression test guarding it before touching that constant.
- **`WidgetThemeResolver`, `WidgetProgressEngine`, `WidgetRenderMapper` are `internal` now.** If a
  future change in `:widget:glance` seems to need one of them directly, that's very likely a sign
  the orchestration belongs in `WidgetRenderModelProvider` instead, not a reason to widen the
  visibility back.
- **Three sessions of device trouble is now a pattern, not a one-off.** If a device is available
  at the start of a session, verify it stays reachable *before* planning the rest of the session
  around it — don't discover instability partway through, the way Session 6 did.
- **`WidgetRenderModel.target`/`.targetZone`/`.showDate` are the next dead-field gap**, same shape
  as `isHighContrast`/`showPercentage` were — but wiring it needs actual new date-formatting logic
  (no `DateTimeFormatter` exists anywhere in the codebase yet), which is why it wasn't fixed this
  session the way the two simpler gaps were last session.
- **Build output is noisy** (TD-005). Filter with
  `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"`.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`.

----------------------------------

## Requires approval before Session 8

1. **A stable device** — not really an approval, but the practical precondition for everything
   else; flagged here because it's now blocked three sessions running.
2. **Milestone 5** — the widget has now had a finishing pass (Session 6) and a stabilization audit
   (Session 7); `docs/WIDGET_REVIEW.md`'s production-readiness read is the thing to weigh before
   saying yes.
3. **The countdown label policy**, still unanswered since Session 3.

----------------------------------

## Estimated Progress

```
Overall Progress            48%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             85%   (gestures and colour picker outstanding)
Widget Engine                96%   (audited and stabilized; real launcher placement unverified — TD-010)
Widget Themes & Sizes         0%
Notifications                 0%
Billing                       0%
Testing                      75%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
