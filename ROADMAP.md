# CountFlow — Roadmap

Living document. Update the status column as milestones move.

**Status values:** `Not Started` · `In Progress` · `Completed` · `Blocked`

| # | Milestone | Status | Session |
|---|---|---|---|
| 0 | Research & architecture | **Completed** | 1 |
| 1 | Project foundation | **Completed** | 2 |
| 2 | Database, repositories, countdown engine | **Completed** | 3 |
| 3 | Event CRUD | **Completed** | 4 |
| 4 | Widget engine | Not Started | — |
| 5 | Multiple widgets | Not Started | — |
| 6 | Settings | Not Started | — |
| 7 | Notifications | Not Started | — |
| 8 | Optimization | Not Started | — |
| 9 | Play Store ready | Not Started | — |

---

## Milestone 0 — Research & architecture · Completed (Session 1)

Studied Google's App Widget sample in full and proposed the production architecture.
Three platform constraints found that changed the design: Glance has no determinate circular
progress, `PeriodicWorkRequest` cannot refresh every minute, and the sample's in-memory state
does not survive process death. Deliverable: `ARCHITECTURE.md`.

---

## Milestone 1 — Project foundation · Completed (Session 2)

Git, Gradle 9.6.1 wrapper, version catalog, six convention plugins in a `build-logic` composite,
14 modules with a downward-only dependency graph, Hilt with WorkManager configuration, the
Material 3 theme with dynamic color, and Navigation Compose with five reachable destinations.

Verified: `assembleDebug` succeeds, lint reports 0 errors, the app installs and launches on an
API 36 emulator with no crashes, all five destinations navigate correctly, and both light and
dark themes render.

---

## Milestone 2 — Database, repositories, countdown engine · Completed (Session 3)

**Reordered:** the countdown engine was pulled forward from Milestone 4. It is pure Kotlin, so
it was testable from day one, and everything downstream depends on it.

Delivered: the full domain model (`Event`, `EventTarget`, `WidgetBinding`, `Reminder`, and the
supporting enums and value classes); `CountdownEngine` at 100% line coverage; four repository
contracts; Room with three entities, cascading foreign keys, converters, and committed schema
export; repository implementations with round-trip-tested mappers; and DataStore preferences.

86 tests, 0 failures. `:core:domain` at 99.4% line coverage, enforced by a Kover gate.

Two defects were found by the tests and fixed: all-day events read as "starting soon" for their
whole day, and "remaining" counted upward once an event was in progress.

**Not delivered:** DAO and repository integration tests, which need Robolectric. Tracked as
TD-003 and scheduled for the start of Milestone 3.

---

## Milestone 3 — Event CRUD · Completed (Session 4)

Built in the order the owner set: tests first, then validation, then UI models, then ViewModels,
then screens — so neither validation nor presentation logic could end up living in a composable.

Delivered: Robolectric with 32 DAO tests and 20 repository tests closing TD-003; `EventValidator`
in the domain; `CountdownLabel` and category formatting through plural resources;
`EventCardUiModel` with an injectable mapper; `EventsViewModel` and `EditEventViewModel` over
`StateFlow`; and the home list and create/edit form.

179 tests, 0 failures. `:core:domain` at 99.5% line coverage. Verified on an API 36 emulator with
14 end-to-end checks driving create, validate, search, filter, and edit.

**Not delivered:** the live widget preview in the form, and the accent colour picker — both
deferred to Milestone 5, where the widget renderer they should preview will actually exist.
Archive, complete, and delete exist on the ViewModel but have no UI gesture yet.

---

## Milestone 4 — Widget engine · Not Started

The widget configuration activity (the piece Google's sample has no answer for): correct
`EXTRA_APPWIDGET_ID` handling, `RESULT_OK` echoing the id, a cancel path that leaves no orphan
bindings, and `reconfigurable` so a placed widget can be re-pointed at another event.
Snapshot-free render model per D-002. First Glance widget end to end.

**Watch:** LIM-005 (Hilt cannot inject `GlanceAppWidget`), LIM-003 (bitmap budget),
LIM-006 (emoji rendering — verify on real hardware).

**Done when:** a widget can be placed, bound to an event, re-pointed, and deleted cleanly.

---

## Milestone 5 — Multiple widgets · Not Started

Unlimited independent widgets. Seven themes (Minimal, Material, Glass, OLED, Progress, Rounded,
Modern). Sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges. The Canvas-drawn
progress ring required by LIM-001, quantized to whole percent and cached.

**Done when:** two widgets showing the same event in different styles update independently.

---

## Milestone 6 — Settings · Not Started

Theme and dark-mode selection, the dynamic-colour toggle, notification preferences, backup and
restore, About with licences and the privacy policy. Revisit `data_extraction_rules.xml` to
exclude widget bindings.

---

## Milestone 7 — Notifications · Not Started

Opt-in reminders at 30 days, 7 days, tomorrow, and today. Notification channels, the
`POST_NOTIFICATIONS` runtime permission, and scheduling that shares the coalesced-alarm
infrastructure from D-008 rather than adding a second wakeup source.

---

## Milestone 8 — Optimization · Not Started

Implement the full D-008 refresh strategy: the launcher-ticked `Chronometer` for the final 24
hours, one coalesced alarm for the whole app, and event-driven invalidation. Enable R8 and write
the keep rules deferred in D-016. Baseline Profiles and macrobenchmarks against the sub-700 ms
cold start and sub-100 ms widget update budgets. Full accessibility pass: TalkBack, large fonts,
high contrast, dynamic scaling.

---

## Milestone 9 — Play Store ready · Not Started

Wire the real Firebase Analytics and Crashlytics behind the `:core:analytics` interface and
AdMob and Play Billing behind `:core:billing`, then measure the cold-start cost and defer
initialization off the critical path (D-009). Release signing, store listing, screenshots,
privacy policy.

---

## Explicitly deferred

**Android 16 Live Updates.** Not implemented, by instruction. The architecture keeps a single
seam for it: everything deciding what to show lives in `:widget:engine` as a pure function from
data to render model, and each surface — home screen, lockscreen, Live Updates — is a thin
adapter over that model. Adding Live Updates later means one new adapter and no changes to
domain, data, or engine.

**Lockscreen widgets and Always-On Display.** `widgetCategory="home_screen|keyguard"` will be
declared from Milestone 4 so the capability is present, but neither surface is a target before
Milestone 9.
