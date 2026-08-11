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

---

## D-058 — `EventLifecycleFilter` replaces `includeArchived`/`includeCompleted` on `EventFilter`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5 (Session 11)

`EventFilter.lifecycle: EventLifecycleFilter` (`UPCOMING` / `COMPLETED` / `ARCHIVED`, exclusive)
replaces the two independent inclusion flags. `EventDao.observeEvents` takes a `lifecycle: String`
parameter and picks exactly one bucket via three `CASE WHEN`-style `OR` arms, following the same
verified-SQL pattern the existing `sort` parameter already uses (D-027). An event that is both
archived and completed sorts into `ARCHIVED` — archiving is the more deliberate, final hiding
action, so it wins the tie.

**Reason.** The Session 11 brief asked for three genuinely separate tabs (Upcoming / Completed /
Archived), which needs "show only X" semantics. The two flags this replaces could only ever
express "don't hide X" — `includeCompleted = true` meant "also show completed alongside
everything else," not "show completed and nothing else." Building three tabs on top of that
would have needed the app to fetch the union and filter client-side, which is exactly the
in-memory filtering D-027 already rejected once for the same reason (a thousand events loaded to
show ten).

**Alternatives.** Add two more booleans (`onlyCompleted`, `onlyArchived`) alongside the existing
two — rejected: four interacting booleans with no enforced exclusivity is more surface for a
combination that was never meant to occur (`onlyCompleted && onlyArchived`) than one enum whose
three values are the only three states that can exist. Filter the existing inclusive union
client-side per tab — rejected on the same in-memory-filtering grounds as D-027.

**Tradeoffs.** A source-breaking change to `EventFilter`'s two-field shape. Low real cost: the
type had exactly one production call site (`EventsViewModel`) and three test call sites, all
already using named-default construction rather than positional args, so every affected site was
a mechanical rename. Verified via `EventDaoTest`'s new
`the three buckets are mutually exclusive and exhaustive` test, which reconstructs the full table
from all three bucket queries and asserts nothing is missing or duplicated.

---

## D-059 — The create/edit form's live preview depends on `:widget:engine`, not `:widget:glance`

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5 (Session 11)

`:feature:events` gains one new dependency, `implementation(project(":widget:engine"))`, to reuse
`WidgetRenderModelProvider.preview()` for `CreateEventScreen`'s new inline preview
(`EventWidgetPreview.kt`). It does **not** depend on `:widget:glance` — `WidgetPreviewCard`, the
configuration screen's own preview card (D-049), stays exactly where it is, unreused.

**Reason.** `WidgetRenderModelProvider.preview()` is pure Kotlin/JVM with zero Android or Glance
dependency (D-033) — exactly the kind of narrow, reusable seam D-004 built `:widget:engine` to be
a "future consumer" of, here realized one milestone later than expected but for exactly the
reason D-004 named. `:widget:glance`, by contrast, carries the full Glance/AppWidget runtime,
`WidgetConfigurationActivity`, all 21 size×style layouts, and the Hilt `EntryPoint` bridge — none
of which a form screen needs, and pulling it in would blur the module graph's existing shape
(`:widget:glance` is assembled only by `:app`, alongside features, not depended on by one).
`WidgetPreviewCard` and the `resolveHeadline`/`WidgetHeadline` content-hierarchy split it draws on
(D-046) are also both `internal` to `:widget:glance` — reusing them verbatim would have required
widening that module's public surface for a single new consumer outside the pattern D-042 already
established for narrowing it.

**What this means for the new preview.** `EventWidgetPreview.kt` (`:feature:events`) is a second,
independently simplified drawing of a `WidgetRenderModel` — the same category of thing
`WidgetPreviewCard` already is relative to the real 21 Glance layouts (D-049's own precedent).
It reuses every fact the model carries (resolved theme colours, corner radius, progress fraction,
`CountdownLabelFormatter`-formatted label text) and invents none of its own — no countdown math,
no theme-colour resolution — but does **not** reproduce `resolveHeadline`'s primary/secondary
line split (D-046). That split exists to resolve a redundancy problem specific to full-size
widgets showing several lines at once ("Tomorrow / Tomorrow"); a compact single-line form preview
was never at risk of that problem, so reproducing the split would have been complexity spent
solving nothing.

**Alternatives.** Depend on `:widget:glance` and widen `WidgetPreviewCard`/`resolveHeadline` to
`public` — rejected for the dependency-weight and module-shape reasons above. Depend on
`:widget:engine` and *also* reimplement the primary/secondary hierarchy split locally — rejected:
would have meant two independent copies of a presentation *decision* (not just its drawing),
which is precisely the "two approximations of one rule" duplication D-034's own precedent warns
against.

**Tradeoffs.** The create/edit form's preview and the configuration screen's preview are now two
separately maintained (if both intentionally simplified) drawings of a `WidgetRenderModel`, not
one shared component. Accepted: a genuinely shared component would need to live somewhere both
`:feature:events` and `:widget:glance` can reach without either depending on the other, which is
a real module-graph change this session's brief explicitly ruled out ("Do NOT redesign the
architecture"). Revisit if a third consumer ever wants the same preview, at which point extracting
one shared component becomes worth its own module-boundary decision.

---

## D-060 — Delete is never a swipe target; the overflow menu is the one action surface every tab and every action always has

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 5 (Session 11)

`EventCard`'s swipe gesture (`SwipeToDismissBox`) is wired only on the `UPCOMING` tab, mapping its
two directions to Complete and Archive — never Delete, on any tab. Every card, on every tab,
also carries an overflow (`⋮`) menu offering every action valid for that tab's lifecycle bucket,
including Delete with its own confirmation dialog. `COMPLETED` and `ARCHIVED` rows have no swipe
gesture at all; their actions (Restore, Delete, and — from `COMPLETED` — Archive) are menu-only.

**Reason.** Two requirements from the brief, both non-negotiable: "Do NOT make destructive
deletion too easy," and "Swipe actions must NOT be the only way to complete, archive, or delete —
provide an accessible alternative." A swipe gesture that deletes on a full-distance drag is
exactly what "too easy" describes — it can be triggered by a single, fast, accidental motion with
no confirmation step in the gesture itself. Scoping swipe to `UPCOMING` only, rather than building
three tabs' worth of swipe-direction combinations (Restore vs. Archive vs. Delete, per tab), is
the smaller mechanism for the two actions the brief actually described swiping for by name
("Complete" one direction, "Archive/Delete" the other) — read as "Archive, and separately, handle
deletion safely" rather than as an instruction to make deletion swipeable too.

**Alternatives.** Swipe-to-delete with a confirming snackbar ("Undo") instead of an upfront
dialog — a legitimate pattern elsewhere, rejected here specifically because the brief named the
upfront-confirmation shape explicitly (`Delete "Japan Trip"? ... Cancel / Delete`). Swipe gestures
on `COMPLETED`/`ARCHIVED` too (e.g., swipe-to-restore) — considered and deferred: it would need a
third state-dependent mapping for marginal gain, since the menu already reaches Restore in one
tap on tabs with only a handful of rows expected.

**Tradeoffs.** A user who wants to restore or delete from `COMPLETED`/`ARCHIVED` has only the menu,
never a faster swipe — accepted, since those two tabs are expected to be visited far less often
than `UPCOMING` and the menu is already the accessible baseline every tab needs regardless.

---

## D-061 — The empty Upcoming tab does not distinguish genuine first launch from "everything is done or archived"

**Date:** 2026-08-09 · **Status:** Accepted (deliberate scope reduction) · **Milestone:** 5 (Session 11)

The brief's mockup text names two separate empty states — "No countdowns yet / Create something
worth looking forward to" for first launch, and "Nothing coming up / Create a countdown for your
next moment" for an Upcoming tab that just happens to be empty. `HomeUiState.emptyState` collapses
these into one `NO_UPCOMING` case, using the second copy ("Nothing coming up..."), for both
triggering conditions.

**Reason.** Telling the two cases apart needs a signal `HomeUiState` does not otherwise have —
whether the user has *ever* created an event, independent of the current tab or filter. The
cheapest correct version would be a new reactive `EventRepository` method (`observeHasAnyEvents()`
backed by a `SELECT COUNT(*)` query) purely to drive copy on one screen. The brief's own
instruction — "Do not over-design these states" — was read as license to skip that plumbing:
"Nothing coming up... create a countdown for your next moment" reads correctly whether it is
truly the user's first session or they have simply completed and archived everything so far,
where "No countdowns yet" would read slightly wrong in the second case (the user does have
countdowns, just not upcoming ones).

**Alternatives.** Add `observeHasAnyEvents()` and the extra combine to distinguish the two states
precisely — rejected as more plumbing than a copy-only distinction is worth, per the brief's own
instruction. Show the first-launch copy always — rejected: reads wrong once a user has genuinely
used the app and cleared their Upcoming tab through completion or archiving, which is the more
common way to reach this state after the first session.

**Tradeoffs.** A true first-time user and a returning user with nothing upcoming see identical
copy. Accepted as the correct trade for this app's scale; revisit only if user feedback
specifically asks for a distinct first-run welcome.

---

## D-062 — `nextTransitionAt` walks a bounded set of midnight candidates, not just "the next one"

**Date:** 2026-08-09 · **Status:** Accepted · **Milestone:** 8 (background refresh infrastructure pulled forward, Session 12)

`CountdownEngine.nextTransitionAt(event, now, deviceZone)` — new pure function, `:core:domain` —
answers "when does this event's displayed countdown next change" for exactly one event. For a
still-future event it does not check only the immediate next local midnight; it walks every
midnight candidate up to `min(daysUntilTarget, config.nearFutureDays + 14)` days out, plus the
event's own start instant and (for timed events) the imminent-threshold instant, and returns the
earliest candidate whose `CountdownLabel`/`CountdownStatus` genuinely differs from now. Same-day
and past events always walk exactly one day forward.

**Reason.** Found by testing, not designed in from the start: `CountdownLabel.NextWeek`'s window
(`CountdownEngine.fallsInNextWeek`) re-anchors to a shifting `today` each day, so the label can
stay literally unchanged across several consecutive local midnights even while
`calendarDaysRemaining` visibly decreases underneath it. A scheduler that only checks the very
next midnight would schedule a wakeup, find nothing changed, and have no way to know when the
*real* next change is — silently degrading into either missed updates or, if it naively
rescheduled one day at a time regardless, exactly the "blindly refresh every widget every minute"
anti-pattern this session's brief explicitly ruled out, just at a coarser interval. Walking a
bounded superset of candidates and checking each one against the current label/status is the only
approach correct for every label transition this domain has, not just the ones a handful of
hand-picked test dates happen to exercise.

**Alternatives.** Check only the next local midnight (the initial implementation) — rejected once
the `NextWeek` plateau was found; produces wrong answers for a whole class of events. Recompute
unconditionally on a fixed interval (e.g. hourly) regardless of whether anything actually changed
— rejected as exactly the polling shape the brief named and ruled out by name.

**Tradeoffs.** The candidate list can be as long as `nearFutureDays + 14` entries for an event
still outside that window — still a handful of cheap in-memory `countdownAt` calls, not an I/O or
performance concern, but a real algorithm, not the one-line "next midnight" a first read of the
requirement suggests.

---

## D-063 — Widget refresh scheduling splits across three modules, coalesces to one alarm, and uses `setAndAllowWhileIdle`

**Date:** 2026-08-09 · **Status:** Accepted (implements D-008/D-036's planned seam) · **Milestone:** 8 (Session 12)

Replaces D-036's app-alive-only scheduler with the real one D-008 always planned, split along the
same module boundary D-004 established: `CountdownEngine.nextTransitionAt` (per-event transition
math, `:core:domain`, D-062) → `WidgetRefreshPlanner` (coalesces every bound widget's event to one
global `Instant`, deduplicated by event, `:widget:engine`) → `WidgetRefreshCoordinator`
(orchestrates a refresh cycle behind two small seams, `AlarmScheduler`/`WidgetRedrawer`,
`:widget:engine`) → `AndroidAlarmScheduler`/`GlanceWidgetRedrawer`/`WidgetRefreshReceiver` (the
real `AlarmManager`/Glance mechanics, `:widget:glance`). Exactly one
`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)` alarm exists for the whole app at any time,
targeting a fixed `PendingIntent` request code so a new schedule always replaces the old one
rather than stacking. One `WidgetRefreshReceiver` handles both the alarm firing (`ACTION_REFRESH`,
delivered only via an explicit `PendingIntent`, needing no manifest `<intent-filter>`) and the
four genuine system recovery broadcasts (`BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`,
`DATE_CHANGED`) — every one of them runs the identical
`WidgetRefreshCoordinator.refreshAndReschedule()` cycle. A `WidgetRefreshSafetyNetWorker`
(`WorkManager`, `KEEP` policy, ~6h/2h flex) is a defensive backstop, not the primary mechanism.

**Reason.** `setAndAllowWhileIdle` over `setExactAndAllowWhileIdle`: needs no
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` permission, survives Doze, and is inexact by only a few
minutes — the brief asked for the *next meaningful transition*, not millisecond precision, so
trading a few minutes of slack for zero permission burden is the correct call, and it matches
D-008's original plan exactly. One receiver for four actions, not four: a `BroadcastReceiver` can
declare more than one `<intent-filter>` action, and every one of Boot/Timezone/Time/Date means
exactly the same thing here — "the schedule might now be wrong, recompute it" — so one small class
with one `runCatching` block is both correct and the minimum surface, versus four nearly-identical
classes. Rescheduling on every `EventRepository.observeEventsWithWidgets()` emission (already
wired in `GlanceWidgetRefreshScheduler` since D-036) already covers every one of the brief's
"rescheduling triggers" — event created/edited/deleted/completed, widget added/removed/
reconfigured — because every one of those is a write to a table that `Flow` already watches; no
new receiver was needed for any of them, confirmed by real-device testing (editing an event
through the real UI produced a correctly re-scheduled real `AlarmManager` alarm, verified via
`dumpsys alarm`). `KEEP`, not `REPLACE`, on the safety-net worker: `GlanceWidgetRefreshScheduler
.start()` runs on every process start, and `REPLACE` would reset its ~6h timer every time, turning
"runs roughly every six hours" into "runs whenever the app last happened to start plus six hours"
— defeating the point of a periodic backstop.

**Alternatives.** A frequent periodic `WorkManager` job as the primary mechanism — rejected
outright, explicitly by the brief ("do NOT introduce frequent periodic WorkManager jobs merely
because they're easy") and on the same `LIM-002` grounds D-008 already rejected it on (15-minute
floor, wrong shape for a countdown that might not need to wake for days). One `AlarmManager` alarm
per widget — rejected; does not scale, and the brief explicitly required coalescing to one system
wakeup regardless of widget count. `setExactAndAllowWhileIdle` — rejected: needs a restricted
permission for no benefit this app's actual requirement (a meaningful label transition, not a
wall-clock deadline) needs.

**Tradeoffs.** A scheduled refresh can fire up to a few minutes late (the `setAndAllowWhileIdle`
inexactness window) — accepted, since nothing this app displays needs to change at the literal
millisecond a transition boundary crosses. The safety net worker means a genuinely-missed alarm
(e.g., an OEM's aggressive alarm-clearing behavior) is recovered within 6–8 hours, not
immediately — accepted as a backstop, not a promise of exactness.

---

## D-064 — `LiveDefaultZoneClock` replaces `Clock.systemDefaultZone()` — a `@Singleton` clock must not freeze the device's zone at construction

**Date:** 2026-08-09 · **Status:** Accepted (regression fix, found by this session's own real-device testing) · **Milestone:** 8 (Session 12)

`TimeModule.providesClock()` now returns a small custom `Clock` (`LiveDefaultZoneClock`) whose
`getZone()` calls `ZoneId.systemDefault()` fresh on every read, instead of
`Clock.systemDefaultZone()`, whose `getZone()` returns a `ZoneId` snapshotted once at
construction. `WidgetRefreshReceiver` also calls `TimeZone.setDefault(null)` specifically on
`ACTION_TIMEZONE_CHANGED`, busting the JVM-level cache `ZoneId.systemDefault()` itself reads
through.

**Reason.** Found live, not by inspection: this session's real-device timezone-change test
(Africa/Casablanca → America/New_York, `adb shell cmd alarm set-timezone`) showed
`WidgetRefreshReceiver` correctly receiving `ACTION_TIMEZONE_CHANGED` and correctly re-running a
full refresh cycle — but the recomputed alarm landed on the exact same absolute instant as before
the change, not the ~5-hour-shifted instant the new zone's "next local midnight" should have
produced. Root cause: `Clock.systemDefaultZone()` is documented to snapshot `ZoneId.systemDefault()`
once, into an immutable `Clock`; bound `@Singleton` (as every `Clock` in this app has been since
D-026), that snapshot is taken once per process and never updates — every long-lived consumer of
`clock.zone`, not just the refresh scheduler, silently kept computing against the zone the process
happened to start in. A traveller landing in a new zone would see stale countdowns, in the app and
in widgets alike, until the process happened to restart.

**Alternatives.** Remove `@Singleton` from `providesClock()` so each injection site gets a fresh
`Clock.systemDefaultZone()` — rejected: every real consumer (`WidgetRefreshCoordinator`, every
ViewModel) is itself a long-lived object that stores its injected `Clock` in a `val` field at
construction, so a non-singleton provider would only move the same staleness bug from "once per
process" to "once per consumer's own lifetime" — no more correct, and loses the one real benefit
of `@Singleton` (one shared, injectable, `Clock.fixed`-swappable instance for tests) for nothing.
Read `ZoneId.systemDefault()` directly at every call site instead of through `Clock` — rejected:
reintroduces exactly the "call the system clock directly, become untestable at time boundaries"
problem D-026 exists to prevent.

**Tradeoffs.** None found specific to the fix itself. This is a correctness fix to code this
project has had since D-026 (Milestone 2) — the bug existed for nine sessions before this one's
real-device timezone test was the first thing in this project's history to actually exercise a
live timezone change against an already-running process. `LiveDefaultZoneClockTest.kt`
(`:core:common`, new) is a permanent regression test, asserting directly against
`TimeZone.setDefault(...)` rather than the device.

---

## D-065 — Reminder trigger time: timed events pin to their own zone, all-day events follow the device; resolution is comparison-based, not a boolean

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 7 (Basic Event Reminders, Session 13)

`Reminder.scheduledTime(event, deviceZone)` — present since Milestone 2, unused until this session
— had one real bug and needed one new capability. The bug: it used `deviceZone` unconditionally for
the calendar-day subtraction behind "N days before," for both all-day and timed targets. The fix
uses the event's own authored zone (`event.target.zone`) for a timed target's subtraction, and
`deviceZone` only for an all-day one:

```kotlin
val zone = if (event.target.isAllDay) deviceZone else event.target.zone
```

The new capability: `deliveredForScheduledTime: Instant?`, a field compared against a *freshly
computed* `scheduledTime` (`isResolvedFor`), not read as a plain flag — so editing an event to a
new date makes an old resolution stop matching automatically, with no separate "reset on edit" code
path. `withPastTriggerResolved` applies the same primitive (`markResolved`) at write time, silently
resolving (never notifying) a reminder whose trigger has already passed the moment it is activated
or edited into the past.

**Reason.** A timed target is already zone-pinned by design — `EventTarget`'s own documentation
states a flight "stays pinned to the zone it was authored in, no matter where the phone is" — but
before this fix, only the target's own instant honored that; a reminder *about* that instant did
not, silently recomputing against wherever the device currently was. A traveller who set "seven
days before my Tokyo flight" while in Tokyo, then flew somewhere else before the reminder fired,
would have seen it silently shift. Confirmed as a real, not just theoretical, risk by a live
device timezone change this session: with the bug present, an alarm's absolute instant would have
shifted by the full zone offset; after the fix, it did not. All-day targets deliberately keep the
opposite policy, per D-014's own established "follows a traveller" precedent for that target kind
— this is the one case the brief's own suggested default ("09:00 in the event's timezone") was
read as *already satisfied* by existing, tested behavior, not requiring a second zone concept.

The idempotency design answers the brief's own explicit requirement — "a reminder must not fire
twice" — with the smallest addition that makes both the "never re-fire" and "never fire an
already-past trigger" rules the *same* code path: a reminder resolved for a reason it was never
actually sent (past at activation) is indistinguishable, from the coordinator's point of view, from
one resolved because it genuinely fired. Neither needs special-casing.

**Alternatives.** A plain `Boolean isDelivered` flag — rejected: would need explicit clearing on
every event edit that changes the target, a step easy to forget and impossible to verify by
inspection the way a comparison against the current computed value is. Storing `deviceZone` at
`Reminder` construction time and pinning to it forever — rejected: the correct zone for a timed
event is the *event's*, which is already available via `event.target.zone` on every read; storing a
device zone snapshot would just reintroduce a staleness risk of the same shape D-064 just fixed
elsewhere.

**Tradeoffs.** None found. `ReminderTest.kt` (`:core:domain`, new, 21 tests) covers both zone
policies explicitly, including a same-instant-across-zones assertion for the timed case and a
genuinely-different-instant assertion for the all-day case.

---

## D-066 — Reminder lifecycle (complete/archive/restore/delete) needs no dedicated cancellation code

**Date:** 2026-08-10 · **Status:** Accepted (documents an existing mechanism, not a new one) · **Milestone:** 7 (Session 13)

`ReminderDao`'s `ACTIVE_REMINDERS_QUERY` (`getActiveReminders`, and its new `Flow` twin
`observeActiveReminders`) already excludes a reminder whose event is archived or completed, at the
SQL level — `e.is_archived = 0 AND e.is_completed = 0`, present since Milestone 2. Session 13's
notification coordinator reads only from this query, so completing or archiving an event removes
its reminders from scheduling consideration with no code written this session, and restoring
either flag re-includes them automatically the next time the reactive query re-emits. Deletion
removes reminder rows outright via the pre-existing cascading foreign key.

**Reason.** Recorded explicitly, not left implicit, because it is exactly the kind of finding this
project's own `TODO.md` "Continuous" section warns about missing: a query already did the correct
thing, and the risk was writing *duplicate* lifecycle-cancellation logic in the new coordinator
without first checking whether the existing data layer already handled it — which would have been
two independent, potentially-diverging copies of the same rule (D-034's precedent).

**Alternatives.** An explicit `cancelRemindersForEvent(eventId)` call from `EventRepository
.setCompleted`/`setArchived` — rejected once the query-level exclusion was confirmed sufficient;
would have been a second mechanism doing what one `WHERE` clause already does correctly, and reactive
recomputation (§6 of `docs/NOTIFICATION_ARCHITECTURE.md`) means no explicit "please recompute now"
call is needed regardless.

**Tradeoffs.** None found.

---

## D-067 — Reminder notification scheduling is a separate coordinator and a separate `BroadcastReceiver` from widget refresh, sharing the pattern, not the code

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 7 (Session 13)

`ReminderNotificationCoordinator`/`NotificationAlarmScheduler`/`ReminderNotificationReceiver`
(`:core:notifications`, new) mirror `WidgetRefreshCoordinator`/`AlarmScheduler`/
`WidgetRefreshReceiver` (`:widget:glance`, Session 12, D-063) in shape — coalesce to one alarm,
`setAndAllowWhileIdle`, one receiver for the alarm plus the four system recovery broadcasts, a
`WorkManager` safety net — without sharing a class or an interface between the two systems.
Concretely, this means **two** `BroadcastReceiver`s are each independently manifest-registered for
the identical four actions (`BOOT_COMPLETED`/`TIMEZONE_CHANGED`/`TIME_SET`/`DATE_CHANGED`), and two
independent `AlarmManager` entries can exist at once, one per subsystem, using different fixed
`PendingIntent` request codes (widget: `1001`, reminders: `2001`).

**Reason.** The brief drew this line explicitly: share "time calculation patterns, zone
correctness, coalescing philosophy... platform scheduling knowledge, boot/time/timezone recovery
strategy," not widget-specific coordinator logic, redraw interfaces, or render models — and,
conversely, warned against forcing unrelated responsibilities into one class merely to reduce file
count. Reminder delivery and widget redraw are genuinely different outcomes with different
correctness properties: redrawing a widget twice from two overlapping triggers is harmless; sending
a duplicate user-facing notification is a real bug specifically named as unacceptable. Two
receivers each declaring the same four actions is not duplicated logic — it is the normal, Android-
supported way for two independent subsystems in one app to each react to "the clock might be wrong
now," the same relationship any two unrelated apps on the same device already have with each other
for the identical broadcasts.

**Alternatives.** One shared receiver dispatching to both coordinators — rejected: would require
either coordinator to know the other exists, or a third abstraction neither currently needs,
purely to save four lines of manifest XML duplicated across two modules. One shared `AlarmScheduler`
interface reused by both — rejected on the same "two independent lifecycles" reasoning; a shared
interface would invite exactly the coupling the brief warned against the moment either system's
scheduling needs diverge even slightly (which timed-vs-all-day zone handling, §4 of
`docs/NOTIFICATION_ARCHITECTURE.md`, already shows they do).

**Tradeoffs.** Two alarms, not one, can be armed simultaneously in the common case (an event with
both a placed widget and an active reminder) — a real, deliberate cost, reasoned through in
`docs/NOTIFICATION_ARCHITECTURE.md` §11 rather than hidden: at most a couple of wakeups a day, not
one, in exchange for the two systems staying independently correct and independently testable.

---

## D-068 — `:core:notifications` calls `CountdownEngine` directly for the notification body's label, and does not depend on `:core:designsystem`

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 7 (Session 13)

`AndroidNotificationSender.send` calls `countdownEngine.countdownAt(event, now, zone).label` at the
moment of delivery — the real, live label `CountdownEngine` would compute for that event right
then — and maps it to a short, notification-specific string in a small `when` inside the same
class, rather than depending on `:core:designsystem` to reuse `CountdownLabelFormatter`.

**Reason.** The brief's own rule — "do not duplicate countdown-label business logic inside the
Android notification implementation" — is about the *decision* (which label applies to an event
right now), not the final rendered string. Calling the real `CountdownEngine` satisfies that rule
exactly: the notification can never disagree with what the app or a widget would show for the same
event at the same instant, confirmed on-device when a `DAY_OF` reminder that fired a few minutes
late correctly read "Expired," not a stale "Today." `:core:designsystem` was deliberately not added
as a dependency for the remaining, presentation-only step (the label's *text*) because that module
carries Compose Material3 as an `api` dependency (D-028) for reasons entirely about serving Compose
UI and Glance — real weight for a background module whose own `send()` method never touches
Compose. This is the same "reuse the fact, not the renderer" shape D-059 already used for the
create/edit form's own preview, applied here to a third consumer.

**Alternatives.** Depend on `:core:designsystem` and call `CountdownLabelFormatter.format(resources,
label)` directly — technically works (that specific overload takes a plain `Resources`, not a
Compose scope) but pulls the whole module's Compose dependency graph onto `:core:notifications`'
classpath for one string lookup. Hand-roll the day-count decision independently in
`:core:notifications` — rejected outright as the exact duplication the brief named.

**Tradeoffs.** Notification body text is a second, independently maintained (if intentionally
minimal) string mapping, not the shared `CountdownLabelFormatter` — and, like that formatter's
callers before Milestone 6, it is not localized, consistent with this project's existing
TD-007-tracked gap rather than a new one.

---

## D-069 — Global Appearance controls the app's own UI only; widgets keep their independent theme/style/accent rules

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 6 (Session 14)

The Theme (System/Light/Dark) and Dynamic Color preferences added this session apply to
`CountFlowTheme`, which wraps only the app's own Compose screens (`CountFlowNavHost`, reached via
`MainActivity`). `MainActivity` reads `PreferencesRepository.preferences` directly as Compose state
— `collectAsStateWithLifecycle`, no dedicated ViewModel — and derives `CountFlowTheme`'s two
existing parameters (`darkTheme`, `dynamicColor`) from it every recomposition. Placed Glance widgets
on the home screen render through a completely separate system (`GlanceTheme`, per-widget
`WidgetTheme`/style/accent overrides, Milestone 4/5) that this session does not touch at all, and a
real-device regression check confirmed it: two placed widgets kept their existing dark, translucent
styling and blue accent unchanged through Light, Dark, and Dynamic-Color-off/on transitions in
Settings.

**Reason.** The brief asked explicitly whether this setting should affect "App UI only" or "App UI
+ widgets," with the recommended MVP policy being app-UI-only, "if this matches the current
architecture." It does — the two rendering systems (Compose `MaterialTheme` for the app,
`GlanceTheme`/`RemoteViews` for widgets) already had zero shared theming code before this session,
so app-UI-only is not a restriction added on top of the architecture, it is simply not building a
new cross-cutting theming bridge that nothing before this session needed. Per-widget style/accent
customization (D-041 and others) is a deliberate, event-scoped user choice made in the
widget-configuration flow; a global app theme switch silently overriding it would be a real
regression to that feature, not a refinement.

**Alternatives.** Propagate the app's `ThemeMode`/`useDynamicColor` into `GlanceTheme` too, so a
widget's neutral tones shift with the app's light/dark choice — rejected: widgets already have
their own explicit theme concept (`WidgetTheme`: OLED, Glass, Material, and others) that has nothing
to do with the phone's system theme, and conflating the two would mean either ignoring per-widget
overrides some of the time or building a precedence rule nobody asked for. A shared
`AppThemeViewModel` injected into both `MainActivity` and `WidgetConfigurationActivity` — deferred:
`WidgetConfigurationActivity`'s own Compose chrome (not the widgets it configures) still follows the
system theme unconditionally, unchanged by this session, since the brief scoped this work to "the
app," or which activity is or isn't "the app" is a call outside its explicit examples, and the
brief also warned against unrelated widget-adjacent work. Worth revisiting if that inconsistency is
ever reported as a real user-facing rough edge.

**Tradeoffs.** `WidgetConfigurationActivity` (reached only via a launcher's "reconfigure" affordance,
not through CountFlow's own navigation) does not follow the in-app theme preference — a narrow,
deliberately accepted inconsistency, not a bug, since real-device testing showed it does not affect
the actual widgets a user places, only one configuration screen's own chrome.

---

## D-070 — Notification status uses `areNotificationsEnabled()`, not a raw permission check, and refreshes on every screen resume

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 6 (Session 14)

The Settings screen's "Event reminders: Allowed/Not allowed" row is backed by
`NotificationManagerCompat.from(context).areNotificationsEnabled()`
(`NotificationStatusProvider`/`AndroidNotificationStatusProvider`), not
`ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)` — the check
`AndroidNotificationSender` uses to gate an actual `notify()` call (D-065's session). `SettingsView
Model` re-reads it via `refreshNotificationStatus()`, called from a `LifecycleResumeEffect` in the
Composable on every screen resume, not just once at construction.

**Reason.** `POST_NOTIFICATIONS` is a runtime permission only from API 33; below that,
`checkSelfPermission` for it returns `GRANTED` unconditionally regardless of whether the user has
actually disabled notifications for the app through the classic per-app toggle that has existed
since API 26 — displaying "Allowed" in that state would be exactly the "misleading messaging on
versions where runtime permission does not exist" the brief explicitly forbade.
`areNotificationsEnabled()` answers the question the row actually asks — "will a CountFlow
notification reach this user right now" — correctly and uniformly on every supported API level,
confirmed on the real device by disabling and re-enabling notifications through Android's system
settings and watching the row flip both directions without restarting CountFlow. Refreshing on
resume (not just construction) is what makes that flip visible at all: the user's path to changing
this state — Settings → "Manage notifications" → Android's settings → back — always leaves and
returns to the same screen instance, which construction-only logic would never re-run for.

**Alternatives.** Poll on a timer while the screen is visible — rejected: resume is the only moment
the value can plausibly have changed (the user can't reach Android's notification settings without
leaving CountFlow first), so a timer would only add battery cost for no additional correctness.
Check `POST_NOTIFICATIONS` directly and branch UI copy on `Build.VERSION.SDK_INT` — rejected as
strictly more code for a worse answer than the version-uniform API already gives.

**Tradeoffs.** None found — `areNotificationsEnabled()` is the API Android's own documentation
recommends for exactly this "will my notifications be seen" question, and imposes no cost
`checkSelfPermission` doesn't already have.

---

## D-071 — Settings does not surface a Premium/Upgrade entry point this session

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 6 (Session 14)

The Milestone 1 placeholder `SettingsScreen` had a "CountFlow Premium" action navigating to
`:feature:premium`'s placeholder screen. The real Settings screen built this session does not
include it; `onNavigateToPremium` was removed from `SettingsScreen`'s and `settingsSection`'s
signatures. `:feature:premium`'s route stays registered in `CountFlowNavHost` — nothing about the
module or its navigation graph entry was deleted, only the link to it from Settings.

**Reason.** The brief's exclusion list is explicit and unambiguous: "No Billing. No AdMob. No
subscriptions. No Pro features," and separately warns "do not add a feature simply because Settings
seems like a convenient place for it." A visible "Premium" row in a shipped-feeling Settings screen
reads as a real, present paywall entry point regardless of what the screen behind it currently does
— it is a promise about the product's shape that this session, and this milestone, does not make.
Removing it is the more accurate representation of what CountFlow's MVP actually offers today, not
a regression: nothing reachable from the real Settings screen was taken away, since it was only
ever ready from a placeholder screen no prior session considered load-bearing.

**Alternatives.** Keep the row, pointing at the existing placeholder — rejected for the reason
above. Delete `:feature:premium` and its nav registration entirely — rejected: out of scope for a
session about essential settings, and the module and route are legitimate, intentional scaffolding
for Milestone 9, not dead code to clean up now.

**Tradeoffs.** None — `:feature:premium` remains exactly as reachable (by direct route navigation,
for whenever Milestone 9 wires a real entry point) as it was before this session; only a link that
promised more than the app currently delivers was removed.

---

## D-072 — App version is read from the installed package, not `BuildConfig`; the stale `versionCode`/`versionName` were corrected

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 6 (Session 14)

`AppVersionProvider`/`AndroidAppVersionProvider` (`:feature:settings`) reads
`PackageManager.getPackageInfo(context.packageName, 0)` at runtime for the About screen's version
label, rather than referencing `:app`'s generated `BuildConfig.VERSION_NAME`/`VERSION_CODE`
directly. Separately, `AndroidApplicationConventionPlugin`'s `versionCode`/`versionName` — `1` and
`"0.1.0"`, unchanged since Milestone 1 (D-001-era project setup) despite thirteen real
`CHANGELOG.md` releases since — were corrected to `14` and `"0.4.9"` to match the version this
session's own `CHANGELOG.md` entry adds.

**Reason.** Reading the installed package's own version keeps `:feature:settings` decoupled from
`:app`'s build configuration, the same reasoning `AndroidLogger` (`:core:common`) already applies
to avoid a direct `BuildConfig.DEBUG` reference — a feature module should not need to know which
application module it happens to be packaged into just to answer "what version am I." The
`versionCode`/`versionName` correction exists because the brief explicitly asked for an accurate
"App version" display: shipping a real About screen that reads `PackageManager` correctly, but
against a `versionName` frozen at `"0.1.0"` for thirteen sessions, would have displayed a materially
wrong, worse-than-no-information number — a bug the About screen's own correctness requirement
surfaced, not a pre-existing one this session went looking for.

**Alternatives.** Pass `BuildConfig.VERSION_NAME`/`VERSION_CODE` down from `:app` through
`CountFlowNavHost` as constructor parameters, the same way `pendingEventId` is threaded (Session
13) — rejected: that pattern exists for a genuine cross-feature navigation event, not a static value
every module can already read for itself via `PackageManager` with no plumbing at all. Leave
`versionCode`/`versionName` unchanged and note the mismatch as a known issue — rejected: the fix is
a two-line change with no risk, not worth carrying as debt through Final MVP QA.

**Tradeoffs.** `versionCode`/`versionName` must now be bumped manually alongside `CHANGELOG.md` each
session that adds one, exactly as `CHANGELOG.md` itself already is — no new process, just a second
place the same discipline applies. `PackageManager.getPackageInfo` can theoretically throw for a
package that isn't installed, which cannot happen for an app reading its own `packageName` from
`Context`; `AndroidAppVersionProvider` still degrades to a placeholder string rather than crash, on
the same "don't trust a platform API to never surprise you" instinct as the rest of this project's
Android-facing code.

---

## D-073 — Privacy Policy and Open-source licenses ship as visible, disabled placeholders, not fake links or a new dependency

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 6 (Session 14)

The About screen's "Privacy Policy" row renders disabled (dimmed, non-clickable) with supporting
text "Not yet available" when `AboutUiState.privacyPolicyUrl` is `null` — which it always is this
session, since no final URL exists yet. "Open-source licenses" renders the same way, permanently for
now, with "Coming soon." Both rows are real UI, present in the shipped layout, not commented out or
hidden behind a feature flag.

**Reason.** The brief was explicit and doubly so: "Do NOT invent a production privacy-policy URL...
Do not silently ship a fake URL," and separately, "Do not add a large third-party library merely to
display licenses." A real URL would need Google's `play-services-oss-licenses` Gradle plugin (the
only lightweight-ish first-party mechanism for an enumerable, auto-generated licenses screen), which
is itself a new external dependency this project has never taken on — exactly what the brief asked
to avoid "merely" for this. Showing the rows now, disabled, means the final feature (a real
`privacyPolicyUrl`, and a real licenses mechanism decided on its own merits) is a data change and,
for licenses, a scoped follow-up session — not a new screen or layout — when both are ready.

**Alternatives.** Omit both rows entirely until ready — rejected: the brief's own instruction was to
"create the UI/navigation infrastructure cleanly," which reads as building the row now, not
deferring its existence. Link Privacy Policy to a placeholder page hosted somewhere — explicitly
the "fake URL" the brief forbade outright.

**Tradeoffs.** The About screen currently ships two rows that do nothing when tapped — an
intentional, temporary state, tracked as two explicit release-preparation items in `TODO.md`'s P0
section rather than left implicit.

---

## D-074 — Customize Widget has exactly one real-data preview; Style/Progress selectors are abstract design samples, not miniature previews

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 5A (Session 16)

The Customize Widget screen now distinguishes two different questions it was previously conflating.
`WidgetPreviewCard` — one instance, unchanged by this decision — answers "what will *my* widget
look like," built from the user's real selected event and every current setting. The Style row's
seven cards and the Progress row's three cards answer a narrower question, "what does this *style*
look like," using `WidgetStyleThumbnail`/`ProgressStyleThumbnail`: a generic "Aa" glyph and abstract
shapes standing in for title/headline/progress, never the real event's actual text. Tapping a
thumbnail selects it and drives the existing `onWidgetStyleChange`/`onProgressStyleChange` →
`refreshPreview` pipeline (unchanged since Milestone 5A/Session 9), so the one real preview updates
immediately; the thumbnail itself does not.

**Reason.** Before this change, the Style/Progress rows were plain `FilterChip` labels with no
visual content at all — a user could not tell Minimal from Modern without selecting each one and
watching the single preview change, which is slow and easy to second-guess. The tempting fix, making
each chip itself a tiny live preview of the real event, was explicitly rejected by the product brief:
seven or ten simultaneous miniature renders of the user's actual event data is visually noisy, and
more importantly implies each thumbnail is its own independent preview rather than a control that
feeds the one real one — the same "which number is real" confusion a form with several duplicate
live-bound fields creates. Keeping the thumbnails content-free and structurally incapable of
receiving event data (their function signatures take only `style`/`progressStyle`, `selected`, and
`onClick` — no event or render-model parameter exists) makes "exactly one real preview" a property
of the type signature, not a convention that a future edit could quietly violate.

**Alternatives.** Miniature live previews per style (rejected above, the brief's own explicit
concern). A single static legend/diagram explaining the styles in prose instead of per-style
thumbnails — rejected: slower to scan than a visual sample, and does not give each option its own
selectable target the way the existing `AccentColorPicker` swatches already do for color. Import
`WidgetThemeResolver`'s real resolved colors/corners directly into the thumbnails instead of
restating close approximations — rejected: that resolver is `internal` to `:widget:engine` for real
`WidgetRenderModel` data, and reaching across that boundary for a fixed illustrative color a
content-free sample doesn't need would trade a one-line constant for a new cross-module dependency.

**Tradeoffs.** Each thumbnail's colors/corner-radius are hand-restated close approximations of
`WidgetThemeResolver`'s real values (`docs/WIDGET_DESIGN_GUIDE.md` is the shared source both are
checked against), not a single shared constant — if a style's real theme value changes, the matching
thumbnail approximation must be updated by hand and will not fail loudly if someone forgets. Ten
thumbnails is more markup than seven/three `FilterChip`s was, all now living in one new file
(`WidgetStyleThumbnail.kt`) rather than inline in `WidgetConfigurationActivity.kt`.

---

## D-075 — Rewarded-style entitlements are a separate, widget-scoped axis from `isPremium`, granted only through one repository method

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 5A follow-up (Session 17)

`WidgetStyle` gains a second boolean, `isRewarded` (Glass, Rounded, Modern — deliberately not the
same three that already happened to carry `isPremium = true`; Rounded is rewarded but not premium),
backed by a new Room table `widget_style_entitlements` (migration 3→4, composite primary key
`(app_widget_id, style)`, `FOREIGN KEY(app_widget_id) REFERENCES widget_bindings ... ON DELETE
CASCADE`) and a new `:core:domain` interface, `WidgetStyleEntitlementRepository`:
`isStyleUnlocked(appWidgetId, style): Boolean` (free styles always `true`, no persistence lookup)
and `grantRewardedStyle(appWidgetId, style)` (throws `IllegalArgumentException` for a non-rewarded
style). A row's mere existence *is* the entitlement — there is no expiry, no quantity, nothing else
to model. `WidgetConfigurationUiState` gained `unlockedRewardedStyles`/`isStyleLocked(style)`, and
the Style row's thumbnails (`WidgetStyleThumbnail`, D-074) gained a `locked` parameter and lock
badge; tapping a locked style raises `pendingRewardRequest` (a plain nullable state field, the same
one-time-signal shape every other transient UI event in this codebase already uses, cleared by its
own explicit `onRewardRequestHandled` call rather than an event channel) instead of selecting. No
ad provider exists yet at this point in the session — this milestone is deliberately the
entitlement/gating foundation only; D-076 wires a real `RewardedStyleAdController` to it.

**Reason.** Widget-scoped, not event-scoped, for the same reason `WidgetBinding` already draws that
boundary for Style/Progress/Accent (D-013): unlocking Glass for one widget must never unlock it for
another widget showing the same event. A separate axis from `isPremium` rather than reusing it,
because the two model genuinely different products — a subscription (D-009's still-unimplemented
scaffold) and a per-widget rewarded-ad unlock — that happen to both gate styles; conflating them
would mean a future billing feature and this feature fighting over the same bit for two different
unlock mechanisms with different scopes (account-wide vs. per-widget) and different grant paths
(purchase receipt vs. ad-reward callback).

**Alternatives.** A single `unlockTier`-style enum spanning both free/rewarded/premium — rejected:
collapses two independent yes/no questions into one axis for no real savings, and would need a
third "how was this unlocked" field anyway once billing exists. Storing the entitlement on
`WidgetBinding` itself (a `Set<WidgetStyle>` column) instead of a new table — rejected: an
entitlement's lifecycle (granted once, never changes with a style *selection*) is unrelated to a
binding's own fields, which do change on every style change; a separate table with its own cascade
keeps "what does this widget currently render as" and "what has this widget ever unlocked" from
being able to drift into the same row by accident.

**Tradeoffs.** The migration's grandfathering backfill (any widget already resolving to Glass,
Rounded, or Modern before this table existed gets a retroactive entitlement row, via a SQL
`COALESCE` reimplementation of `WidgetBinding.resolveWidgetStyle`'s override-else-default logic)
is hand-duplicated SQL, not shared code with the Kotlin function it mirrors — if that resolution
logic ever changes, the migration's copy will not update itself and will not fail loudly if
someone forgets it exists. Zero UI-visible effect without D-076: as of this decision alone, every
rewarded style is permanently locked for every widget with no entitlement, since nothing can grant
one yet.

---

## D-076 — AdMob Rewarded Ads unlock rewarded styles; the entitlement is granted only from Google's genuine earned-reward callback, never ad-open or ad-dismiss

**Date:** 2026-08-10 · **Status:** Accepted · **Milestone:** 5A follow-up (Session 17) · **Test ads
only — see Tradeoffs.**

Wires a real ad provider to D-075's entitlement foundation. `RewardedStyleAdController` — `load
(Activity)` / `show(Activity, onRewardEarned, onDismissed, onFailed)` — is declared in
`:widget:glance` (which `WidgetConfigurationViewModel`/`Activity` already live in) with **no Google
Mobile Ads import anywhere in its file**; the real implementation
(`AdMobRewardedStyleAdController`, `play-services-ads:25.4.0` + `user-messaging-platform:4.0.0`,
both verified as current and non-deprecated against Google's own maven index and docs, not
recalled from training data) lives in `:app` and is bound to the interface through a new Hilt
`@Binds` module (`AdsModule`) — the first Hilt module ever declared directly in `:app`, made
necessary because `:app`→`:widget:glance` is the only valid dependency direction in this project's
module graph and the brief required AdMob to stay out of `:core:domain`/`:core:database`/
`:widget:engine`. `WidgetConfigurationViewModel.onWatchAdClicked` wires `onRewardEarned` — and
*only* `onRewardEarned` — to `grantRewardedStyle`; `onDismissed`/`onFailed` never grant anything.
Inside `AdMobRewardedStyleAdController.show`, a local `var earned = false` (fresh per call, not a
class field) records whether Google's own `OnUserEarnedRewardListener` fired before
`FullScreenContentCallback.onAdDismissedFullScreenContent` does, since Google's own docs confirm
the latter fires unconditionally on every close — reward or not — and this flag is what stops a
genuine reward from also being reported as a spurious "dismissed without reward" a moment later.
UMP consent (`AdConsentGate`, wrapping `ConsentInformation`/`UserMessagingPlatform`, no custom GDPR
logic) and `MobileAds.initialize` are triggered lazily, only from
`WidgetConfigurationViewModel.onUnlockDialogShown` — the instant the unlock dialog itself first
appears — not from `CountFlowApplication`/`MainActivity`'s own launch.

**Reason.** The reward-security rule ("opening the ad is not enough, displayed is not enough,
dismissed is not enough — only earned reward grants") is the one hard constraint of this whole
feature; a local per-call flag is the simplest construct that cannot leak state between two
different `show()` calls the way a class-level field could. The consent/init scoping is a
considered reading of the brief's "at app launch," not the most literal one: `CountFlowApplication`
itself documents that `onCreate` work is charged directly to a 700 ms cold-start budget (already
measured at 2.5–2.8 s, Session 15), and `WidgetConfigurationActivity` is a genuinely separate
launcher entry point most visits to which never touch a locked style at all — eagerly initializing
ad infrastructure (which can itself show a one-time UMP consent form) for a screen visit that will
never need it would be the exact "automatic ad merely because of a tap" eagerness the brief's own
"intentional value exchange" framing rejects, one level earlier than the tap itself.

**Alternatives.** The newer "GMA Next-Gen SDK" (`ads-mobile-sdk`) — rejected: still v1.x, marks its
own ad-preloading feature "(beta)," and two separate documentation fetches never confirmed an
overall GA/stable status, against `play-services-ads`'s confirmed-current "maintenance mode" (not
deprecated) status. Refreshing consent/initializing the SDK in `CountFlowApplication.onCreate`
(the most literal "at app launch" reading) — rejected per Reason above; flagged here explicitly as
a deviation worth revisiting if Google's guidance is read differently in the future. A `SharedFlow`
one-shot event for the reward result instead of plain `UiState` fields (`pendingRewardRequest`,
`adFeedback`) — rejected: no channel/event pattern exists anywhere else in this codebase (every
one-time UI signal is already a nullable/boolean state field observed via `LaunchedEffect`), and
introducing the only one here for this feature alone would be a new pattern with no precedent.

**Tradeoffs.** `REWARDED_STYLE_TEST_AD_UNIT_ID` (`ca-app-pub-3940256099942544/5224354917`) and the
manifest's AdMob `APPLICATION_ID` (`ca-app-pub-3940256099942544~3347511713`) are both Google's own
published **test** values, marked with a prominent "MUST NOT SHIP AS PRODUCTION CONFIGURATION"
comment at each site — a real production ad unit and App ID are owner-provided and explicitly out
of this decision's scope (see `TODO.md`). CountFlow now contains a network-communicating
third-party SDK (Google Mobile Ads + UMP) for the first time — Session 15's
`docs/PRIVACY_DATA_INVENTORY.md` "zero network / zero advertising SDK" finding is no longer
current and must be regenerated before any Play Store submission; not regenerated as part of this
decision, by the same brief that scoped it out. `refreshEntitlements()` had to become a genuine
`suspend fun` (previously a fire-and-forget `viewModelScope.launch` wrapper) so
`onRewardEarned`'s grant → refresh → clear → select sequence can await the refresh before
selecting — without this, `onWidgetStyleChange` could read a stale `unlockedRewardedStyles` and
wrongly re-raise a reward request immediately after a real grant.

---

## D-077 — DEBUG/RELEASE AdMob identifiers are resolved per build variant from one Gradle object, never a literal in Kotlin source; rewarded-ad readiness is a real, observable state, not inferred from a callback

**Date:** 2026-08-11 · **Status:** Accepted · **Milestone:** 5A follow-up (Session 18)

Two related fixes to D-076's own delivered feature, both found from real diagnostic evidence
(`CountFlowAds` Logcat output, added the session before this one), not speculation.

**Production/test identifier separation.** CountFlow's real, owner-provided production AdMob App
ID (`ca-app-pub-3546123128954911~2283615612`) and Rewarded Ad Unit ID
(`ca-app-pub-3546123128954911/7392472066`) now exist in the repo for the first time, alongside
Google's test values — both declared exactly once, as named constants on a private `AdMobConfig`
object at the top of `app/build.gradle.kts`, never as a literal anywhere in Kotlin source. AGP's
own per-`buildType` `manifestPlaceholders`/`buildConfigField` mechanism resolves the right pair
automatically: DEBUG gets Google's test values, RELEASE gets CountFlow's production values, from
the same single `AndroidManifest.xml`'s one `${admobApplicationId}` placeholder and the same
`AdMobRewardedStyleAdController.requestAdLoad`'s one `BuildConfig.REWARDED_STYLE_AD_UNIT_ID` read —
there is no second code path, no flavor, no manual override to forget. `AdMobConfigTest`
(`:app`) asserts the DEBUG variant's real, AGP-resolved `BuildConfig` value is Google's test ID and
never CountFlow's production one; a companion assertion reads `app/build.gradle.kts` itself (the
one place the RELEASE variant's own values live) to confirm they are CountFlow's production
identifiers, since AGP compiles unit tests against exactly one variant (`debug`, unchanged) and a
RELEASE-variant `BuildConfig` cannot be constructed from that same test process. The stronger
confirmation — that `assembleRelease` genuinely produces a RELEASE APK whose merged manifest and
generated `BuildConfig` carry the production values, and that `assembleDebug` never does — was
run manually this session (both artifacts' merged manifests and generated `BuildConfig.java`
grepped directly) rather than automated, and is recorded in this session's own report.

**Rewarded-ad readiness state.** `CountFlowAds` diagnostics (added the prior session) showed a
real UX bug on a physical Samsung Galaxy A55: the very first "Watch ad & unlock" tap could land
while UMP/`MobileAds`/`RewardedAd.load()` was still genuinely in flight, and the UI reported "Ad
unavailable right now" — a legitimate load in progress being misreported as a failure.
`RewardedStyleAdController` gained `val state: StateFlow<RewardedAdState>`
(`LOADING`/`READY`/`SHOWING`/`FAILED`), mirrored into
`WidgetConfigurationUiState.rewardedAdState` by a collector started once in the ViewModel's
`init`. The Unlock Style dialog's primary button now reads this state directly: disabled with
"Preparing ad…" for `LOADING`/`SHOWING`, "Watch ad & unlock" for `READY`, "Retry" for `FAILED`.
`WidgetConfigurationViewModel.onWatchAdClicked` refuses to call
`RewardedStyleAdController.show` unless `rewardedAdState == READY` — the real enforcement, since a
disabled Compose button alone cannot be trusted against a tap landing in the same frame as a state
change. A genuine `FAILED` (consent/load/show failure) no longer auto-retries in the background —
only a dismiss-without-reward still does, preserving the existing preload lifecycle unchanged for
that one case — specifically so a "Retry" action has something real to retry rather than racing an
automatic reload that already happened.

**Reason.** Both fixes follow directly from evidence, not guesswork: the diagnostic logging this
session started from showed exactly which call landed against `rewardedAd == null`, and the build
scan showed exactly zero prior guard against a debug/release identifier mix-up (nothing stopped a
future edit from swapping which constant went where). Neither reward-security rule from D-076
changed — `onRewardEarned` is still wired to nothing but Google's genuine earned-reward callback.

**Alternatives.** Per-flavor source sets (`debug`/`release` directories with their own
`AndroidManifest.xml`) instead of manifest placeholders — rejected: two manifest files to keep in
sync is a worse single-source-of-truth story than one manifest plus one placeholder, and this
project has never used flavor-specific manifests for anything else. Exposing `RewardedAdState` via
a callback parameter added to `load()` (`load(activity, onReady, onFailed)`) instead of a
`StateFlow` — rejected: `load()` is also called autonomously from inside `show()`'s own dismiss
handler to prepare the next ad, with no caller-supplied callback available to reuse at that point;
a persistent, controller-owned `StateFlow` needs no such threading and matches this codebase's own
established `StateFlow`-for-observable-state idiom already used at every ViewModel. Auto-retrying
a `FAILED` load in the background — rejected per Reason above, since it would make the requested
"Retry" action a no-op the user could never actually see land.

**Tradeoffs.** `AdMobConfigTest`'s RELEASE-variant assertion reads Gradle Kotlin DSL source text
rather than a real compiled `BuildConfig` — weaker evidence than the DEBUG-variant assertions
(which read the genuine, AGP-resolved value), documented as such in the test's own KDoc rather than
overstated. This is a real, accepted gap: a future change to the release `buildTypes` block that
happens to keep the text pattern this test greps for, while actually breaking the wiring some other
way, would not be caught by this test — only by the manual `assembleRelease` verification this
session ran once, not by continuous CI. `RewardedAdState` is intentionally coarse (four states, no
sub-states for "which specific SDK step is in flight") — matches what the UI actually needs to
decide, not a full mirror of every AdMob/UMP callback this controller receives internally.
