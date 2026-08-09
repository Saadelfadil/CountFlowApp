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

## What exists right now (Milestone 4 of 9, complete)

- **Domain**: `Event`, `EventTarget` (the all-day/timed split — read its KDoc, it is the most
  important type in the app), `WidgetBinding`, `Reminder`, `CountdownEngine`, `EventValidator`,
  four repository interfaces.
- **Persistence**: Room (3 entities, cascading foreign keys, schema v1 committed), DataStore
  preferences, repository implementations — all integration-tested against real SQLite via
  Robolectric, not mocked.
- **App UI**: home list with search/sort/filter, create/edit form with validation. No delete or
  archive gesture yet (TD-008); no accent-colour picker; no widget preview.
- **Widget**: one 2×2 `CountdownGlanceWidget`, a configuration activity with a verified
  no-orphan-bindings guarantee, a theme resolver for all seven named styles, a progress engine,
  and a Milestone-4-scoped refresh scheduler (app-alive only — the real alarm-based strategy is
  Milestone 8, D-008).
- **Not yet real**: a widget has never been placed through an actual launcher/`AppWidgetHost`
  flow. Verified instead by launching the configuration activity directly and inspecting the
  database. See `KNOWN_ISSUES.md` TD-010 before trusting that a real placement definitely works.

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

## Two defects this codebase has already had, and how they were caught

Both were found by testing, not by review — worth knowing because the same *shape* of bug is
the one most likely to recur:

- An all-day event read as "starting soon" for its entire day, because the imminent-countdown
  threshold check didn't exclude all-day events (D-023, BUG in Session 3).
- The widget configuration activity crashed after a successful binding write, because forcing an
  immediate redraw threw when the widget id didn't resolve to a real `GlanceId` — stranding
  already-saved data instead of finishing gracefully (BUG-R005, Session 5). The fix: a write
  that already succeeded must not be undone by an optional follow-up step failing.

## How to verify the project still works

```
./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug
```

Current baseline: 217 tests, 0 failures, 0 lint errors (10 accepted warnings, all documented in
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
| `DECISIONS.md` | Every decision (38 as of Session 5) with reason, alternatives, tradeoffs. |
| `ROADMAP.md` | Milestone-by-milestone status and what each one delivered. |
| `KNOWN_ISSUES.md` | Open bugs (none), technical debt, platform limitations, resolved defects. |
| `TODO.md` | Prioritised outstanding work, P0 first. |
| `CHANGELOG.md` | Keep-a-Changelog-format release notes per milestone. |
