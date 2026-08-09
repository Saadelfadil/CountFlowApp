# CountFlow — Architecture Decision Record

Every architectural decision, why it was taken, what else was considered, and what it costs.
Append new decisions; never rewrite a decided entry. Change `Status` instead.

**Status values:** `Accepted` · `Provisional` (revisit at a named trigger) · `Superseded by D-nnn` · `Rejected`

---

## D-001 — Build on the canonical-layout half of Google's App Widget sample

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 0

Adopt the `glance/layout/` structure from `android/platform-samples` — the layout/data/widget
three-file split, the bitmap memory budget math, the offscreen font measurement, and the
multi-preview size annotations. Carry nothing forward from the 2023 `glance/weather/` sample.

**Reason.** The canonical layouts are well-designed and explicitly published as copy-into-your-project
components. The weather sample keeps state in `object` singletons that do not survive process death.

**Alternatives.** Build widgets from scratch; copy the whole sample wholesale.

**Tradeoffs.** We inherit two known defects that must be fixed on adoption: the font utility
inflates a real `TextView` per call, and it uses the deprecated `scaledDensity`.

---

## D-002 — Room is the single source of truth; no snapshot layer for the MVP

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 0

Widgets read event data directly from Room in `provideGlance`. The per-widget `CountdownSnapshot`
persisted through a custom `GlanceStateDefinition`, proposed in ARCHITECTURE.md §4.3, is **not**
built yet.

**Reason.** Owner chose simplicity for the MVP over an optimization that has not been shown to be
necessary. Snapshot caching can be introduced later if profiling justifies it.

**Alternatives.** Hybrid Room + snapshot (originally recommended); snapshot only.

**Tradeoffs.** `:widget:glance` will depend on the data layer rather than on a narrow snapshot
contract, so the widget layer is less liftable into a future app than it would otherwise be, and
widget rendering is coupled to database availability. Both are recoverable — introducing the
snapshot later means inserting a mapper, not restructuring.

**Revisit when:** widget update profiling exceeds the 100 ms budget, or the widget layer is
actually extracted for reuse.

---

## D-003 — `:core:domain` is a pure Kotlin/JVM module

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

The domain module applies `countflow.jvm.library`, not the Android library plugin, and depends
on nothing — not even `:core:common`.

**Reason.** Structural enforcement beats convention. An accidental `import android.*` in the
domain layer becomes a compile error rather than something a reviewer has to notice.

**Alternatives.** Android library module with a lint rule; a module-graph assertion test.

**Tradeoffs.** Domain cannot use Android types even where convenient — no `Context`, no
`android.util`. That is the point, but it means the `Clock` abstraction and any resource-backed
strings must be modelled as interfaces resolved higher up.

---

## D-004 — Widgets split into `:widget:engine` and `:widget:glance`

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

`:widget:engine` owns scheduling, render-model mapping, and progress-ring rendering, and knows
nothing about countdowns. `:widget:glance` owns the CountFlow-specific Glance presentation.

**Reason.** The owner asked for the architecture to be reusable by future widget apps. A single
`:widgets` module could not be lifted without dragging countdown concepts along.

**Alternatives.** One `:widgets` module, as originally suggested.

**Tradeoffs.** One more module boundary to maintain, and some indirection when a change spans
both. D-002 weakens the payoff, since the Glance layer now reaches into data directly.

---

## D-005 — Opt out of AGP 9's built-in Kotlin and new DSL

**Date:** 2026-08-08 · **Status:** Provisional · **Milestone:** 1

`gradle.properties` sets `android.builtInKotlin=false` and `android.newDsl=false`.

**Reason.** AGP 9 enables built-in Kotlin by default, which refuses to coexist with the
`org.jetbrains.kotlin.android` plugin. KSP, Hilt, and the Compose compiler plugin are all
documented and tested against the standard Kotlin Gradle plugin. Google's own `platform-samples`
repository sets both flags at the same AGP version. Since Hilt and Room both require KSP,
pioneering the built-in-Kotlin path would have put the entire DI and persistence stack on
unproven ground.

**Alternatives.** Migrate to built-in Kotlin now; drop to AGP 8.x (which would forfeit
compileSdk 37).

**Tradeoffs.** Both flags are deprecated and **removed in AGP 10**. A migration is required
before upgrading past AGP 9.x. Tracked as TD-001.

**Revisit when:** upgrading to AGP 10, or when KSP and Hilt document built-in-Kotlin support.

---

## D-006 — Kotlin 2.3.21 with KSP 2.3.11, not Kotlin 2.4.0

**Date:** 2026-08-08 · **Status:** Provisional · **Milestone:** 1

**Reason.** Kotlin 2.4.0 is released, and Google's sample uses it — but the sample has no
annotation processing. KSP's newest release (2.3.11) still builds against Kotlin 2.3.20. Pairing
Kotlin 2.4.0 with a KSP built for 2.3.x risks processor failures in Hilt and, later, Room.
A matched pair is worth more than a newer minor version.

**Alternatives.** Kotlin 2.4.0 + KSP 2.3.11 and hope; KAPT instead of KSP (see D-010).

**Tradeoffs.** One Kotlin minor version behind the latest.

**Revisit when:** KSP publishes a release built against Kotlin 2.4.x.

---

## D-007 — Glance 1.1.1 stable, not 1.3.0-alpha02

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 1

**Reason.** Everything CountFlow needs — `Scaffold`, `TitleBar`, `CircleIconButton`,
`GlanceTheme`, `SizeMode.Exact`, `GlanceStateDefinition`, `AndroidRemoteViews` — shipped in
1.1.0. An alpha dependency in a Play Store release is a support burden with no offsetting
benefit.

**Alternatives.** 1.3.0-alpha02, which Google's own sample uses.

**Tradeoffs.** 1.1.1 dates from October 2024. Newer Glance features (snap scrolling, the
expressive toolbar) are unavailable. None are needed.

---

## D-008 — Battery strategy replaces the originally specified refresh tiers

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 1 (implemented in 4 and 8)

Three layers: a native `Chronometer` embedded via `AndroidRemoteViews` for the final 24 hours
(zero app wakeups, ticked by the launcher); one coalesced `AlarmManager.setAndAllowWhileIdle`
alarm for the whole app at the next instant any widget's text would change; and event-driven
invalidation on time, timezone, date, locale, boot, and package-replaced broadcasts.
`updatePeriodMillis` is never used.

**Reason.** The originally specified "last day → every minute" tier is not implementable:
`PeriodicWorkRequest` has a hard 15-minute floor. Waking the process every 60 seconds per widget
would also contradict the project's own battery requirement. Coalescing is O(1) wakeups
regardless of widget count, where per-widget tiers are O(N). Day-granularity widgets only change
at local midnight, so one daily alarm is both cheaper than the specified 6-hourly poll and more
accurate, because the number flips exactly when the date does.

**Alternatives.** The specified per-widget tiers; exact alarms (needs a restricted permission);
foreground service (unjustifiable).

**Tradeoffs.** `Chronometer` supports only `H:MM:SS`-shaped formats and cannot be themed as
richly as Glance text, so it applies only to the final-24-hour tier. Its base is
`elapsedRealtime`, so it must be re-based on `ACTION_BOOT_COMPLETED`.

---

## D-009 — Firebase, AdMob, and Play Billing stay behind no-op interfaces until Milestone 9

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

`:core:analytics` and `:core:billing` expose interfaces with inert implementations. Neither SDK
is on the classpath.

**Reason.** Both add measurable cold-start cost against a sub-700 ms target, and having them on
the classpath would make every other module harder to unit test.

**Alternatives.** Wire them now; omit the modules entirely until needed.

**Tradeoffs.** The real integrations may surface constraints that the interfaces do not
anticipate, requiring interface changes in Milestone 9.

---

## D-010 — KSP, not KAPT

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

**Reason.** KAPT runs a full Java annotation-processing round and is measurably slower across a
14-module graph. Hilt and Room both support KSP.

**Tradeoffs.** Couples the build to KSP's Kotlin-version compatibility — which is exactly what
forced D-006.

---

## D-011 — Type-safe navigation with `@Serializable` route types

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

Routes are `@Serializable` objects and data classes rather than string patterns. Each feature
contributes a `NavGraphBuilder` extension and exposes navigation only as callbacks.

**Reason.** A mistyped route becomes a compile error instead of a runtime crash, and arguments
are type-checked. Callback-based navigation keeps features from depending on one another —
`:feature:events` has no compile-time knowledge that `:feature:settings` exists.

**Alternatives.** String routes; a shared `:core:navigation` module holding all route definitions.

**Tradeoffs.** `:app` must wire every cross-feature edge explicitly. That is a feature, not a
cost: it makes the full navigation surface readable in one file.

---

## D-012 — compileSdk 37, targetSdk 36, minSdk 31

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 1

**Reason.** From **31 August 2026** Google Play requires new apps and updates to target API 36
or higher, so targetSdk 36 is a floor rather than a preference. compileSdk 37 gives access to
the newest APIs at compile time. minSdk 31 means `java.time` is native (no desugaring), dynamic
color is always available (no version guards), and adaptive icons need no PNG fallbacks.

**Tradeoffs.** compileSdk > targetSdk produces a permanent lint `OldTargetApi` warning. Accepted
deliberately; see TD-004. Requires `platforms;android-37.0` and `build-tools;37.0.0` installed.

---

## D-013 — Widget style belongs to the widget binding, not the event

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 0 (implemented in 2 and 4)

A `widget_binding` record maps `appWidgetId → eventId + style overrides`. The event carries only
a default that a new widget inherits.

**Reason.** One event can appear as several widgets, and the entire point of that is showing it
different ways. Style on the event makes that impossible.

**Tradeoffs.** Two places to read style from when rendering; resolution order (binding override,
else event default) must be applied consistently in one helper.

---

## D-014 — Target time stored as epoch millis plus zone id plus an all-day flag

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 0 (implemented in 2)

`targetEpochMillis: Long`, `targetZoneId: String`, `isAllDay: Boolean`. Never a naive
`LocalDateTime`.

**Reason.** A countdown that stores a naive local date-time breaks when the user travels or when
a DST boundary falls before the target. "New Year's Eve" is all-day and should follow the
device's zone; "my flight at 14:05" is an instant pinned to the departure zone. The model has to
express both.

**Tradeoffs.** Every read must resolve against a zone, so the countdown engine takes a zone
parameter rather than assuming the system default.

---

## D-015 — "Today" and "Tomorrow" are calendar comparisons, not duration arithmetic

**Date:** 2026-08-08 · **Status:** Accepted (owner decision) · **Milestone:** 0 (implemented in 2)

The engine compares `LocalDate` values in the target zone.

**Reason.** Dividing a duration by 86,400,000 is wrong roughly half the time: 23 hours can span
two calendar days and 25 hours can span one.

---

## D-016 — R8 disabled until Milestone 8

**Date:** 2026-08-08 · **Status:** Provisional · **Milestone:** 1

`isMinifyEnabled = false` on release.

**Reason.** The first release build should be verifiable without maintaining keep rules for a
codebase that does not exist yet.

**Tradeoffs.** Release APK size and startup are unrepresentative until Milestone 8. Keep rules
will be needed then for serialization route classes, Room entities, and reflectively referenced
Glance/RemoteViews classes.

**Revisit when:** Milestone 8.

---

## D-017 — Convention plugins built before any module

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 1

**Reason.** With 14 modules, per-module Gradle configuration duplicated 14 times is unmaintainable
and drifts immediately. Building `build-logic` first meant every module's build script is a
plugin alias, a namespace, and its dependencies — nothing else.

**Tradeoffs.** Convention plugins are compiled Kotlin without version-catalog type-safe accessors,
so they look up the catalog by name. AGP DSL changes hit them first, which is exactly what
happened during this milestone.

---

## D-018 — `java.time` rather than `kotlinx-datetime`

**Date:** 2026-08-08 · **Status:** Provisional · **Milestone:** 2

**Reason.** The countdown engine lives or dies on calendar correctness, and `java.time` has the
strongest DST and calendar semantics available: `ZonedDateTime`, zone rules, `ChronoUnit`
stepping that re-anchors after each unit, and `atStartOfDay` returning a real instant on days
where midnight does not exist. At `minSdk 31` it is native on device, so there is no desugaring
cost. `kotlinx-datetime` is a thinner wrapper over the same platform types.

**Alternatives.** `kotlinx-datetime`, which was in the catalog after Milestone 1 and has now
been removed.

**Tradeoffs.** `java.time` is JVM-only. **This is the single thing in `:core:domain` that would
have to change if CountFlow ever became a Kotlin Multiplatform project** — everything else in
the module is plain Kotlin. Recorded explicitly because the Session 2 brief said "Use Kotlin
Native", which was read as native Android rather than KMP.

**Revisit when:** multiplatform is genuinely on the table.

---

## D-019 — `:core:database` depends on `:core:domain`

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

Entities hold real domain enums; Room's converters serialise them.

**Reason.** This decision was made, then reversed mid-session. The first attempt kept the
database module independent of the domain, with every enum column typed as `String`. That is
defensible on purity grounds and worse in practice: it makes every column stringly typed, moves
enum parsing into hand-written mapper code, and leaves converters with nothing to do. The
database is part of the data layer, and the data layer depending on the domain is the correct
direction — not a violation.

**Alternatives.** The stringly-typed independent module described above.

**Tradeoffs.** A domain enum rename becomes a schema migration. That is true either way, since
values are persisted by name; the dependency just makes it visible at compile time.

---

## D-020 — Enums are persisted by name, never by ordinal

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

**Reason.** An ordinal is smaller and is a live grenade. Inserting a constant into the middle of
an enum silently reinterprets every stored row — everyone's "Birthday" events quietly become
"Holiday" on the next release, with no error anywhere. Names make reordering harmless.

**Tradeoffs.** A few bytes per row, and renaming a constant is still breaking. Renaming is at
least a deliberate act that a migration can accompany; reordering is an accident waiting to
happen.

Unknown names fall back to the default rather than throwing: a row written by a newer build and
read by an older one after a Play rollback must not make the event list uncrashable.

---

## D-021 — The domain returns label tokens, not display strings

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

`CountdownLabel` is a sealed type — `Today`, `Tomorrow`, `InDays(n)` — that the UI maps to
string resources.

**Reason.** Returning `"Tomorrow"` from the domain would hard-code English and bypass Android's
resource system. It would also break plural rules: "in 1 day" versus "in 2 days" is not a
concatenation problem in every language, and some languages have more than two plural forms.

**Tradeoffs.** The UI layer must exhaustively map every token, and adding a token is a
two-module change. The compiler enforces the exhaustiveness, so the second half is safe.

---

## D-022 — Calendar breakdown and unit totals are separate types

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

`CountdownBreakdown` holds remainders (1 year, 2 months, 3 days); `CountdownTotals` holds whole
counts of each unit (400 days, 57 weeks); `calendarDaysRemaining` holds the midnight count.

**Reason.** Conflating these is *the* classic countdown bug. A 400-day countdown is "one year
and change", and code that reads `days` expecting 400 gets a small number. Three names that
cannot be mistaken for one another beat one ambiguous field.

`calendarDaysRemaining` is separate again because it is the number a widget should display —
"five days away" means five sleeps, not 120 hours, and the two disagree whenever a DST
transition or an odd hour falls in between.

**Tradeoffs.** More surface on the result type. Every field is documented with which question
it answers.

---

## D-023 — All-day events are never `IMMINENT`

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

**Reason.** Found by a failing test, not by design. `IMMINENT` exists to drive second-level
ticking, and there is nothing to tick towards when the event is a whole day. Without the rule,
an all-day event read as "starting soon" from midnight onward for its entire day.

**Tradeoffs.** The hour before an all-day event begins shows "Tomorrow" rather than "Starting
soon". That is the more useful of the two.

---

## D-024 — Destructive migration is never enabled

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

`fallbackToDestructiveMigration` is absent from the Room builder in every build type.

**Reason.** It is tempting during development and it silently deletes user data. For this app
that means every countdown the user created, plus every widget on their home screen going
blank, after an ordinary update. Room throwing on a missing migration is the correct behaviour:
it fails for the developer instead of for the user.

**Tradeoffs.** Schema changes during development need a real migration or a manual app
uninstall. A test asserts the migration count matches the version number, so the omission is
caught in CI rather than in the field.

---

## D-025 — Coverage on `:core:domain` is enforced, not reported

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

Kover fails the build below 95% line coverage on `:core:domain`.

**Reason.** A coverage number nobody enforces drifts downward. The domain is the one module
where the cost of a bug is highest and the cost of testing is lowest — it is pure Kotlin with
no Android, no I/O, and an injected clock.

**Tradeoffs.** Only `:core:domain` is gated. Applying the same bar to UI modules would reward
writing shallow tests for composables; that is not where correctness lives.

Current: **99.4% lines, 94% branches**, with the `countdown` and `model` packages at 100%.

---

## D-026 — `java.time.Clock` is injected directly, with no wrapper

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

**Reason.** `java.time.Clock` *is* a clock abstraction: an injectable type with a ready-made
`Clock.fixed` for tests. A bespoke `CountFlowClock` interface would add a layer whose only
function is to require its own fake.

**Tradeoffs.** Ties the domain to `java.time`, which D-018 already does. The rule that matters
is the one this enables: nothing outside the DI module calls `Instant.now()`, because that reads
the system clock straight through and makes the caller untestable at exactly the time boundaries
where a countdown app breaks.

---

## D-027 — Dynamic sorting via `CASE WHEN`, not `@RawQuery`

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 2

**Reason.** The event list sorts four ways and filters on four axes. A `@RawQuery` with a
string-built `ORDER BY` would be shorter and would give up Room's compile-time verification of
the SQL. `CASE WHEN` arms keep the query checked at build time.

Filtering and sorting stay in SQL rather than in Kotlin because the search field re-queries on
every keystroke; filtering in memory would load the whole table each time.

**Tradeoffs.** The query is uglier, and adding a sort option means editing SQL rather than
Kotlin. Verified SQL is worth it.


---

## D-028 — `:core:designsystem` is the shared presentation layer, not a generic component library

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 3

It depends on `:core:domain` and owns the mapping from domain tokens to text and colour.

**Reason.** `CountdownLabel` has to become a string somewhere, and that somewhere needs Android
resources. Putting it in `:feature:events` would leave the widget layer unable to reach it, and
widgets need the same "Tomorrow" the app shows. Both features and widgets already depend on the
design system, so it is the one place that serves everyone.

**Alternatives.** A new `:core:ui` module — correct in the abstract, and module creation
mid-session for one file is churn. `:feature:events` — leaves widgets stranded.

**Tradeoffs.** "Design system" now means more than buttons and colours. The name is slightly
wrong for what the module is; the alternative was a fourth naming convention.

**Revisit when:** the widget layer lands and the sharing is exercised for real.

---

## D-029 — UI models, with tokens deliberately left unresolved

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 3

Compose consumes `EventCardUiModel`, never `Event`, `CountdownResult`, or `EventTarget`. But the
UI model still carries `CountdownLabel` and `EventCategory` as tokens rather than finished
strings.

**Reason.** The first half keeps time semantics out of composables — the moment a screen can
reach `event.target`, someone computes a day count in a composable and gets it wrong in a way no
test catches. The second half is about locale: baking strings into the model freezes a row in
whatever language was active when the flow last emitted, and a language change would not
re-render it.

**Alternatives.** Resolving strings in the mapper, which would make the mapper require
`Resources` and lose locale reactivity.

**Tradeoffs.** "No domain objects in Compose" is therefore not literally true — two plain enums
and one sealed token cross the line. They carry no behaviour and no time semantics, which is the
property that actually matters.

---

## D-030 — Mapping a list uses one shared instant

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 3

`EventUiMapper.mapAll` takes `now` as a parameter rather than reading the clock per row.

**Reason.** Reading `clock.instant()` inside the loop lets the first and last rows of a long list
straddle midnight, so two identical events show different day counts. Rare, and it looks exactly
like data corruption to a user.

---

## D-031 — Search text and list options are separate flows

**Date:** 2026-08-08 · **Status:** Accepted · **Milestone:** 3

`EventsViewModel` holds the raw query in one `StateFlow` and sort/filter in another; the state
combines the raw query with a debounced copy driving the query.

**Reason.** Three requirements that a single debounced input cannot satisfy together: the text
field must show the keystroke immediately, the database must not be queried per keystroke, and a
sort tap must not inherit the typing delay.

**Tradeoffs.** One user action can produce two emissions — clearing filters writes both flows —
so there is a transient state between them. Compose coalesces it into one frame, but tests have
to await a settled state rather than the first emission.

---

## D-032 — Gradle's empty-test-task check is disabled

**Date:** 2026-08-08 · **Status:** Provisional · **Milestone:** 3

`failOnNoDiscoveredTests` is set to false in the shared convention.

**Reason.** Gradle 9 fails a test task that discovers nothing, assuming misconfiguration. Eight
CountFlow modules are deliberately empty scaffolds.

**Tradeoffs.** A module whose tests silently stopped being discovered would go unnoticed. Test
counts are reported per module in every session summary, so a count dropping to zero is visible
there.

**Revisit when:** the scaffold modules have code and tests of their own.


---

## D-033 — `:widget:engine` becomes pure Kotlin/JVM

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 4

Reverses the Milestone 1 scaffold, which applied `countflow.android.library`.

**Reason.** The same structural argument as D-003. `WidgetRenderModel`, `WidgetThemeResolver`,
and `WidgetProgressEngine` need nothing an Android SDK provides, and "widgets should only
render" is best enforced as a compile error rather than a convention someone has to remember.
The one thing that does need Android — `Context`, `GlanceId`, `AppWidgetManager` — belongs in
`:widget:glance`, which is exactly the boundary this module exists to draw.

**Tradeoffs.** `:widget:engine` cannot depend on `:core:common`'s dispatcher qualifiers or
logging facade, the same restriction `:core:domain` already accepts. None of Milestone 4's code
needed them.

---

## D-034 — Two presentation rules moved from `:feature:events` into `:core:domain`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 4

`CountdownResult.showsMeaningfulDayCount` and `EventCategory.defaultEmoji`, both written in
Session 4 as private helpers inside `EventUiMapper`, moved to live beside `CountdownLabel` and
`EventCategory` respectively.

**Reason.** The widget engine needs the identical answers the app's event list already computes
— an app row and a widget showing the same event must never disagree about whether "1" next to
"Tomorrow" is worth drawing, or what emoji a category defaults to. Moving them *before*
`WidgetRenderMapper` was written, rather than after, meant there was never a moment where two
independent copies existed to drift apart.

**Alternatives.** Duplicate the logic in `:widget:engine`; introduce a new shared module.
Duplication was rejected on the same grounds `FakeEventRepository`'s design note gives — two
approximations of one rule is how a green test suite stops meaning anything. A new module was
unjustified for two small functions when `:core:domain` already serves both consumers.

**Tradeoffs.** None found. Neither function carries a localisation concern — an emoji is fixed
Unicode data, and the day-count rule is a decision, not text — so neither conflicts with
`CountdownLabel`'s own reason for staying out of the domain (D-021).

---

## D-035 — Click targets are `ActionCallback`s, not `actionStartActivity<MainActivity>()`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 4

**Reason.** `:widget:glance` cannot reference `MainActivity` without depending on `:app`, and
`:app` depends on `:widget:glance` — the reverse would invert the module graph. An
`ActionCallback` receives a real `Context` at click time and can ask the `PackageManager` for
whatever the launcher would open, which also means a future `MainActivity` rename cannot
silently break the tap target the way a hardcoded `ComponentName` string could have.

**Alternatives.** A `ComponentName` built from a string literal naming the class — works, but
is a silent, undetectable coupling to a class this module cannot see at compile time.

---

## D-036 — The widget refresh scheduler is a seam, not Milestone 8's strategy

**Date:** 2026-08-09 · **Status:** Accepted (implements the seam proposed in D-008) · **Milestone:** 4

`GlanceWidgetRefreshScheduler` keeps widgets current only while the app process is alive, by
observing `EventRepository.observeEventsWithWidgets()` — a Room-backed `Flow` that already
re-emits on any relevant change — and redrawing on each emission. It also runs
`WidgetLifecycleCoordinator.pruneOrphans()` once at startup.

**Reason.** Building the full alarm-based coalesced scheduler now would be scope creep against
this milestone's actual goal: proving the engine architecture with one working widget. Because
Room is always the source of truth (D-002), a widget under this scheme is never *wrong* between
redraws — only stale while the app is backgrounded — and that staleness window is precisely what
D-008's Milestone 8 scheduler exists to close.

**Tradeoffs.** No update while the process is dead, and no second-level ticking for the final
day. Both are explicitly out of scope for this milestone and tracked for Milestone 8.

**Revisit when:** Milestone 8.

---

## D-037 — Widget bindings cannot be excluded from backup at the table level

**Date:** 2026-08-09 · **Status:** Accepted (limitation, mitigated) · **Milestone:** 4

Android's `data-extraction-rules` operate on whole files — a database, a `SharedPreferences`
file — not on individual tables within one. `widget_bindings` shares `countflow.db` with
`events` and `reminders`, which must stay backed up, so the table cannot be excluded without
excluding everything else in the same file.

**Reason for not fixing it structurally.** Splitting bindings into a second Room database would
make table-level exclusion possible, but is a real schema and migration change, not a manifest
edit — too large for what this session budgeted.

**Mitigation.** `WidgetLifecycleCoordinator.pruneOrphans()`, run at every app startup, discards
any binding whose `appWidgetId` the launcher does not currently report live. Since
`appWidgetId` values are allocated per device, a restored binding can never coincidentally
match a real id on a new device, so the mitigation is exact, not probabilistic — restored rows
survive the backup but stop pointing at anything the moment the app first checks.

**Revisit when:** the binding table's write volume or size ever justifies a second database on
its own merits, independent of this concern.

---

## D-038 — `hasText` in Glance's testing library is always a substring match

**Date:** 2026-08-09 · **Status:** Accepted (documents a verified API detail) · **Milestone:** 4

Verified directly against the `glance-testing` 1.1.1 artifact rather than assumed: `hasText`'s
second parameter is `ignoreCase`, not `substring` — the function has no exact-match mode of its
own. `hasTextEqualTo` is the separate, dedicated function for exact matching.

**Reason this is recorded.** A test asserting `hasText("12")` against a widget also showing
"In 12 days" passes regardless of whether the standalone headline number was ever drawn, because
both nodes match "12" as a substring. This cost a debugging round trip during this milestone and
is worth a permanent note so a future test author does not repeat it.

---

## D-039 — Forced-background widget themes resolve their own text and progress-track colors

**Date:** 2026-08-09 · **Status:** Accepted (closes a real gap, not a new abstraction) · **Milestone:** 4

`CountdownWidgetContent` no longer pulls `onSurface`/`onSurfaceVariant`/`surfaceVariant` from
`GlanceTheme` unconditionally. When `WidgetTheme.backgroundColorArgb` is non-null — OLED's forced
true black, Glass's forced translucent dark surface — text and the progress track instead come
from a small fixed `ForcedBackgroundPalette`. `WidgetTheme.isHighContrast` (true for OLED and
Modern) is applied the same way for themes that keep the *dynamic* background but still ask for a
stronger pass: it skips the muted `onSurfaceVariant` tone for full-emphasis `onSurface`.

**Reason.** `GlanceTheme`'s on-colors are tuned to pair with the *dynamic* Material You surface —
the one that tracks wallpaper. Two named themes deliberately force a background of their own
instead, and nothing guaranteed the two color sets agreed; a light dynamic scheme could produce
washed-out "on-surface" text over OLED's forced black. This also closed a dead field:
`WidgetThemeResolver` had computed `isHighContrast` since this milestone began, but nothing
downstream ever read it until now — the renderer applied every resolved color except this one.

**Alternatives.** Leave `isHighContrast` unread (the state at the start of this session) — rejected
because a resolver computing a value nothing consumes is worse than not computing it, and because
Accessibility was an explicit goal for finishing this widget. A themeable `ColorScheme` object
passed through `WidgetTheme` — rejected as more abstraction than four fixed colors need; see
D-040's note on the same session's architectural-stability instruction.

**Tradeoffs.** `ForcedBackgroundPalette` is a second, small, hand-picked color set that must be
kept legible if `WidgetThemeResolver`'s forced backgrounds ever change. It lives as a private
object inside `CountdownWidgetContent.kt`, not a new cross-module abstraction — there was nothing
to inject and no second caller.

---

## D-040 — `showPercentageText` is conjoined in the mapper, not left for the renderer to derive

**Date:** 2026-08-09 · **Status:** Accepted (closes a real gap, not a new feature) · **Milestone:** 4

`WidgetBinding.showPercentage` has existed since Milestone 2 — persisted through Room, mapped
through `:core:data` — but nothing in `:widget:engine` or `:widget:glance` ever read it; the
progress percentage had no way to reach the screen no matter how the field was set.
`WidgetRenderModel` gains one field, `showPercentageText`, computed in `WidgetRenderMapper` as
`binding.showPercentage && progress.isVisible` — never `true` when there is no bar to show the
number beside.

**Reason.** This is "finish the first widget," not "add a feature": the field was already part of
what a binding configures, already flowing through every layer up to the mapper, and silently
dropped exactly one hop from the screen. The conjunction belongs in the mapper, not the renderer,
for the same reason `showDaysValue` is pre-computed rather than derived from `CountdownLabel` in
Glance code: the renderer should read one boolean, not re-derive a business rule from two other
fields it happens to have in hand.

**Alternatives.** Leave it unwired until a settings screen exists to set it — rejected because the
field already defaults to `false` via `WidgetBinding.inheriting()`, so wiring it changes no
default behavior today and only stops being silently broken. Compute the conjunction in the
renderer instead of the mapper — rejected on the same "widgets should only render" grounds every
other visibility flag in `WidgetRenderModel` already follows.

**Tradeoffs.** None found. No UI sets `showPercentage` to `true` yet, so this is currently
inert for every real binding — its value is in being correct the day a settings screen does set
it, rather than needing a second fix at that point.

---

## D-041 — GLASS's translucent background alpha raised from 0x99 to 0xCC

**Date:** 2026-08-09 · **Status:** Accepted (contrast fix, found by review) · **Milestone:** 4.5

`WidgetThemeResolver.TRANSLUCENT_DARK_SURFACE`'s alpha channel moved from `0x99` (60% opaque) to
`0xCC` (80% opaque). A new constant, `MIN_ALPHA_FOR_RELIABLE_CONTRAST`, documents the floor this
reasoning depends on, and a regression test asserts the resolved theme never drops below it.

**Reason.** GLASS is the one named style that composites over content this app does not control —
every other style's background is either fully opaque (OLED's true black) or the system's own
dynamic Material You surface (which Android itself guarantees an appropriate on-color pairing
for). At the original 60% alpha, a fully light/white wallpaper behind the widget composites to
roughly mid-gray, and `ForcedBackgroundPalette.onSurface` (white text) measured at approximately
4.9:1 contrast against that worst case — barely above WCAG AA's 4.5:1 floor for normal-size text,
with no margin for a brighter wallpaper or a less accurate display than assumed. At 80% alpha, the
same worst case composites dark enough that the same white text measures approximately 10.8:1 —
comfortably past WCAG AAA (7:1).

**Alternatives.** A wallpaper-color-sampling approach that picks text color dynamically — rejected
as real new engineering (Android widgets have no API to read the wallpaper color behind them
without `WallpaperManager` permissions and a fair amount of new plumbing) for a Milestone
explicitly scoped to stabilization, not new capability. A fully opaque GLASS background —
rejected because it stops being "glass" in any visual sense, which is the entire point of the
style existing separately from MINIMAL.

**Tradeoffs.** GLASS is now less transparent than originally designed — less of the wallpaper
shows through. This is the deliberate trade: a "glass" effect that is illegible on a light
wallpaper is worse than one that is slightly less see-through.

---

## D-042 — `WidgetThemeResolver`, `WidgetProgressEngine`, `WidgetRenderMapper` are `internal`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 4.5

All three were `public` `object`s inside `:widget:engine` with no real caller outside the module —
`WidgetRenderModelProvider`, itself inside `:widget:engine`, was their only consumer beyond their
own tests. All three are now `internal`.

**Reason.** Session 7's brief asked explicitly for a public-API review as part of stabilizing the
architecture before treating it as settled. A module's public surface should be exactly what its
consumers need, not everything its implementation happens to expose — the wider surface was
never used by `:widget:glance` and cost nothing to close. Verified empirically before committing
to it, not just reasoned: both `:widget:engine`'s own test compilation (which needs to see
`internal` members of its module's main source set) and `:widget:glance`'s compilation (which
never referenced any of the three) succeeded unchanged.

**Alternatives.** Leave them `public` — the status quo carried no correctness cost, only an
unnecessarily wide surface. Rejected because "no cost today" is not the same as "no cost ever";
a wider surface is a standing invitation for a future caller in `:widget:glance` to reach past
`WidgetRenderModelProvider` for one of these directly, which would be exactly the kind of shortcut
`WidgetRenderModel`'s own documentation warns against.

**Tradeoffs.** None found. This is a pure visibility narrowing with no behavioral change,
confirmed by the full test suite passing unchanged before and after.

---

## D-043 — Widget `minWidth` corrected from 180dp to 110dp to actually match the declared 2×2 size

**Date:** 2026-08-09 · **Status:** Accepted (corrects a real defect, not a design change) · **Milestone:** 4.9

`countdown_widget_info.xml`'s `minWidth` moves from `180dp` to `110dp`, so it agrees with
`minHeight="110dp"` and `targetCellWidth="2"`/`targetCellHeight="2"`.

**Reason.** Android's documented widget cell-size formula is `dp = 70 × cells − 30`. Solved the
other way, `180dp` is exactly the 3-cell value (`70×3−30=180`), while `110dp` is the 2-cell value
(`70×2−30=110`). Every session since Milestone 4 documented this widget as "2×2," but `minWidth`
had been declaring a 3-cell-wide footprint the entire time — invisible until Session 8 became the
first session to reach a real widget picker on a real launcher, which reported the widget's size
as "3 × 2" before this fix and "2 × 2" immediately after, with no other change made. See
KNOWN_ISSUES.md BUG-R009.

**Alternatives.** Update every document to say "3×2" instead — rejected: the entire visual design
done in Sessions 6–7 (typography, spacing, contrast) was built and reasoned about against a 2×2
assumption throughout `ARCHITECTURE.md` and every session brief since Milestone 4; the footprint
was the error, not the design intent.

**Tradeoffs.** None found. Verified empirically, not just by formula: the real launcher's own
widget-picker label changed from "3 × 2" to "2 × 2" after this one-line change, confirmed by
screenshot (`docs/SCREENSHOT_GUIDE.md`).

---

## D-044 — Completed/expired progress bar uses the same muted color as the label

**Date:** 2026-08-09 · **Status:** Accepted (visual-consistency fix) · **Milestone:** 4.9

`CountdownWidgetContent`'s `LinearProgressIndicator` now takes `color = labelColor` (the same
value already computed for the label text — muted for completed/expired, accent otherwise)
instead of unconditionally `color = accent`.

**Reason.** Found on a real device, not by inspection: a completed or expired event showed a
label correctly de-emphasized to a muted gray, sitting directly above a progress bar still drawn
in the full, vivid accent color at 100% fill. The two elements disagreed about whether this
event still mattered visually. Reusing the already-computed `labelColor` fixes both cases with no
new state and no new branch — the condition already existed for the label.

**Alternatives.** A separate `progressColor` field on `WidgetRenderModel` — rejected as
unnecessary: the renderer already has every fact it needs (`isCompleted`, `isExpired`) via the
model, and the mapper's job is producing facts, not every possible derived color a future renderer
tweak might want.

**Tradeoffs.** None found. Verified visually on-device before and after
(`docs/SCREENSHOT_GUIDE.md`); no automated regression test was added, since Glance 1.1.1's
testing API (`glance-testing`) exposes text/content-description matchers but no way to assert a
composable's resolved `ColorProvider` value — noted as a testing-capability gap in
`KNOWN_ISSUES.md` rather than worked around with new test infrastructure.

---

## D-045 — Widget corner radius is per-style: four styles track the system value, three override it

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`WidgetTheme.cornerRadiusDp` changes from `Int` to `Int?`. `null` means "track
`android.R.dimen.system_app_widget_background_radius`" — the dimension Android itself uses to clip
a widget's outer bounds, applied via Glance's `cornerRadius(Int resId)` overload (confirmed to
accept a resource id directly, not just a resolved dp value). Minimal, Material, Progress, and OLED
resolve to `null`; Glass (20dp), Rounded (28dp), and Modern (8dp) keep fixed overrides.

**Reason.** TD-011 (open since Session 7) asked a binary question — "adopt the system value or
don't" — that turned out to have a per-style answer instead. A style resolves to `null`
specifically when it has no design reason to look rounder or squarer than whatever this launcher
considers a normal widget; a style keeps a fixed override specifically when the override *is* part
of what makes it that style — Rounded is not "Material with a bigger number," it is "rounder than
default," which is meaningless if it tracks the same default it's defined against. Each of the
seven `when` arms in `WidgetThemeResolver.resolve` carries an inline comment naming its specific
reason, not a blanket policy applied uniformly.

**Alternatives.** Adopt the system value everywhere, dropping the three fixed overrides —
rejected: Rounded's entire premise (per this session's brief) is being visibly rounder than the
system default, and Modern's editorial density was specifically designed around a crisper corner
than the softer system default typically provides. A single new fixed default for all seven,
replacing the old hand-picked 16dp — rejected for the same reason TD-011 was opened in the first
place: any fixed number, however better-chosen, still risks visibly disagreeing with the real
launcher's own outer clip mask on some device/theme combination the way the old 16dp did.

**Tradeoffs.** Four styles' actual on-screen radius is no longer a value this codebase controls or
can unit-test against a literal — it now depends on the launcher and Android version. Accepted:
that is the entire point of tracking the system value, and the three styles with a real design
reason to differ still assert one explicitly.

---

## D-046 — Countdown content hierarchy: a shared `WidgetHeadline` model, computed once, before any style renders

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`CountdownWidgetContent.resolveHeadline` computes one `WidgetHeadline` (`primary`, `unit`,
`secondary`, `showIdentity`) per render, before dispatching to any of the seven per-style layouts.
Every layout consumes this output verbatim — none re-derives what the headline or its supporting
line should say.

**Reason.** The brief named two redundancy bugs by example — "Tomorrow / Tomorrow" and "7 / Next
week" shown for every in-range count rather than only when it adds information — and both are
symptoms of the same missing rule: nothing previously decided, in one place, whether a second line
was worth drawing. Computing `WidgetHeadline` once means that rule exists exactly once. A
near-term countdown's `secondary` is the event's clock time or nothing, never the label word
again. An ordinary day count's `secondary` is populated only for `CountdownLabel.NextWeek`
specifically, because that is the one label carrying information (*which* calendar week) the
headline number doesn't already state — every other in-range label restates the number and is
correctly suppressed. Completed/expired events flip `primary` to the status word and demote the
title into `secondary`, suppressing `showIdentity` so the title isn't drawn twice.

**Alternatives.** Let each of the seven layouts decide its own hierarchy independently — rejected:
this is exactly how the original redundancy bugs happened, and seven independent
should-I-show-a-second-line decisions is seven places to get it wrong instead of one. A
`WidgetRenderModel` field computed in `:widget:engine` instead of the Glance layer — rejected: the
hierarchy decision is about *how to say something* (identical reasoning to
`CountdownLabelFormatter`'s own module boundary), not *what is true*, and belongs with the other
presentation-only decision already made in this file (`resolveColors`), not duplicated across the
domain/engine boundary.

**Tradeoffs.** None found. Unit-tested directly (`CountdownWidgetContentTest`), including two
targeted regression tests for `WidgetHeadline.isNumeric` after BUG-R011 (below) showed a headline
selection bug this model didn't originally guard against.

---

## D-047 — Determinate circular progress via a cached `Canvas`/`Bitmap` renderer, quantized to whole percent

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`CircularProgressRenderer` draws a ring to a `Bitmap` via `Canvas`/`Paint.Style.STROKE` with rounded
caps, keyed on `(sizePx, percent, trackArgb, progressArgb, strokeWidthPx)` and cached in a 32-entry
access-order `LinkedHashMap` (`removeEldestEntry` overridden as a simple LRU). `ProgressLayout`
sizes the ring at 62% of the shorter cell dimension (clamped 64–120dp) from `LocalSize`.

**Reason.** Glance's own `CircularProgressIndicator` is indeterminate-only in 1.1.1 (`LIM-001`,
confirmed against the AAR since Session 1) — there is no library path to a determinate ring at
all. `Canvas`-to-`Bitmap` is the documented workaround (`ARCHITECTURE.md` D-001's canonical-sample
adoption already anticipated this pattern generally). Quantizing `percent` to a whole number
(`WidgetProgress.percent` already existed as this exact shape, not derived from `fraction` at
render time) bounds the cache to at most 101 entries per distinct
size/color/stroke combination rather than regenerating a bitmap on every fractional-percent
recomposition.

**Alternatives.** An unbounded cache — rejected outright given `LIM-003`'s silent-drop failure
mode; a host that exceeds the `6 × screenWidthPx × screenHeightPx` bitmap budget doesn't error, it
just drops the widget, so an unbounded cache is not a "slow, but works" failure, it's a "widget
disappears with no diagnostic" one. A size-unbounded cache trimmed by total bytes rather than entry
count — rejected as more precise than this data needs: worst case at `MAX_CACHED = 32` is
`225KB × 32 ≈ 7.2MB`, already comfortably under budget for any plausible screen size, so a simpler
entry-count cap was preferred over byte-accounting complexity with no real payoff.

**Tradeoffs.** The ring only renders for a numeric headline (`headline.isNumeric`) — a word-shaped
headline ("Completed") falls back to the plain centered text every other centered style uses,
rather than being crammed into an ~80dp circle. Accepted deliberately: a status word doesn't fit a
ring gracefully at any font size, and the fallback reuses an already-correct pattern rather than
inventing a second one.

---

## D-048 — `WidgetRenderModelProvider.preview()`: a pure, no-I/O render path for the configuration screen's live preview

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`WidgetRenderModelProvider` gains a third method, `preview(event: Event, binding: WidgetBinding):
WidgetRenderModel`, alongside the existing `observe`/`get`. It runs the same
`CountdownEngine.countdownAt` → `WidgetRenderMapper.map` pipeline as a real render, synchronously,
against an `event`/`binding` pair the caller already holds — no repository read, no database
write, safe to call on every keystroke.

**Reason.** The brief required the configuration screen's preview to "react immediately" with no
save required — every style change, toggle flip, or accent pick has to redraw a real, correctly
computed preview before anything is persisted. `WidgetConfigurationViewModel`'s entire design since
Milestone 4 rests on writing a binding **only** in direct response to `onConfirm()` — the "no
orphan bindings" guarantee its own class doc describes. A naive "write speculatively so the
preview matches the database" approach would have reopened exactly that risk to add a customize
step; `preview()` instead gives the screen the same rendering logic a real widget uses, for data
that may never be written at all.

**Alternatives.** Re-derive a simplified approximation of the render logic directly in the
configuration `ViewModel` or `WidgetPreviewCard` — rejected: this is precisely the kind of
duplicated presentation rule `TODO.md`'s "Continuous" section already warns against (D-034's
`showsMeaningfulDayCount`/`defaultEmoji` precedent), and a preview that could silently drift from
what a real render produces would be worse than no live preview at all. Write a real binding on
every change and delete it on cancel — rejected as reintroducing the orphan-binding risk Milestone
4 was built specifically to close.

**Tradeoffs.** `preview()` and `get()`/`observe()` now both live on the same class with
meaningfully different contracts (one I/O-free and synchronous, two `Flow`/`suspend`-based) —
judged a single cohesive responsibility ("produce a render model, in whatever shape the caller
needs it") rather than two, consistent with `docs/WIDGET_REVIEW.md` §2's existing read on this
same class's `observe`/`get` split.

---

## D-049 — The configuration screen's live preview is a simplified plain-Compose card, not a pixel-identical Glance reproduction

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`WidgetPreviewCard` draws a `WidgetRenderModel` with ordinary Compose Material 3 components
(`Box`/`Column`/`Text`/`CircularProgressIndicator`/`LinearProgressIndicator`), varying alignment,
background, corner radius, and ring-vs-bar by style through one composable — not seven independent
reproductions of `CountdownWidgetLayouts.kt`'s seven Glance layouts.

**Reason.** `WidgetConfigurationActivity` is a normal `ComponentActivity`; Glance has no supported
way to render a live composition inline inside one (Glance widgets exist only as `RemoteViews`
produced for a host, not embeddable Compose content). Building and maintaining seven true
duplicate layouts in plain Compose — one per style, kept in lockstep with `CountdownWidgetLayouts.kt`
by hand forever — was judged a maintenance liability disproportionate to what a *preview* needs to
do: it reuses the exact same `resolveHeadline` decision the real renderer uses (so *what* it shows
is never faked or re-derived, only *how* it's drawn is simplified), which is enough to make style,
toggle, and accent choices meaningful before saving.

**Alternatives.** Seven pixel-faithful plain-Compose reproductions — rejected as above. Rendering
the real Glance composition off-screen and capturing it as a bitmap for the preview — rejected as
substantially more engineering than this milestone's scope justified, with no confirmed Glance API
for headless/off-screen composition capture at this library version.

**Tradeoffs.** The preview is a genuine approximation, documented as such in the class's own KDoc
so a future reader doesn't mistake it for authoritative. `docs/WIDGET_DESIGN_REVIEW.md` and
`docs/SCREENSHOT_GUIDE.md`'s real on-device captures remain the source of truth for exact visual
appearance, not this preview.

---

## D-050 — Accent color picker: Dynamic Material You plus eight curated presets, no free-form RGB picker

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5A

`AccentColorPicker` (`core/designsystem`) offers one Dynamic swatch (an outlined circle with a
plain "A" text glyph) and eight fixed preset colors, wired into both the create/edit form and the
configuration screen's per-widget `accentColorOverride`.

**Reason.** The brief asked for the picker deferred since Milestone 3, explicitly scoped away from
"an unrestricted RGB picker for MVP unless already justified" — no such justification exists in
this codebase. A curated set keeps every accent legible against both forced backgrounds
(`ForcedBackgroundPalette`) and the dynamic scheme without a contrast-checking UI this milestone
has no scope to build. `AccentColor.Dynamic`/`AccentColor.Fixed` were already the modeled shape
(`WidgetThemeResolver.resolve` already branches on exactly this), so the picker fills in existing
domain surface rather than adding new representation.

**Alternatives.** A full HSV/RGB picker — rejected per the brief's explicit MVP scope note, and
because an arbitrary user-chosen color has no guarantee of meeting D-041's contrast floor the way a
curated set can be pre-verified to. `Icons.Filled.Palette` for the Dynamic swatch instead of a text
glyph — rejected: `core/designsystem`'s `build.gradle.kts` deliberately excludes
`material-icons-extended` for bundle size (a standing constraint, confirmed by grep against every
other icon usage in the codebase, which draws only from `material-icons-core`), and this is not
sufficient justification to add it back.

**Tradeoffs.** Users cannot express an arbitrary brand color for an event — acceptable for MVP
scope per the brief's own instruction; revisit only if user feedback specifically asks for it.

---

## D-051 — Countdown label policy: the Session 9 hierarchy is permanent product policy

**Date:** 2026-08-09 · **Status:** Accepted (owner decision) · **Milestone:** 5A → ratified 5B

The content hierarchy `WidgetHeadline`/`resolveHeadline` already computed (D-046) is now the
**permanent, owner-approved** countdown label policy, closing the open question carried since
Session 3. Stated explicitly, as the owner specified it, so future sessions don't need to
re-derive it from code:

| Case | Primary | Secondary |
|---|---|---|
| Ordinary count | `218 days` | — (never `In 218 days`) |
| Falls in next calendar week | `8 days` | `Next week` (adds real information the number doesn't) |
| Near-term, timed | `Tomorrow` | `8:00 AM` |
| Near-term, all-day | `Tomorrow` | — |
| Completed | `Completed` | `<Event Title>` |
| Expired | `Expired` | `<Event Title>` |

**Reason.** This is exactly what `resolveHeadline` already produces — Session 9 built the
mechanism to solve the "Tomorrow / Tomorrow" and "7 / Next week" redundancy bugs the brief named
by example, and the owner has now reviewed and approved that output as correct, not merely
"good enough to close a bug." No code changes accompany this entry; it is a ratification, not a
new decision about behavior. Recorded here specifically so no future session second-guesses or
redesigns the hierarchy without a new, deliberate reason to.

**Alternatives.** None newly considered — this entry exists to close the standing open question,
not to reopen it. Any future change to this hierarchy should be a new, numbered decision with its
own reason, not a silent drift.

**Tradeoffs.** None. Removed from every "pending approval" / "requires approval" list this entry
appears on (`TODO.md`, `SESSION_SUMMARY.md`).

---

## D-052 — BUG-011 (Force Stop recovery): no further engineering time until Milestone 8

**Date:** 2026-08-09 · **Status:** Accepted (owner decision) · **Milestone:** 5B

No further attempt will be made to recover a widget from Android's Force Stop state before
Milestone 8's alarm-based refresh infrastructure exists. The Session 9 fix (a branded
`android:initialLayout`, "CountFlow / Tap to refresh") is the final state of this bug for the
foreseeable milestones — not a placeholder awaiting a follow-up patch.

**Reason.** Force Stop cancels an app's scheduled work by Android's own design; any workaround
that "recovers" from it would mean fighting platform semantics rather than working within them —
explicitly out of bounds per this project's own standing instruction (`SESSION_SUMMARY.md`
Session 9, "do not attempt to defeat Android's force-stop semantics"). The owner has now reviewed
that reasoning directly and confirmed it: further time is better spent on Milestone 5B's
responsive-system work than on a problem whose real fix already has a scheduled home
(Milestone 8's refresh infrastructure would resolve it as a side effect, per `TODO.md`).

**Alternatives.** A "tap to retry" affordance sooner than Milestone 8 — considered and explicitly
declined by the owner for this phase, not because it wouldn't work, but because it isn't worth
displacing responsive-system work to build now. A custom periodic trigger to force redraws —
rejected outright; this is precisely the kind of new wakeup-source engineering `docs/WIDGET_REVIEW.md`
§7's battery reasoning and `D-008`'s alarm-strategy already own, and duplicating it ad hoc here
would fragment that design.

**Tradeoffs.** BUG-011 remains open and user-visible (a Force-Stopped widget still does not
recover on its own) until Milestone 8. Accepted explicitly, in writing, rather than left
ambiguous.

---

## D-053 — Responsive sizing uses `SizeMode.Exact` plus an app-owned classifier, not `SizeMode.Responsive`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5B

`CountdownGlanceWidget.sizeMode` is `SizeMode.Exact`. `CountdownWidgetContent` reads the resulting
`LocalSize` every recomposition and buckets it into a `WidgetSizeClass` (`COMPACT`/`STANDARD`/
`WIDE`) itself, via `classifyWidgetSize` (`WidgetSizeClass.kt`).

**Reason.** Glance offers two responsive modes: `Responsive(sizes: Set<DpSize>)`, which snaps
`LocalSize` to the nearest of a fixed set the app declares, and `Exact`, which reports the widget's
real current size continuously. This app already declares its supported footprints as cell counts
in `countdown_widget_info.xml` (`minWidth`/`minHeight`/`maxResizeWidth`/`maxResizeHeight`, D-056) —
the same values Android's own tooling uses to compute how a launcher's grid maps onto this widget.
Duplicating those as a literal `Set<DpSize>` for `Responsive` would mean keeping two representations
of "what sizes this widget supports" in sync by hand, one in XML cell terms and one in Compose
`DpSize` terms — and, as D-055 found out, the two are not simply related by Android's documented
formula. `Exact` sidesteps the duplication entirely: the app already needs its own size→layout
classification logic regardless (021 style×size combinations, not one), so building that logic
against the widget's genuine current size is strictly more information than building it against a
value Glance already snapped to the nearest guess.

**Alternatives.** `SizeMode.Responsive` with a hand-maintained `Set<DpSize>` — rejected for the
duplication reason above, and because `Responsive`'s snapping is designed for a small number of
curated exact layouts, which is a worse fit for a widget whose supported range (2×1 through 4×2)
spans a continuum a real host can place anywhere within, not just at the declared extremes.

**Tradeoffs.** `Exact` recomposes on every real size change, not just at declared breakpoints — a
user dragging a resize handle triggers more recompositions than `Responsive` would. Not a measured
concern at this milestone's scope (no jank reported in device testing this session); worth
revisiting if a future session measures resize-drag performance specifically.

---

## D-054 — `ProgressLayoutCompact` and `ProgressLayoutWide` never duplicate the headline inside and outside the ring

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5B

Two decisions, one rule: at `WidgetSizeClass.COMPACT`, `ProgressLayoutCompact` draws no ring at
all (falls back to the same bare-headline treatment `MinimalLayoutCompact` uses); at
`WidgetSizeClass.WIDE`, `ProgressLayoutWide`'s ring is a pure visual with `showHeadline = false`
(`CircularProgressRenderer`'s consuming `ProgressRing` helper), since the layout's left column
already draws the headline as text.

**Reason.** The `COMPACT` half: `MIN_RING_DP` (64f) is a floor below which a ring reads as a smudge
rather than a progress indicator; `COMPACT`'s real measured height (104dp, D-055) minus padding
leaves less room than that floor even before considering the headline text inside it needs room
too. Forcing the ring anyway would either overflow the card or force the ring dp below its own
documented legibility floor — both worse than a fallback that reuses an already-correct pattern.
The `WIDE` half is a bug this session found in its own new code, not a defect inherited from
elsewhere: an early version of `ProgressLayoutWide` drew the headline as text in its left column
*and* again inside the ring in its right column, caught by
`every style renders the headline exactly once at every size class` failing with "found '2' node(s)
matching... '12'" once a `WIDE`-sized test existed to catch it. Fixed by making the ring text
optional and turning it off specifically for this call site.

**Alternatives.** For `COMPACT`: shrink the ring below `MIN_RING_DP` anyway — rejected, since the
floor exists precisely because a ring that small stops communicating "progress" at all. For `WIDE`:
keep the headline inside the ring and drop the left column's text version instead — rejected,
since the left-column text is what makes the composition read as "LEFT countdown / RIGHT
visualization" (two distinct facts) rather than "one fact, illustrated twice"; the ring is more
useful as pure visual reinforcement of a reading already given in text than as the sole place to
read the number from in a layout that has room to state it plainly elsewhere.

**Tradeoffs.** A user who specifically chose Progress for its ring sees no ring at all if they
resize down to `COMPACT` — named explicitly as this session's weakest style×size combination in
`docs/RESPONSIVE_WIDGET_REVIEW.md`'s Final Report, not hidden.

---

## D-055 — Widget size thresholds are calibrated from real on-device measurements, not Android's cell-size formula

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5B

`WidgetSizeClass.kt`'s `COMPACT_MAX_HEIGHT_DP` (164f) and `WIDE_MIN_WIDTH_DP` (300f) replace an
earlier version of the same two constants (75f, 180f) that were derived from Android's documented
`dp = 70×cells − 30` cell-size formula.

**Reason.** This is a second instance of exactly the mistake BUG-R009 (Session 8) named and
recorded a lesson about: trusting a documented formula's numbers as a stand-in for what a real
device actually renders, without checking. A real 2×2 CountFlow widget on this session's Pixel
Launcher measured 172×224dp via its own resolved `LocalSize` — not the formula's 110×110dp. The
same widget resized to a real, launcher-confirmed 2×1 (confirmed both by the launcher's own
resize-handle affordance and by `AppWidgetManager`'s options bundle reporting "2×1") measured
172×104dp — not 110×40dp. Both axes were wrong by roughly 2×. The practical consequence, found on
the same real device: the original 75dp `COMPACT_MAX_HEIGHT_DP` sat so far below the real 104dp
compact height that a genuine 2×1 resize still classified as `STANDARD`, silently drawing the
wrong layout in a shrunk card rather than the dedicated compact one (`docs/RESPONSIVE_WIDGET_REVIEW.md`
has the before/after screenshots). Recalibrated to the midpoint of the two real height measurements
(104 and 224 → 164dp); re-verified correct on the same real widget after the fix.

**Alternatives.** Keep the formula-derived thresholds and treat the misclassification as acceptable
drift — rejected outright once found, for the same reason BUG-R009 wasn't left as "close enough":
a widget silently rendering the wrong composition for its actual size is a real, user-visible
defect, not a rounding error. Read the launcher's *declared* grid cell size via some other Android
API instead of hardcoding a measured constant — investigated briefly; no public, launcher-agnostic
API exists for "how many dp is one grid row on this specific launcher," which is exactly why
`SizeMode.Exact` (D-053) and an app-owned empirical threshold are the mechanism Android widgets
actually have available for this problem.

**Tradeoffs.** `WIDE_MIN_WIDTH_DP` is reasoned (extrapolated from the one real width measurement),
not measured — a real 4×2 resize was not obtained this session despite three genuine automation
attempts (`docs/RESPONSIVE_WIDGET_REVIEW.md` has the full account). Both thresholds are also
specific to this one emulator's one launcher configuration; a different launcher's grid could
render at different real dp values again, the same way this session's numbers already disagreed
with the formula. No general fix exists for that within this milestone's scope — noted as residual
risk in `KNOWN_ISSUES.md`, not silently assumed away.

---

## D-056 — Widget manifest declares a resizable 2×1-to-4×2 range, not three separate size declarations

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5B

`countdown_widget_info.xml` moves from a single fixed `minWidth="110dp" minHeight="110dp"
resizeMode="none"` declaration to `minWidth="110dp" minHeight="40dp" maxResizeWidth="250dp"
maxResizeHeight="110dp" resizeMode="horizontal|vertical"`, with `targetCellWidth`/`targetCellHeight`
unchanged at 2×2.

**Reason.** One widget provider that resizes between three footprints, not three separate
`<appwidget-provider>` declarations or three separate providers, matches how every other resizable
system/OEM widget in this session's own `dumpsys appwidget` inspection is declared (Clock's "World"
widget, Calendar, Contacts' favorites widget — all single providers with a min/max range). `minWidth`/
`minHeight` are the smallest footprint's dimensions on each axis (2×1's width, 2×1's height) — not
2×2's height reused for both, which would silently forbid ever resizing down to 2×1 despite
`resizeMode` claiming to allow it, a mistake this session caught in its own draft before shipping it.
`targetCellWidth`/`targetCellHeight` stay at 2×2 so the picker preview (TD-014, Session 9) and
default placement experience keep showing the size all seven styles were originally designed for.

**Alternatives.** Three separate declared sizes with no resize between them (add new picker
entries for "Countdown Compact" / "Countdown Wide") — rejected: this is a genuinely different
product shape (three widgets, not one resizable one) that the brief's own "one coherent responsive
design system" framing argues against; a user resizing their existing widget is a materially
better experience than needing to delete and re-add a differently-named one to get a different
size.

**Tradeoffs.** None found specific to this decision; the real, substantive tradeoff (whether the
declared min/max dp values correspond to anything a real launcher's grid will actually offer) is
D-055's finding, not a cost of this declaration shape itself.

---

## D-057 — The configuration screen's preview reads the placed widget's real size from `AppWidgetManager`, not a guess

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5B

`WidgetConfigurationActivity.currentWidgetSizeClass()` reads `OPTION_APPWIDGET_MIN_WIDTH`/
`OPTION_APPWIDGET_MIN_HEIGHT` from `AppWidgetManager.getAppWidgetOptions(appWidgetId)` for the
specific widget being reconfigured, classifies it via the same `classifyWidgetSize` the real
renderer uses, and shapes `WidgetPreviewCard` (D-049) to match — falling back to `STANDARD` only
when the host hasn't reported real dimensions yet (a fresh placement mid-configuration, or this
activity launched directly with no real host behind the `appWidgetId` at all, the same case
`onEventBound()`'s `runCatching` already tolerates).

**Reason.** D-049 already established the preview as a deliberate approximation, not a
pixel-identical reproduction — but shape (square vs. short-and-wide vs. wide-and-short) is cheap to
get right and meaningfully changes what "approximation" means: a user who has already resized their
widget to 2×1 and reopens configuration should see a preview shaped like their actual widget, not a
square that looks nothing like what's on their home screen. This is the same "assume nothing about
size" discipline as D-053 and D-055 applied to a third code path — reading a real, host-reported
value instead of a value this codebase computed and hoped matched reality.

**Alternatives.** Always preview at `STANDARD` regardless of the widget's real placed size —
rejected once resizable sizes existed at all, for the reason above. Pass the size class through
as ViewModel state instead of reading it directly in the Activity — rejected: the size is
Android-Bundle-shaped data with no bearing on the ViewModel's own responsibilities (event/style/
toggle selection), and reading it once in `onCreate` and threading it down as a plain
`WidgetSizeClass` parameter keeps the ViewModel free of an Android dependency it doesn't otherwise
need, consistent with the project's existing preference for keeping ViewModels
`SavedStateHandle`/Bundle-agnostic where reasonable.

**Tradeoffs.** The preview's shape only ever reflects the size at the moment configuration was
opened — resizing the real widget while configuration is open (an unusual but possible sequence)
would not update the preview's aspect ratio without reopening the screen. Not treated as worth
solving this milestone; the underlying `AppWidgetManager` options read has no live-update
callback this screen currently subscribes to.
