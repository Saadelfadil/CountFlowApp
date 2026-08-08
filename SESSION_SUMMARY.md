# CountFlow

## Session 3

Date: 2026-08-08
Current Milestone: **Milestone 2 — Domain, Countdown Engine, Persistence (COMPLETE)**

> **READ THIS FIRST:** Milestone 2 is done. 86 tests pass, `:core:domain` is at 99.4% line
> coverage with the countdown engine at 100%, and the build is green. Do **not** start
> Milestone 3 without explicit approval.
>
> Authoritative documents, in reading order: `ARCHITECTURE.md` (design, wins on conflict),
> `PROJECT_STATUS.md` (permanent overview), `DECISIONS.md` (27 entries), then this file.
>
> The Session 2 "Kotlin Native" question is closed: this session's brief specified Room and
> DataStore, both Android-only, so native Android was confirmed by instruction. The one place
> that decision is load-bearing is recorded as D-018.

----------------------------------

## Objective

Build the business model everything else depends on: the complete domain model in pure Kotlin,
the countdown engine with exhaustive tests, then Room, repositories, and DataStore — in that
order, and only in that order. No widgets, no UI, no notifications, no billing.

----------------------------------

## Completed

**Step 1 — Domain model (`:core:domain`, pure Kotlin/JVM, zero Android)**
- `Event`, `EventTarget`, `WidgetBinding`, `Reminder` as immutable data classes.
- `EventCategory`, `WidgetStyle` (with a `isPremium` flag), `ProgressStyle`, `ReminderType`.
- `AccentColor` as a sealed type: `Dynamic` or `Fixed(argb)`.
- `EventId`, `ReminderId`, `AppWidgetId` as value classes, so an event id cannot be passed where
  a reminder id belongs.
- `EventTarget` encodes the all-day/timed distinction and owns zone resolution.

**Step 2 — Countdown engine**
- `CountdownEngine` computing years, months, weeks, days, hours, minutes, seconds, percentage
  complete, elapsed, and remaining, plus a status and a display label.
- `CountdownResult` splits three easily-conflated quantities into distinct fields:
  `breakdown` (calendar remainders), `totals` (whole units), and `calendarDaysRemaining` (the
  midnight count a widget should actually display).
- `CountdownStatus` — COMPLETED, EXPIRED, IMMINENT, TODAY, UPCOMING — drives behaviour.
- `CountdownLabel` — a sealed token type, not a string — drives text.
- `CountdownConfig` holds every threshold as data, including the locale's first day of week.

**Step 3 — Tests: 86 across three modules, 0 failures**
- 69 in `:core:domain`, covering DST in both directions, leap years and leap days, timezone
  travel, all-day versus timed lifetimes, month and year boundaries, past events, progress
  edge cases, and every label boundary.
- 11 mapper round-trip tests in `:core:data`; 6 schema guard tests in `:core:database`.
- Kover gate at 95% on `:core:domain`; actual **99.4% lines, 94% branches**, with the
  `countdown` and `model` packages at **100%**.

**Step 4 — Room (`:core:database`)**
- `EventEntity`, `WidgetBindingEntity`, `ReminderEntity` with cascading foreign keys, a unique
  `(event_id, type)` index on reminders, and indexes on the columns the list filters and sorts by.
- Converters for enums, `Instant`, and `LocalTime`.
- Three DAOs. Filtering and sorting run in SQL, sorting via `CASE WHEN` arms so Room verifies
  the query at compile time.
- Schema v1 exported to `core/database/schemas` and committed.
- `Migrations.kt` plus a guard test asserting the migration count matches the schema version.

**Step 5 — Repositories**
- Contracts in the domain: `EventRepository`, `WidgetBindingRepository`, `ReminderRepository`,
  `PreferencesRepository`, with `EventFilter`, `EventSort`, `ThemeMode`, `UserPreferences`.
- Room-backed implementations in `:core:data`, mapping on the IO dispatcher.

**Step 6 — DataStore**
- Preferences only, with a corruption handler and defensive enum parsing.

**Build**
- `countflow.android.room` convention plugin; Kover; `java.time.Clock` provided through DI.

----------------------------------

## Two defects found and fixed

Both were found by tests, not by inspection, and both would have shipped.

1. **All-day events reported `IMMINENT` for their whole day.** The imminent threshold was
   applied to every event, so once an all-day event's midnight start had passed it satisfied the
   check indefinitely. A widget would have shown a live ticking countdown to a moment already
   gone. Fixed by excluding all-day events from the check entirely (D-023).

2. **`remaining` counted upward for an event in progress.** It was the absolute distance to the
   target, so an all-day event twelve hours into its day reported "12 hours" while `isPast` was
   false — reading as twelve hours still to wait. Fixed by separating three quantities that had
   been one: `remaining` (forward-looking, clamps to zero), `gap` (unsigned, feeds breakdown and
   totals), and `calendarDaysRemaining` (signed).

A third issue was a test error rather than a code error, and is worth recording because the
distinction is the heart of this milestone: across a spring-forward weekend the engine reports
a three-day calendar gap and a 71-hour duration. Both are correct. I had written the expectation
as two days and 23 hours. The test now asserts both numbers and explains why they differ.

----------------------------------

## Files Created

47 Kotlin files added this session (76 total, ~3,600 lines).

```
core/domain/src/main/kotlin/com/countflow/core/domain/
    model/{Ids,EventCategory,WidgetStyle,AccentColor,EventTarget,Event,WidgetBinding,Reminder}.kt
    countdown/{CountdownStatus,CountdownLabel,CountdownConfig,CountdownResult,CountdownEngine}.kt
    repository/{EventRepository,WidgetBindingRepository,ReminderRepository,PreferencesRepository}.kt
core/domain/src/test/kotlin/com/countflow/core/domain/
    testing/TestFixtures.kt
    countdown/{CountdownEngineLabelTest,CountdownEngineCalendarTest,
               CountdownEngineTimeZoneTest,CountdownEngineProgressTest}.kt
    model/DomainModelTest.kt
    repository/RepositoryContractTest.kt

core/database/src/main/kotlin/com/countflow/core/database/
    {CountFlowDatabase,Migrations}.kt
    entity/{EventEntity,WidgetBindingEntity,ReminderEntity,WidgetBindingWithEvent}.kt
    dao/{EventDao,WidgetBindingDao,ReminderDao}.kt
    converter/Converters.kt
    di/DatabaseModule.kt
core/database/src/test/kotlin/com/countflow/core/database/DatabaseSchemaTest.kt
core/database/schemas/com.countflow.core.database.CountFlowDatabase/1.json

core/data/src/main/kotlin/com/countflow/core/data/
    mapper/{EventMapper,WidgetBindingMapper,ReminderMapper}.kt
    repository/{EventRepositoryImpl,WidgetBindingRepositoryImpl,ReminderRepositoryImpl}.kt
    preferences/PreferencesRepositoryImpl.kt
    di/{DataModule,DataStoreModule}.kt
core/data/src/test/kotlin/com/countflow/core/data/mapper/MapperRoundTripTest.kt

core/common/src/main/kotlin/com/countflow/core/common/di/TimeModule.kt
build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt
```

## Files Modified

`gradle/libs.versions.toml` (Room plugin, Kover, javax.inject; kotlinx-datetime removed),
`build.gradle.kts` (Room plugin on the buildscript classpath),
`build-logic/convention/build.gradle.kts`, `core/{domain,database,data}/build.gradle.kts`,
and all seven documents.

----------------------------------

## Architecture Decisions

Ten new entries, D-018 to D-027, all detailed in `DECISIONS.md`. The ones that shape the code:

- **D-018 — `java.time`, not `kotlinx-datetime`.** Strongest DST and calendar semantics, native
  at minSdk 31. **This is the one thing in `:core:domain` that would have to change for Kotlin
  Multiplatform**, recorded explicitly because of the Session 2 ambiguity.
- **D-019 — `:core:database` depends on `:core:domain`.** Made, then reversed mid-session. The
  independent version made every enum column stringly typed and left converters with nothing to
  do. The database is part of the data layer; depending on the domain is the correct direction.
- **D-020 — enums persisted by name, never ordinal.** An ordinal is smaller and is a live
  grenade: inserting a constant mid-enum silently reinterprets every stored row.
- **D-021 — the domain returns label tokens, not strings.** Returning `"Tomorrow"` would
  hard-code English and break plural rules.
- **D-022 — breakdown, totals, and calendar days are three separate fields.** Conflating them is
  the classic countdown bug.
- **D-023 — all-day events are never `IMMINENT`.** Found by a failing test.
- **D-024 — destructive migration is never enabled.** It would delete every countdown the user
  made and blank every widget, on an ordinary update.
- **D-025 — coverage is enforced at 95% on `:core:domain`,** not merely reported.
- **D-026 — `java.time.Clock` injected directly,** no bespoke wrapper. Nothing outside the DI
  module calls `Instant.now()`.
- **D-027 — `CASE WHEN` sorting, not `@RawQuery`,** to keep Room's compile-time SQL verification.

----------------------------------

## Current Project Structure

```
CountFlow App/
├── 8 markdown documents
├── build-logic/                7 convention plugins (Room added)
├── app/                        Application, MainActivity, NavHost
├── core/
│   ├── common/                 dispatchers, scope, logging, Clock       [implemented]
│   ├── designsystem/           M3 theme + PlaceholderScreen             [implemented]
│   ├── domain/                 model, countdown engine, contracts       [COMPLETE]
│   ├── database/               Room, 3 entities, 3 DAOs, schema v1      [COMPLETE]
│   ├── data/                   repositories, mappers, DataStore         [COMPLETE]
│   ├── notifications/                                                   [empty — M7]
│   ├── analytics/                                                       [empty — M9]
│   └── billing/                                                         [empty — M9]
├── feature/{events,settings,premium}/    navigation + placeholders      [nav done]
└── widget/{engine,glance}/                                              [empty — M4]
```

----------------------------------

## Dependencies Added

| Component | Version | Why |
|---|---|---|
| `androidx.room:room-gradle-plugin` | 2.8.4 | schema export via convention plugin |
| `org.jetbrains.kotlinx.kover` | 0.9.9 | coverage gate on `:core:domain` |
| `javax.inject` | 1 | `@Inject` in the domain without pulling in Hilt |

Removed: `kotlinx-datetime` (superseded by `java.time`, D-018).

Room 2.8.4 and DataStore 1.2.1 were already in the catalog and are now actually applied.

----------------------------------

## Current Features Working

- Countdown computation correct across DST both directions, leap years, leap days, timezone
  travel, all-day and timed events, month and year boundaries, and past events.
- Persistence: events, widget bindings, and reminders with cascading deletes.
- Repositories exposing `Flow`s the UI can collect; DataStore preferences with defaults.
- The app still builds, installs, and runs — unchanged from Session 2, since no UI was touched.

----------------------------------

## Pending Work

**P0 — blocks Session 4**
1. **Approval to begin Milestone 3.**
2. **Confirm the countdown label policy** (see "Requires approval" below). Cheap to change now,
   expensive once screens and widgets depend on it.

**P1 — Milestone 3:** open with TD-003 (Robolectric + DAO tests), then home screen, create/edit
form, ViewModels, and mapping `CountdownLabel` tokens to string resources with proper plurals.

Full breakdown in `TODO.md`.

----------------------------------

## Known Issues

No runtime bugs. Full detail in `KNOWN_ISSUES.md`.

- **TD-001 (High)** — `builtInKotlin=false` / `newDsl=false` are removed in AGP 10. Unchanged.
- **TD-003 (Medium, narrowed)** — **no DAO or repository integration tests.** The domain is
  thoroughly covered and the mappers are round-trip tested, but nothing exercises a real SQLite
  database. Unverified: that the `CASE WHEN` sort arms order correctly, that the cascade actually
  deletes, that the empty-set `IN`/`NOT IN` handling behaves, and that `getActiveReminders`
  filters on both switches. Needs Robolectric; scheduled for the start of Milestone 3.
- **TD-005 (Low, new)** — ~28 lines of build-script deprecation noise per build, a side effect of
  TD-001. Filter with `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"`.
- **TD-006 (Low, new)** — title search is ASCII-case-insensitive only; "ÉCOLE" will not match
  "école".
- Platform limitations LIM-001 to LIM-006 unchanged and still relevant from Milestone 4 onward.

**Lint:** 0 errors, 10 accepted warnings, all documented.

----------------------------------

## Next Session Plan

**Step 0 is a gate.** Resolve the two P0 items. Do not start Milestone 3 without approval.

1. Add Robolectric to the test convention plugin and close TD-003 with DAO tests against an
   in-memory database — the cascade, the four sort arms, the empty-set filter behaviour, and the
   `getActiveReminders` join. Do this **before** UI code depends on query behaviour nothing has
   exercised.
2. Repository tests with Turbine over the returned `Flow`s.
3. Map `CountdownLabel` tokens to string resources in `:core:designsystem` or `:feature:events`,
   using `plurals` rather than concatenation.
4. `EventsViewModel` exposing immutable state via `StateFlow`, combining the repository flow with
   search and sort inputs.
5. Home screen: list, realtime search, sort menu, category filter, empty state.
6. Create/edit form: title, emoji picker, category, date picker, time picker, all-day toggle,
   accent colour, reminders.
7. Archive, complete, and delete with undo.
8. Verify `./gradlew assembleDebug test :core:domain:koverVerify :app:lintDebug`, then run on the
   emulator and drive the CRUD flow the way navigation was driven in Session 2.
9. Write the Milestone 3 rationale note and update all seven documents.

Suggested commits: `test(database): dao and cascade coverage with robolectric`,
`feat(designsystem): countdown label string resources`,
`feat(events): home screen with search and sort`,
`feat(events): create and edit event form`.

----------------------------------

## Build Status

**✅ Builds Successfully**

Verified from clean this session:
- `./gradlew clean assembleDebug test :core:domain:koverVerify :app:lintDebug` → BUILD SUCCESSFUL
- 86 tests, 0 failures — 69 domain, 11 data, 6 database
- Coverage gate passed: `:core:domain` 99.4% lines against a 95% minimum
- Lint: 0 errors, 10 warnings, all previously accepted
- Debug APK 39 MB (debug tooling, no R8)
- Room schema v1 exported and committed

Reproduce with `JAVA_HOME` set to JDK 21 and `platforms;android-37.0` installed.

----------------------------------

## Tests

**86 written, 86 passing, 0 failing.**

| Module | Tests | What they cover |
|---|---|---|
| `:core:domain` | 69 | Labels and statuses, calendar-versus-duration day counts, DST both directions, leap years and days, timezone travel, all-day lifetimes, progress edge cases, model invariants, reminder scheduling, contract defaults |
| `:core:data` | 11 | Entity/domain round trips: every category, every style combination, every reminder type, dynamic versus fixed accent, all-day flag and zone preservation |
| `:core:database` | 6 | Schema export present and versioned, every table declared, both cascades declared, migration count matches version |

**Coverage** — `:core:domain` 99.4% lines, 94% branches. `countdown` and `model` packages at
100%. Enforced by `koverVerify` at a 95% minimum, so the build fails if it regresses.

Two techniques worth keeping. The DST tests call `assertCrossesDstTransition`, which fails if a
transition does *not* fall inside the window under test — without it, a tz database update could
leave them passing while silently exercising an ordinary week. And the label tests are
table-driven, so adding a boundary case is one line.

**Not covered:** anything requiring a real SQLite database (TD-003).

----------------------------------

## Git Status

Seven commits this session, on `master`, ordered so each builds on the last:

```
7697c26  build: add room convention plugin and coverage gate
6c30375  feat(domain): event, widget binding, and reminder models
33cbe07  feat(domain): countdown engine with calendar-accurate day counts
9338e00  feat(domain): repository contracts
82d8508  test(domain): dst, leap year, travel, and boundary coverage
52721cf  feat(database): entities, daos, converters, and schema export
8b06b56  feat(data): repositories, mappers, and datastore preferences
         docs: milestone 2 documentation        ← this commit
```

Fifteen commits total. No remote configured.

----------------------------------

## Developer Notes

- **`calendarDaysRemaining` is the number to display.** Not `totals.totalDays`. They disagree
  whenever a DST transition or an odd hour falls between now and the target, and the calendar
  count is what "five days away" means to a person. `CountdownEngineCalendarTest` documents
  every divergence.
- **Never call `Instant.now()` or `LocalDate.now()`.** Inject `java.time.Clock`. The whole test
  suite depends on time being a parameter; one direct call makes its caller untestable at exactly
  the boundaries where this app breaks.
- **The domain must not return display strings.** `CountdownLabel` is a token. Milestone 3 maps
  tokens to string resources with proper plurals — resist the shortcut of a `toString()`.
- **Widget style resolution belongs to `WidgetBinding.resolveWidgetStyle(event)`.** Do not
  reimplement "override, else default" at a call site; the precedence rule exists in one place
  so two widgets on one event can genuinely differ.
- **Do not enable `fallbackToDestructiveMigration`,** in any build type, ever (D-024). If Room
  throws about a missing migration, write the migration.
- **Bumping the database version fails the build** until a migration is added — `DatabaseSchemaTest`
  asserts the count matches. That is intentional.
- **Build output is noisy** (TD-005). Filter with
  `grep -vE "^w: file:.*build.gradle.kts|Deprecated 'org"` or real warnings will hide in it.
- Commands: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew :core:domain:koverVerify` ·
  `./gradlew :app:lintDebug`. Coverage HTML lands in `core/domain/build/reports/kover/`.

----------------------------------

## Estimated Progress

```
Overall Progress            24%

Research & Architecture    100%
Project Setup              100%
Domain / Countdown Engine  100%
Database                   100%
UI                           8%   (theme + navigation shell; no real screens)
Widgets                      0%
Notifications                0%
Billing                      0%
Testing                     35%   (domain complete; DAO layer outstanding — TD-003)
Play Store                   0%
```
