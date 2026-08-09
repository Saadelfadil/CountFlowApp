# CountFlow

## Session 11

Date: 2026-08-09
Current Milestone: **Milestone 3 finishing pass — Event management polish (COMPLETE); Milestone 5B remains the most recent widget milestone**

> **READ THIS FIRST:** This session closed the two gaps Milestone 3 (Session 4) had left open
> since the event list and create/edit form first existed: no live widget preview in the form,
> and no UI gesture for archive/complete/delete. Both had been waiting specifically for
> infrastructure that now exists — a real widget renderer to preview against (built Milestone 4)
> and a settled event-list design to add gestures to. Deliberately scoped smaller than Sessions 9
> or 10: this is a product-polish session on an already-built screen, not new architecture, and
> the brief was explicit that it "not begin Notifications, Billing, or Milestone 6."
>
> **What changed:** the event list is now three tabs — Upcoming, Completed, Archived
> (`EventLifecycleFilter`, D-058, replacing the old `includeArchived`/`includeCompleted`
> inclusion-flag pair). Every row supports complete/archive/restore/delete two ways: a swipe
> gesture on Upcoming rows, and an overflow menu present on every row, on every tab, that is the
> one path every action — including delete, deliberately never a swipe target — always has
> (D-060). Delete requires a worded confirmation naming the real event and the real cascade
> behavior. The create/edit form now shows a live widget preview (`EventWidgetPreview`), reusing
> the real `WidgetRenderModelProvider.preview()` pipeline through a new, deliberately narrow
> `:feature:events → :widget:engine` dependency rather than the heavier `:widget:glance` module
> (D-059).
>
> **Verified on a real device, not just unit-tested:** the full lifecycle (create → edit →
> complete → archive → restore → delete, plus cancel-delete); both swipe directions and the
> menu's non-swipe alternative (confirmed against the real accessibility semantics tree — the
> card's merged description and the overflow button's own independent description both appear as
> separate TalkBack nodes); a real placed widget's behavior across completing, archiving, and
> deleting its bound event, with **zero widget-specific code needed** — the existing render
> pipeline and cascading foreign key already handled all three correctly; light/dark mode; and
> 200% font scale, which found and fixed one real bug (the tab row didn't scroll like the
> category row beside it).
>
> Authoritative documents, in reading order: `AI_CONTEXT.md`, `ARCHITECTURE.md`,
> `PROJECT_STATUS.md`, `DECISIONS.md` (61 entries — D-058 through D-061 are new this session),
> then this file.
>
> Two items are open for Session 12 — see "Requires approval" at the end.

----------------------------------

## Objective

Finish the everyday event-management experience so that Event CRUD/UI can be considered 100%
complete for V1. Per the brief: organize the event list into Upcoming/Completed/Archived without
turning CountFlow into a project-management app; make complete/archive/delete reachable via
intuitive swipe actions *and* an accessible non-swipe alternative, since swipe must never be the
only way to reach a destructive or state-changing action; require real confirmation before
delete, worded from the app's actual data behavior, not a guess; design intentional (not
over-designed) empty states for first launch, no-upcoming, no-completed, and no-archived; add a
live widget preview to the create/edit form reusing Session 9's existing
`WidgetRenderModelProvider.preview()` infrastructure rather than building a second preview engine;
verify the whole thing on a real device including dark/light mode and large font scale; and
answer six specific closing questions, then stop before Notifications, Billing, or Milestone 6.

----------------------------------

## Completed

**Three lifecycle tabs replace the single unfiltered list**

`EventLifecycleFilter` (`core:domain`, new) — `UPCOMING`/`COMPLETED`/`ARCHIVED`, exclusive —
replaces `EventFilter`'s two independent `includeArchived`/`includeCompleted` flags, which could
only ever express "don't hide X," never "show only X." `EventDao.observeEvents` picks exactly one
bucket via three `CASE WHEN`-style `OR` arms in SQL, the same verified-query pattern the existing
`sort` parameter already used (D-027). An event that is both completed and archived sorts into
`ARCHIVED` — archiving is the more deliberate, final action, so it wins the tie; confirmed
on-device via the real SQL, not just reasoned about (D-058).

**Complete, archive, restore, and delete — reachable two ways on every row**

`EventCard` wraps Upcoming rows in a `SwipeToDismissBox`: swipe one direction to complete, the
other to archive. Every row, on every tab, also carries an overflow (⋮) menu with every action
valid for that tab's bucket — Upcoming offers Mark complete/Archive/Delete; Completed offers Mark
not complete/Archive/Delete; Archived offers Restore/Delete. Delete is **never** a swipe target
on any tab — it always opens a worded `AlertDialog` (`Delete "<title>"? This countdown and its
reminders will be deleted. Any widgets showing it will return to the unconfigured state. Cancel /
Delete`), naming the real event and describing only what `EventRepository.deleteEvent`'s cascade
actually does. This closes TD-008 (open since Session 4) with the accessible alternative the
brief explicitly required — the menu is not a fallback for the swipe, it is the one path every
action always has (D-060).

**Four tab-and-filter-aware empty states**

`HomeUiState.emptyState` now distinguishes `NO_UPCOMING` ("Nothing coming up... Create a
countdown for your next moment," with a "Create countdown" button beside the FAB), `NO_COMPLETED`
("No completed countdowns yet"), `NO_ARCHIVED` ("No archived countdowns"), and `NO_MATCHES`
(active search/filter, any tab — takes priority over the tab-specific copy). Deliberately does
**not** distinguish genuine first-launch from "everything is done or archived" — both use the
`NO_UPCOMING` copy, which reads correctly either way, per the brief's own "do not over-design
these states" instruction and D-061's explicit reasoning for skipping the extra plumbing that
distinction would need.

**Live widget preview in the create/edit form**

`EventWidgetPreview.kt` (new, `feature/events/.../edit/`) — a compact, single-line-per-fact card
reusing `WidgetRenderModelProvider.preview()`, the exact pure, no-I/O render path the widget
configuration screen's own preview already uses (D-048). `EditEventViewModel` gained a
`refreshPreview()` call after every field mutator (title, emoji, category, date, time, all-day,
accent color), building an ephemeral, never-persisted `Event`/`WidgetBinding` pair each time — the
same discipline `onSave()` itself already follows for the real write, via a new shared `buildEvent`
helper so the preview and the real save can never quietly diverge on what a save would produce.
Requires `:feature:events` to depend on `:widget:engine` — a new, deliberately narrow edge (D-059)
that does **not** extend to `:widget:glance`, keeping the heavier Glance/AppWidget runtime, the 21
size×style layouts, and the `resolveHeadline` content-hierarchy split (D-046, `internal` to that
module) unreused outside `:app`. Confirmed on-device: the preview updates live as title, category,
and accent color change.

**Accessibility fix to the card's own semantics**

The card's descriptive content (emoji, title, category, countdown) keeps its single merged
TalkBack sentence, but that merge is now scoped to just that content — the new overflow button
sits outside it, confirmed via the real semantics tree (`uiautomator dump`) to carry its own
independent "More actions for `<title>`" description rather than being swallowed by a card-wide
`clearAndSetSemantics`.

**Real-device verification sweep**

- Full lifecycle: create (with live preview reacting to title/category/accent) → edit (preview
  pre-populated from the loaded event) → complete (swipe, confirmed via DB) → archive (menu,
  confirmed the widget stayed unaffected — exactly per `Event.isArchived`'s own doc) → restore →
  delete (confirmed via DB and the widget falling back to its unconfigured placeholder) → cancel
  delete (confirmed non-destructive).
- A real placed widget (reused from prior-session leftover state, re-bound via a direct
  configuration-activity launch) confirmed correct across completing, archiving, and deleting its
  bound event — no widget-specific code was needed for any of the three; the existing render
  pipeline and cascading foreign key already did the right thing.
- Both swipe directions on the Upcoming tab; the overflow menu's tab-specific item sets on all
  three tabs; all four empty states (one, `NO_MATCHES`, found incidentally via a mis-tapped
  category filter, which turned out to be a useful confirmation).
- Light mode and dark mode.
- 200% font scale — found and fixed one real bug (below).

**Bug found and fixed within the session**

The new `EventTabRow` didn't scroll horizontally like the existing `CategoryFilterRow` beside it.
At 200% font scale, three fixed-width `FilterChip`s with no room to grow forced "Archived" to
wrap one letter per line into a vertical stack instead of scrolling off-screen. Found via the
session's own large-font-scale device check; fixed by adding the same `horizontalScroll` the
category row already had. Verified fixed via re-screenshot; the full engineering gate was re-run
after the fix.

**New tests**

`EditEventViewModelTest.kt` (new) — this ViewModel's first-ever unit tests, closing a gap
`TODO.md` had named since the ViewModel existed: preview reactivity to every field change,
preview clearing when the title goes blank, and confirmation that nothing the preview computes
ever reaches the repository before `onSave()`. `EventDaoTest`/`EventRepositoryImplTest` gained
tests for the new exclusive-bucket semantics, including one that reconstructs the full events
table from all three bucket queries and asserts nothing is missing or duplicated.
`EventsViewModelTest` gained tab-switching and per-tab empty-state tests.

**Verification**

- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL (run
  twice: once before the font-scale bugfix, once after, both green).
- 259 tests, 0 failures (up from 245).
- Lint: 0 errors, 17 warnings, unchanged since Session 9.
- `:core:domain` coverage unchanged at 97.0%, gated at 95%.

----------------------------------

## Files Created

```
feature/events/…/edit/EventWidgetPreview.kt                          (new)
feature/events/src/test/kotlin/…/edit/EditEventViewModelTest.kt      (new)
```

----------------------------------

## Files Modified

```
core/domain/…/repository/EventRepository.kt          (EventLifecycleFilter enum, EventFilter.lifecycle)
core/domain/src/test/…/repository/RepositoryContractTest.kt  (updated for the new field)
core/database/…/dao/EventDao.kt                       (observeEvents query rewritten for lifecycle bucket)
core/database/src/test/…/dao/EventDaoTest.kt           (bucket tests, mutual-exclusivity test)
core/data/…/repository/EventRepositoryImpl.kt          (passes filter.lifecycle.name)
core/data/src/test/…/repository/EventRepositoryImplTest.kt (+1 lifecycle test)
feature/events/build.gradle.kts                        (+implementation(project(":widget:engine")))
feature/events/…/home/EventsViewModel.kt                (onTabChange, ListOptions.tab)
feature/events/…/home/HomeUiState.kt                    (tab field, 4-way emptyState)
feature/events/…/home/HomeScreen.kt                      (EventTabRow, empty-state copy, wiring)
feature/events/…/home/EventCard.kt                       (full rewrite — swipe, overflow menu, delete dialog)
feature/events/…/edit/EditEventUiState.kt                (previewModel field)
feature/events/…/edit/EditEventViewModel.kt               (refreshPreview(), buildEvent() shared helper)
feature/events/…/edit/CreateEventScreen.kt                (renders EventWidgetPreview when present)
feature/events/src/test/…/home/EventsViewModelTest.kt     (tab tests, updated empty-state tests)
AI_CONTEXT.md, CHANGELOG.md, DECISIONS.md, KNOWN_ISSUES.md, PROJECT_STATUS.md, ROADMAP.md, TODO.md
```

----------------------------------

## Architecture Decisions

Four new entries, D-058 through D-061, detailed in `DECISIONS.md`:

- **D-058** — `EventLifecycleFilter` replaces `includeArchived`/`includeCompleted` on
  `EventFilter`, giving three mutually exclusive buckets instead of two inclusion flags.
- **D-059** — The create/edit form's live preview depends on `:widget:engine`, not
  `:widget:glance` — reuses the real render pipeline, deliberately does not reuse
  `WidgetPreviewCard` or `resolveHeadline` (both `internal` to the heavier module).
- **D-060** — Delete is never a swipe target; the overflow menu is the one action surface every
  tab and every action always has.
- **D-061** — The empty Upcoming tab does not distinguish genuine first launch from "everything
  is done or archived" — a deliberate scope reduction per the brief's own instruction.

----------------------------------

## Current Project Structure

One new dependency edge: `:feature:events → :widget:engine` (D-059), added to
`feature/events/build.gradle.kts` on top of the `countflow.android.feature` convention plugin's
baseline. No new modules. `:widget:engine` remains pure Kotlin/JVM (D-033), so this adds no
Android or Glance dependency weight to the events feature. See `PROJECT_STATUS.md` for the full,
updated module graph.

----------------------------------

## Dependencies Added

None external. The one new dependency is internal to the project (`:widget:engine`, above).

----------------------------------

## Current Features Working

Everything from Session 10, plus: a three-tab event list (Upcoming/Completed/Archived) with full
complete/archive/restore/delete support via both swipe and an always-present accessible menu;
worded delete confirmation; four tab-aware empty states; a live widget preview in the create/edit
form. Event CRUD/UI is now considered complete for V1 — see this session's Final Report, below and
in the closing message, for the precise verdict and what would still change before Google Play.

----------------------------------

## Pending Work

**P0 — blocks Session 12**
1. **Approve Milestone 6, or further Milestone 5 work**, now that Event CRUD/UI is at 100% for V1.
2. **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — carried
   over unchanged from Session 10; not attempted this session, which was explicitly scoped to
   event management, not widget sizing.

**P1 — rest of Milestone 5:** same-event-two-different-styles real-UI verification; emoji
rendering on a physical device (LIM-006); re-measure `WidgetSizeClass` thresholds on a physical
device and a second launcher (TD-016); migrate `EventCard`'s swipe gesture off the deprecated
`confirmValueChange` parameter (TD-018, low priority, still functions correctly).

----------------------------------

## Known Issues

Full detail in `KNOWN_ISSUES.md`.

**Resolved this session:** TD-008 (archive/complete/delete gesture, open since Session 4, closed
with a full accessible-menu alternative per the brief's explicit requirement).

**New this session:** TD-018 (`EventCard`'s swipe gesture uses a deprecated Material 3 parameter;
functions correctly, no drop-in replacement exists).

**Open, unchanged:** TD-001, TD-002, TD-005, TD-006, TD-007, TD-009, TD-016, TD-017. BUG-011
(decision final per D-052, not revisited this session). LIM-002, LIM-003, LIM-005, LIM-006.

**Lint:** 0 errors, 17 accepted warnings, unchanged since Session 9.

----------------------------------

## Next Session Plan

1. Get explicit approval before starting Milestone 6, or before resuming Milestone 5's remaining
   widget-sizing work — this session's brief was explicit that it stops before both.
2. If a real (ideally physical) device is available: prioritize the real 4×2 (`WIDE`) placement
   and screenshot Session 10 could not complete — the one gap actively blocking Milestone 5's
   completion.
3. If approved to continue Milestone 5 instead: same-event multi-style real-UI verification, a
   second-launcher `WidgetSizeClass` re-measurement.
4. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then update all
   documents per the standing working agreement.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session (twice — before and after the font-scale bugfix, both green):
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 259 tests, 0 failures (up from 245)
- Coverage gate passed: `:core:domain` 97.0% lines, unchanged
- Lint: 0 errors, 17 warnings, unchanged since Session 9
- Runtime: the same stable local emulator established in Session 8 (`Pixel_9`), reused
  successfully for the full verification sweep, including direct-launching the widget
  configuration activity for a widget id already placed on the home screen from prior-session
  leftover state, and reading the app's own SQLite database directly (`run-as` + a local
  `sqlite3`) to confirm state transitions independent of what the UI claimed

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed. For device work,
launch `~/Library/Android/sdk/emulator/emulator -avd Pixel_9` directly (GUI mode).

----------------------------------

## Tests

**259 written, 259 passing, 0 failing — up from 245.**

| Module | Tests | Change this session |
|---|---|---|
| `:core:domain` | 91 | Unchanged (2 existing tests updated for the new field, no new tests) |
| `:core:database` | 40 | +2 (lifecycle-bucket queries, replacing 2 old include-flag tests) |
| `:core:data` | 32 | +1 (lifecycle filter selects the archived bucket) |
| `:feature:events` | 33 | +11 (tab switching, per-tab empty states, and `EditEventViewModelTest.kt`'s first-ever coverage of this ViewModel) |
| `:widget:engine` | 34 | Unchanged |
| `:widget:glance` | 29 | Unchanged |

**Coverage** — `:core:domain` 97.0% lines, unchanged (the new `EventLifecycleFilter` enum needed
no dedicated test of its own; its behavior is exercised through `EventDaoTest`/
`EventRepositoryImplTest`). The full event lifecycle's real-device behavior (swipe gestures, menu
actions, widget-binding side effects) is verified on-device per this session's own sweep, not by
automated test — Compose UI tests for the app's own screens remain a standing gap (`TODO.md`
Testing gaps), unchanged this session.

----------------------------------

## Git Status

Not yet committed as of writing this summary — commit follows immediately after. Working tree
before that commit: 20 modified files, 2 new files, building on `main` at `23e6cab` (Session 10's
final commit). No remote configured.

----------------------------------

## Developer Notes

- **A boolean pair that can only express "don't hide X" cannot express "show only X."** The old
  `EventFilter.includeArchived`/`includeCompleted` had worked fine for a single unfiltered list
  with an archived-events toggle, but building three genuinely exclusive tabs needed a different
  shape entirely (D-058). Worth recognizing early when a feature's shape changes from "narrow a
  list" to "partition a list" — the two need different filter representations, not the same one
  stretched further.
- **A pure, no-I/O preview function is exactly the kind of thing worth depending across a module
  boundary for, even from a module that otherwise has nothing to do with widgets.**
  `WidgetRenderModelProvider.preview()`'s entire value in D-048 was staying reusable without
  reopening the orphan-binding risk it was built to avoid; Session 11 found its second real
  consumer (`:feature:events`, not just `:widget:glance`) and the pure-Kotlin module boundary
  D-033/D-004 established two milestones earlier is exactly what made adding that consumer a
  one-line `build.gradle.kts` change rather than a redesign.
- **`internal` visibility is a real signal about who should — and shouldn't — reuse something,
  not just a compiler nag.** `WidgetPreviewCard` and `resolveHeadline` being `internal` to
  `:widget:glance` (D-042's narrowing, D-046) was the concrete reason this session built a second,
  independently simplified preview drawing (`EventWidgetPreview`) rather than widening that
  module's public surface for one new consumer — and the resulting preview is smaller and simpler
  *because* it wasn't trying to reproduce a decision (the primary/secondary hierarchy split) built
  for a problem (multi-line widget redundancy) the compact form preview was never at risk of.
- **A layout pattern copied from a sibling needs its whole behavior copied too, not just its
  static appearance.** `EventTabRow` visually matched `CategoryFilterRow` but omitted
  `horizontalScroll`, invisible until 200% font scale forced a wrap bug neither test suite nor a
  100%-scale screenshot would ever catch. Same shape of gap as BUG-R011 (Session 9): a constant or
  a pattern tuned for/copied under one condition, applied unconditionally to another.
- **Reading the app's own SQLite database directly (`run-as com.countflow cat databases/...`)
  settled two ambiguous on-device results this session that screenshots alone could not.** Once
  when a tap landed on "Archive" instead of "Mark complete" (the widget correctly not updating
  turned out to be *correct* behavior, not a bug, once the DB confirmed which action actually
  fired) and once to confirm exact tap coordinates needed scaling from the screenshot's
  display-pixel size to the device's real pixel size consistently. Worth remembering as a standing
  technique: when a UI screenshot's story doesn't match what should have happened, the database is
  a faster ground truth than re-guessing the tap.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Device: `~/Library/Android/sdk/emulator/emulator -avd Pixel_9`.

----------------------------------

## Requires approval before Session 12

1. **Milestone 6, or the rest of Milestone 5** — Event CRUD/UI is now at 100% for V1; the natural
   next step is either Settings (Milestone 6) or finishing Milestone 5's widget-sizing loose ends
   (real `WIDE` confirmation, same-event multi-style verification).
2. **Priority of the real 4×2 (`WIDE`) device measurement** (TD-016, TD-017), carried over
   unchanged from Session 10 — still the one concrete gap blocking Milestone 5's own completion.

----------------------------------

## Estimated Progress

```
Overall Progress            57%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             100%   (Session 11: lifecycle tabs, gestures, live preview — complete for V1)
Widget Engine                98%   (validated on a real device — docs/PRODUCT_REVIEW.md)
Widget Themes & Sizes        70%   (responsive 2×1/2×2/4×2 delivered — Milestone 5B;
                                     real WIDE confirmation and multi-widget polish remain)
Notifications                 0%
Billing                       0%
Testing                      80%   (domain, DAO, repository, ViewModel, widget engine, Glance UI)
Play Store                    0%
```
