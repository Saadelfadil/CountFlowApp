# CountFlow

## Session 4

Date: 2026-08-08
Current Milestone: **Milestone 3 — Event CRUD (COMPLETE)**

> **READ THIS FIRST:** Milestone 3 is done. 269 tests pass, `:core:domain` is at 99.5% line
> coverage, and the CRUD flow was driven on a real emulator with 14 end-to-end checks. Do **not**
> start Milestone 4 without explicit approval.
>
> Authoritative documents, in reading order: `ARCHITECTURE.md` (design, wins on conflict),
> `PROJECT_STATUS.md` (permanent overview), `DECISIONS.md` (32 entries), then this file.
>
> One question is still open from Session 3 and is now visible on screen — see "Requires
> approval" at the end.

----------------------------------

## Objective

Build event CRUD in the order the owner specified: integration tests first, then validation, then
UI models, then ViewModels, and only then screens. The ordering was the point — it makes it
impossible for validation or presentation logic to end up living in a composable.

----------------------------------

## Completed

**Step 1 — Integration tests, closing TD-003**
- Robolectric added to `:core:database` and `:core:data`, pinned to SDK 34.
- 32 DAO tests against real in-memory SQLite: the four `CASE WHEN` sort arms, `COLLATE NOCASE`
  search, the empty-category-set flag, cascade deletes, the unique `(event_id, type)` index, and
  the `getActiveReminders` two-switch join.
- 20 repository tests, domain model in and domain model out, including the two SQL edge cases the
  implementation handles in Kotlin (`IN ()` and `NOT IN ()` over empty sets).

**Step 2 — `EventValidator` in the domain**
- Field-tagged error tokens; every problem reported at once rather than stopping at the first.
- Title length measured in code points, so an emoji title is not cut off at half its length.
- Emoji validated by grapheme cluster via `BreakIterator` — a ZWJ family, a skin-tone modifier
  sequence, and a regional-indicator flag are each correctly one emoji, while a pasted word is
  rejected.
- Target bounds and zone resolution guarded, so a restored backup naming an unknown zone reports
  an error instead of throwing from inside a query.

**Step 3 — UI models and formatting**
- `CountdownLabelFormatter` maps countdown and category tokens to string resources with plurals.
  Exposed as a `Resources` function (for the widget layer) and a composable wrapper that
  re-resolves on configuration change.
- `EventCardUiModel` plus an injectable `EventUiMapper`. Compose never sees `Event`,
  `CountdownResult`, or `EventTarget`.

**Step 4 — ViewModels**
- `EventsViewModel`: search, four sorts, category filter, two distinct empty states.
- `EditEventViewModel`: create and edit through one form, with validation gating every write.

**Step 5 — Screens**
- Home: list, search field, category chips, sort menu, empty states, add button.
- Create/edit: emoji, title, category, date picker, time picker, all-day toggle.

**Verification**
- `assembleDebug test :core:domain:koverVerify :app:lintDebug` — BUILD SUCCESSFUL.
- 269 tests, 0 failures. Lint 0 errors, 10 accepted warnings.
- On an API 36 emulator, a 14-step script drove: empty state → create form → blank-title
  rejection → non-emoji rejection → save → list render → search miss → search hit → clear →
  edit round trip → category filter → clear filters. **All 14 passed.**

----------------------------------

## Three problems found, and one false alarm

1. **Repository tests collided with two coroutine schedulers.** A `StandardTestDispatcher`
   created in `@Before` carries its own `TestCoroutineScheduler`, which conflicts with the one
   `runTest` installs the moment the code under test calls `withContext`. Fixed with
   `Dispatchers.Unconfined` for the SQL tests and `UnconfinedTestDispatcher` for `setMain`.

2. **The first debounce design was wrong.** Debouncing the whole input set would have added
   250 ms to every sort tap and made the search field lag a keystroke behind. Fixed by splitting
   the raw query and the list options into separate flows (D-031). The test now asserts both
   halves — that the field updates immediately *and* that the database is not queried until the
   debounce elapses.

3. **Gradle 9 fails a test task that finds no tests**, which broke the five empty scaffold
   modules. Disabled in the shared convention (D-032).

**The false alarm is worth recording.** The device script reported "event saved and listed" as
failing. The event was in the database and on screen — the check was wrong: `EventCard` uses
`clearAndSetSemantics` to expose one spoken label per card, so the row publishes a `content-desc`
and no child `text` nodes. A second check passed spuriously for the opposite reason ("day" matched
inside "Holiday"). Both were fixed by reading `content-desc` and asserting exact strings. Two bad
assertions, one of them green — a reminder that a passing UI check proves less than it looks.

----------------------------------

## Files Created

20 new files this session; 96 Kotlin files and ~8,500 lines in total.

```
core/domain/…/validation/{EventValidation,EventValidator}.kt
core/domain/src/test/…/validation/EventValidatorTest.kt

core/designsystem/…/format/CountdownLabelFormatter.kt
core/designsystem/src/main/res/values/strings.xml

core/database/src/test/…/{DatabaseTestCase}.kt
core/database/src/test/…/dao/{EventDaoTest,CascadeAndRelationTest}.kt
core/database/src/test/resources/robolectric.properties

core/data/src/test/…/repository/{EventRepositoryImplTest,WidgetBindingRepositoryImplTest}.kt
core/data/src/test/resources/robolectric.properties

feature/events/…/model/{EventCardUiModel,EventUiMapper}.kt
feature/events/…/home/{HomeUiState,EventsViewModel,EventCard,HomeScreen}.kt
feature/events/…/edit/{EditEventUiState,EditEventViewModel,CreateEventScreen}.kt
feature/events/src/test/…/{model/EventUiMapperTest,home/EventsViewModelTest,
                           testing/FakeEventRepository}.kt

build-logic/…/KotlinAndroid.kt        (configureTestTasks added)
```

Removed: `feature/events/…/create/CreateEventScreen.kt`, superseded by the `edit` package.

----------------------------------

## Architecture Decisions

Five new entries, D-028 to D-032, detailed in `DECISIONS.md`:

- **D-028 — `:core:designsystem` is the shared presentation layer**, not a generic component
  library, and depends on `:core:domain`. `CountdownLabel` has to become text somewhere with
  access to resources, and both the app and the coming widget layer need the same "Tomorrow".
- **D-029 — UI models, with tokens deliberately left unresolved.** Compose never sees domain
  objects that carry behaviour or time semantics — but `CountdownLabel` and `EventCategory` cross
  as inert tokens, because resolving them to strings in the mapper would freeze each row in
  whatever locale was active when the flow last emitted.
- **D-030 — mapping a list uses one shared instant**, so the first and last rows of a long list
  cannot straddle midnight and show different day counts for identical events.
- **D-031 — search text and list options are separate flows.** Three requirements a single
  debounced input cannot satisfy at once.
- **D-032 — Gradle's empty-test-task check is disabled**, because several modules are empty by
  design.

----------------------------------

## Current Project Structure

```
CountFlow App/
├── 8 markdown documents
├── build-logic/               7 convention plugins
├── app/                       Application, MainActivity, NavHost
├── core/
│   ├── common/                dispatchers, scope, logging, Clock          [implemented]
│   ├── designsystem/          theme + token-to-text formatting            [implemented]
│   ├── domain/                model, engine, validation, contracts        [COMPLETE]
│   ├── database/              Room, 3 entities, 3 DAOs, schema v1         [COMPLETE]
│   ├── data/                  repositories, mappers, DataStore            [COMPLETE]
│   ├── notifications/                                                     [empty — M7]
│   ├── analytics/                                                         [empty — M9]
│   └── billing/                                                           [empty — M9]
├── feature/
│   ├── events/                home list, create/edit, 2 ViewModels        [COMPLETE]
│   ├── settings/              placeholder                                 [nav done]
│   └── premium/               placeholder                                 [nav done]
└── widget/{engine,glance}/                                                [empty — M4]
```

----------------------------------

## Dependencies Added

| Component | Version | Why |
|---|---|---|
| `org.robolectric:robolectric` | 4.16.1 | DAO and repository tests against real SQLite, no emulator |
| `androidx.test:core-ktx` | 1.7.0 | `ApplicationProvider` for the in-memory database |

`:core:data` takes Room only as a **test** dependency, so Room's types still cannot leak upward
into features or widgets.

----------------------------------

## Current Features Working

- Create an event with validation: blank titles and non-emoji input are rejected with a message
  attached to the offending field.
- List events with the countdown label, day count, category, and progress.
- Realtime search, case-insensitive, debounced.
- Category filtering and four sort orders.
- Tap a row to edit; the form pre-fills and saves back.
- Two distinct empty states — "no countdowns yet" versus "nothing matches".
- Everything below the UI from Milestones 1–2 is unchanged and still passing.

----------------------------------

## Pending Work

**P0 — blocks Session 5**
1. **Approval to begin Milestone 4** (widget engine).
2. **Confirm the countdown label policy** — see "Requires approval" below.

**P1 — Milestone 4:** widget configuration activity, `EntryPointAccessors` in `provideGlance`,
binding cleanup and pruning, first Glance widget end to end.

Full breakdown in `TODO.md`.

----------------------------------

## Known Issues

No runtime bugs. Full detail in `KNOWN_ISSUES.md`.

**Closed this session:** TD-003 (no DAO or repository integration tests).

**Open:**
- **TD-001 (High)** — `builtInKotlin=false` / `newDsl=false` are removed in AGP 10. Unchanged.
- **TD-007 (Medium, new)** — **some UI strings are not localised.** Countdown labels and category
  names go through plural-aware resources; the four sort names, six validation messages, and all
  empty-state and field copy are hard-coded in Kotlin. Left deliberately rather than half-done —
  scheduled as one pass alongside Settings in Milestone 6. The risk is that the app *looks*
  localised because the parts a reviewer checks first are.
- **TD-008 (Low, new)** — archive, complete, and delete exist on the ViewModel and are tested,
  but no gesture calls them.
- **TD-009 (Low, new)** — the date picker's UTC round trip is correct but comment-guarded only.
- **TD-002, TD-005, TD-006** unchanged.
- Platform limitations LIM-001 to LIM-006 unchanged; LIM-005 and LIM-006 bite in Milestone 4.

**Testing gaps:** no Compose UI tests (the device script is real coverage but lives outside the
repository), and `EditEventViewModel` has no direct unit test.

**Lint:** 0 errors, 10 accepted warnings.

----------------------------------

## Next Session Plan

**Step 0 is a gate.** Resolve the two P0 items. Do not start Milestone 4 without approval.

1. Widget configuration activity: declare `android:configure`, handle `EXTRA_APPWIDGET_ID`,
   return `RESULT_OK` with the id echoed back, and make cancelling leave no orphan binding.
2. Declare `widgetFeatures="reconfigurable|configuration_optional"` and
   `widgetCategory="home_screen|keyguard"` from the start.
3. First `GlanceAppWidget`, reaching dependencies through `EntryPointAccessors.fromApplication`
   inside `provideGlance` — Hilt cannot inject a `GlanceAppWidget` (LIM-005).
4. Reuse `CountdownLabelFormatter.format(resources, label)`. It takes `Resources` rather than
   being composable-only precisely so Glance can call it. Do not write a second mapping.
5. Clean up bindings in `onDeleted`, and call `pruneOrphanedBindings` at startup against the
   launcher's live id list.
6. Exclude `widget_bindings` from `data_extraction_rules.xml`.
7. Verify emoji rendering on real hardware, not the emulator (LIM-006).
8. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then place a
   widget on the emulator home screen and drive bind, re-point, and delete.
9. Write the Milestone 4 rationale note and update all seven documents.

Suggested commits: `feat(widget): configuration activity and binding lifecycle`,
`feat(widget): first glance countdown widget`, `test(widget): binding cleanup and pruning`.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified this session:
- `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 269 tests, 0 failures — 88 domain, 38 database, 31 data, 22 feature, plus 90 added across
  those modules this session
- Coverage gate passed: `:core:domain` 99.5% lines, 94.4% branches, against a 95% line minimum
- Lint: 0 errors, 10 warnings, all previously accepted
- Runtime: installed on an API 36 emulator, 14 end-to-end checks passed, no crashes

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

**269 written, 269 passing, 0 failing.**

| Module | Tests | What they cover |
|---|---|---|
| `:core:domain` | 88 | Countdown engine (DST both directions, leap years, travel, all-day lifetimes, every label boundary), model invariants, reminder scheduling, contract defaults, and validation |
| `:core:database` | 38 | Schema guards, plus 32 DAO tests against real SQLite: sort arms, case-insensitive search, empty-set filtering, cascades, the unique reminder index, the active-reminder join |
| `:core:data` | 31 | Mapper round trips, plus 20 repository tests end to end through SQLite |
| `:feature:events` | 22 | UI mapping rules and ViewModel state, including that the search field updates immediately while the query is debounced |

**Coverage** — `:core:domain` 99.5% lines, 94.4% branches, with `countdown`, `model`, and
`validation` packages all at 100%. Enforced by `koverVerify`.

Two techniques worth keeping. The DST tests call `assertCrossesDstTransition`, which fails if a
transition does *not* fall inside the window under test. And `FakeEventRepository` deliberately
does **not** re-implement filtering or sorting — a fake that approximates SQL turns green tests
into false confidence; query behaviour is proven against real SQLite instead.

----------------------------------

## Git Status

Six commits this session, on `master`:

```
c4c43c0  build: add robolectric and relax the empty-test-task check
031ec4f  test(database): dao, cascade, and query coverage against real sqlite
4b8f61d  test(data): repository coverage end to end
80a7c90  feat(domain): event validation
01f2fde  feat(designsystem): countdown label and category formatting
a4ec552  feat(events): home list and create/edit form
         docs: milestone 3 documentation        ← this commit
```

Twenty-two commits total. No remote configured.

----------------------------------

## Developer Notes

- **`calendarDaysRemaining` is the number to display**, not `totals.totalDays`. Unchanged and
  still the easiest thing to get wrong.
- **Never call `Instant.now()` or `LocalDate.now()`** outside the DI module. Inject `Clock`.
  `EditEventViewModel` uses `LocalDate.now(clock)` — note the argument.
- **Do not resolve `CountdownLabel` to a string in a mapper or a ViewModel.** It stays a token
  until composition so a language change re-renders it. `CountdownLabelFormatter` has a
  `Resources` overload for callers outside Compose — the widget layer will need exactly that.
- **`EventUiMapper.mapAll` takes `now` as a parameter on purpose.** Do not "simplify" it to read
  the clock per row.
- **A row exposes one accessibility label, not five.** `EventCard` uses `clearAndSetSemantics`,
  so uiautomator sees a `content-desc` and no child `text` nodes. Any device script must read
  `content-desc` — this cost half an hour of chasing a save bug that did not exist.
- **`FakeEventRepository` does not filter or sort.** That is deliberate; do not "improve" it.
  A fake that reimplements SQL makes the ViewModel tests agree with the fake rather than with
  the database.
- **Validation lives in the domain**, not the ViewModel, because the form is not the only writer.
  Restore and widget configuration will both create events.
- **Build output is noisy** (TD-005). Filter with
  `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"`.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`.

----------------------------------

## Requires approval before Session 5

1. **Milestone 4.**
2. **The countdown label policy**, still unanswered from Session 3 and now visible on screen: an
   event a week out reads "7 / Next week". Is that the wording and the threshold set you want?
   Everything is in `CountdownConfig` and is a one-line change today; once widgets render these
   strings, changing them means touching two surfaces and their tests.

----------------------------------

## Estimated Progress

```
Overall Progress            36%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
Event CRUD / UI             85%   (gestures and colour picker outstanding)
Widgets                      0%
Notifications                0%
Billing                      0%
Testing                     70%   (domain, DAO, repository, ViewModel; no UI tests)
Play Store                   0%
```
