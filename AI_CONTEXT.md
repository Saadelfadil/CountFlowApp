# AI_CONTEXT.md

**Read this first if you are an AI assistant starting cold on CountFlow.** It is a synthesis,
not a replacement — every claim here traces to a fuller document, and where the two disagree,
that fuller document wins. `ARCHITECTURE.md` is the one exception: it wins over everything,
including this file, except where a later `DECISIONS.md` entry explicitly supersedes it.

---

## What this is, in two sentences

CountFlow is an Android countdown-widget app: users create events, and the events render as
home-screen widgets. The app is lightweight; the widgets are the product — everything about the
architecture optimises for that, not for the app screens being impressive.

## The one thing to understand before touching any code

**The countdown engine and the widget engine are both pure Kotlin/JVM modules with zero Android
dependency, enforced by the build system, not by convention.** `:core:domain` and
`:widget:engine` apply `countflow.jvm.library`, not an Android library plugin. An accidental
`import android.*` in either is a compile error. This is the single structural decision that
makes the rest of the codebase trustworthy: business logic cannot leak into a screen or a widget
renderer because the type system will not let it compile there. If you are about to write
countdown arithmetic, a "which label applies" decision, or a "what does this look like" rule
inside a Composable — stop. It almost certainly belongs in `:core:domain` or `:widget:engine`
instead, and probably already exists there.

## Module graph, compressed

```
:app ──► every feature, every core module, :widget:glance

:feature:events, :feature:settings, :feature:premium ──► :core:designsystem, :core:domain, :core:common
:core:designsystem ──► :core:domain                         (D-028: token-to-text formatting)

:widget:glance ──► :widget:engine, :core:designsystem, :core:common
:widget:engine ──► :core:domain                              (D-033: pure Kotlin/JVM)

:core:data ──► :core:domain, :core:database, :core:common
:core:database ──► :core:domain, :core:common
:core:domain ──► nothing
```

Two modules are pure Kotlin/JVM (`:core:domain`, `:widget:engine`). Everything else is an
Android library or the app module. Full detail: `PROJECT_STATUS.md` § Module graph.

For anything inside `widget/`, read `docs/WIDGET_ARCHITECTURE.md` before touching code — it is
the single-file version of this document, scoped to the widget system, with real file paths and
function names for data flow, render flow, refresh flow, and both lifecycles. Read
`docs/PRODUCT_REVIEW.md` and `docs/SCREENSHOT_GUIDE.md` before assuming anything about the widget
is production-verified — as of Session 8 there is finally real, on-device, screenshotted evidence
(`docs/WIDGET_REVIEW.md`, Session 7, predates that and is largely superseded — see its own banner).

**If you need a device this session, check for a local one before assuming you need a remote
one.** Sessions 5–7 fought a flaky remote device at `127.0.0.1:6555` and Session 7 wrongly
concluded no local emulator existed — that conclusion came from `which emulator` failing (not on
`PATH`), not from checking `~/Library/Android/sdk/emulator/emulator` directly, which exists and
works, alongside an existing `Pixel_9` AVD. `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`
launched directly gave Session 8 a fully stable device for the whole session. Try this first.

## What exists right now (Milestone 4.9 of 9, complete)

- **Domain**: `Event`, `EventTarget` (the all-day/timed split — read its KDoc, it is the most
  important type in the app), `WidgetBinding`, `Reminder`, `CountdownEngine`, `EventValidator`,
  four repository interfaces.
- **Persistence**: Room (3 entities, cascading foreign keys, schema v1 committed), DataStore
  preferences, repository implementations — all integration-tested against real SQLite via
  Robolectric, not mocked.
- **App UI**: home list with search/sort/filter, create/edit form with validation. No delete or
  archive gesture yet (TD-008); no accent-colour picker; no widget preview.
- **Widget**: one 2×2 `CountdownGlanceWidget`, finished to production quality within that scope
  (accessibility content description, colors that resolve correctly against forced dark
  backgrounds, no dead configuration fields — see D-039, D-040), a configuration activity with a
  verified no-orphan-bindings guarantee, a theme resolver for all seven named styles, a progress
  engine, and a Milestone-4-scoped refresh scheduler (app-alive only — the real alarm-based
  strategy is Milestone 8, D-008).
- **Now real, confirmed Session 8**: the widget has been placed through the actual system picker
  and launcher, configured, updated live, survived an app update, and survived a full device
  reboot — all screenshotted (`docs/SCREENSHOT_GUIDE.md`). This closed TD-010 after three
  sessions of trouble. The same device access found a Critical bug invisible to every prior
  session: the widget's real footprint was 3×2, not the 2×2 everyone assumed, because
  `minWidth="180dp"` was the wrong value under Android's own cell-size formula — fixed (BUG-R009).
  Still not measured on any device, by any session: update latency, memory, CPU, battery, or
  TalkBack output — see `docs/PRODUCT_REVIEW.md` for what was prioritized instead and why.

`ROADMAP.md` has the milestone-by-milestone detail; `SESSION_SUMMARY.md` has what the *most
recent* session specifically did.

## The five things that will bite you if you don't know them

1. **A day count is a calendar comparison, never `duration / 86_400_000`.** Dividing gives the
   wrong answer roughly half the time across a DST boundary. Use
   `CountdownResult.calendarDaysRemaining`, not `totals.totalDays`, for anything a user reads as
   "N days away." (`CountdownEngineCalendarTest` documents every case where they diverge.)
2. **Never call `Instant.now()` or `LocalDate.now()` directly.** Inject `java.time.Clock`
   (provided in `:core:common`'s `TimeModule`). The entire test suite depends on time being a
   parameter; one direct call makes its caller untestable at exactly the boundaries this app
   cares about.
3. **The domain never returns display strings — only tokens.** `CountdownLabel`,
   `EventCategory`. Resolving them to text happens at composition
   (`core/designsystem/…/format/CountdownLabelFormatter.kt`), because resolving early freezes
   text in whatever locale was active when data last loaded.
4. **`GlanceAppWidget` cannot be Hilt-injected** (Glance's runtime instantiates it, not
   Android's). `provideGlance` reaches dependencies through a Hilt `EntryPoint`
   (`widget/glance/…/di/WidgetEntryPoint.kt`) — the one deliberate bridge point. Everything else
   in the widget layer, including the receiver and the configuration activity, is injected
   normally.
5. **Glance's `hasText` test matcher is always a substring match** (D-038) — its second param is
   `ignoreCase`, not `substring`. Use `hasTextEqualTo` for an exact match, or a loose assertion
   will pass when it shouldn't.

## Defects this codebase has already had, and how they were caught

Worth knowing because each is a *shape* of bug likely to recur elsewhere:

- An all-day event read as "starting soon" for its entire day, because the imminent-countdown
  threshold check didn't exclude all-day events (D-023, BUG in Session 3). Found by testing.
- The widget configuration activity crashed after a successful binding write, because forcing an
  immediate redraw threw when the widget id didn't resolve to a real `GlanceId` — stranding
  already-saved data instead of finishing gracefully (BUG-R005, Session 5). Found by device
  testing. The fix: a write that already succeeded must not be undone by an optional follow-up
  step failing.
- Two configuration fields (`WidgetTheme.isHighContrast`, `WidgetBinding.showPercentage`) were
  computed or persisted correctly for milestones but never actually read by the layer that would
  have shown their effect (BUG-R006, BUG-R007, Session 6). Found by re-reading the render model
  against the renderer, not by a failing test — nothing asserted the values were read at all,
  which is exactly the risk: a field with no consumer looks identical to a field that works,
  right up until someone checks. Worth deliberately auditing "does every field on a render model
  have a reader" when a milestone claims to be finished, not just "does every test pass."
- GLASS's translucent widget background could drop below WCAG AA text contrast over a light
  wallpaper — a case no unit test could ever catch, because the actual composited color depends on
  content (the user's wallpaper) that exists entirely outside the app and the test suite (BUG-R008,
  Session 7). Found by computing the real composited contrast from the color constants in the
  code, not by seeing it rendered. Worth remembering: any widget color that composites over
  something the app doesn't control (a launcher's wallpaper, not an app-drawn background) needs
  its worst case reasoned through explicitly — "it looked fine in the emulator" was never even
  available to check this, but wouldn't have been sufficient evidence anyway, since the emulator's
  one wallpaper isn't the worst case.
- **The widget's actual size was wrong for its entire history, and no amount of reading the code
  could have caught it.** `minWidth="180dp"` looked reasonable next to `targetCellWidth="2"` —
  until a real launcher's own widget picker reported the footprint as "3 × 2" (BUG-R009,
  Session 8). Android's own formula (`dp = 70×cells − 30`) makes `180dp` unambiguously the 3-cell
  value, but nothing in the code, the tests, or three sessions of documentation ever checked a dp
  value against that formula. The lesson: a manifest/XML value asserted to mean something specific
  (a cell count, a size class) needs checking against the platform's actual formula for it, not
  just against what a comment claims.
- **A library's public API surface is not the same as its runtime behavior.** Session 7 read
  Glance 1.1.1's `Text` API via `javap` and correctly found no overflow/ellipsis parameter, and
  concluded long titles clip with no ellipsis (TD-013). A real render showed the underlying
  `RemoteViews` `TextView` ellipsizes by default anyway (Session 8). The API reading wasn't wrong;
  it was incomplete evidence being treated as sufficient. Verify a rendering claim against one
  real render before writing it down as fact.

## How to verify the project still works

```
./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug
```

Current baseline: 223 tests, 0 failures, 0 lint errors (10 accepted warnings, all documented in
`KNOWN_ISSUES.md`), `:core:domain` line coverage above 95% (currently ~97%).

Build output is noisy — every module logs two deprecation warnings per compile, a side effect of
opting out of AGP 9's built-in Kotlin (TD-001, D-005). Filter with:

```
grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"
```

For anything touching the widget layer, `--no-build-cache` is worth using once after a resource
or manifest change — a Gradle build-cache staleness issue bit this project once already (TD-004).

## The working agreement

Read `SESSION_SUMMARY.md`'s most recent entry for exact standing instructions, but the pattern
has held across every session: architecture is proposed and approved before code, work proceeds
one milestone at a time with an explicit approval gate between them, and every session ends by
updating **all** of `SESSION_SUMMARY.md`, `PROJECT_STATUS.md`, `DECISIONS.md`, `ROADMAP.md`,
`CHANGELOG.md`, `KNOWN_ISSUES.md`, `TODO.md`, and this file. Do not start a new milestone without
checking `TODO.md`'s P0 section first — it is where unresolved cross-session questions live.

## Document map (full detail, one line each)

| File | What it actually contains |
|---|---|
| `ARCHITECTURE.md` | The original design proposal. Wins on any conflict. |
| `PROJECT_STATUS.md` | Permanent overview: module graph, tech stack, progress bars. |
| `SESSION_SUMMARY.md` | What the *most recent* session did, in narrative detail. |
| `DECISIONS.md` | Every decision (44 as of Session 8) with reason, alternatives, tradeoffs. |
| `docs/WIDGET_ARCHITECTURE.md` | The widget system in one file: data/render/refresh flow, both lifecycles, Glance's sharp edges, forward compatibility. |
| `docs/WIDGET_REVIEW.md` | The Milestone 4.5 audit (Session 7, no device — see its own banner; largely superseded by the two below). |
| `docs/PRODUCT_REVIEW.md` | The Milestone 4.9 product-quality verdict: ranked strengths/weaknesses, would-you-ship assessment, real device evidence. |
| `docs/SCREENSHOT_GUIDE.md` | Real, curated on-device screenshots of every major widget state, with the recipe to reproduce each. |
| `ROADMAP.md` | Milestone-by-milestone status and what each one delivered. |
| `KNOWN_ISSUES.md` | Open bugs (none), technical debt, platform limitations, resolved defects. |
| `TODO.md` | Prioritised outstanding work, P0 first. |
| `CHANGELOG.md` | Keep-a-Changelog-format release notes per milestone. |
