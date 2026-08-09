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
