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
