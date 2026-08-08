# CountFlow — Roadmap

Living document. Update the status column as milestones move.

**Status values:** `Not Started` · `In Progress` · `Completed` · `Blocked`

| # | Milestone | Status | Session |
|---|---|---|---|
| 0 | Research & architecture | **Completed** | 1 |
| 1 | Project foundation | **Completed** | 2 |
| 2 | Database, repositories, countdown engine | Not Started | — |
| 3 | Event CRUD | Not Started | — |
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

## Milestone 2 — Database, repositories, countdown engine · Not Started

**Reordered:** the countdown engine was pulled forward from Milestone 4. It is pure Kotlin, so
it is testable from day one, and everything downstream depends on it.

1. `:core:domain` — `Event`, `Countdown`, `EventCategory`, `WidgetStyle`, `ProgressStyle`,
   `AccentColor`, repository interfaces, and a `Clock` abstraction.
2. `CountdownEngine` with a table-driven test suite: DST transitions, leap years, all-day versus
   timed events, cross-timezone targets, past events, and the friendly labels
   (Today / Tomorrow / Next Week / Yesterday / Completed).
3. `:core:database` — `EventEntity`, `WidgetBindingEntity`, DAOs, converters, schema export,
   and a migration test harness from version 1.
4. `:core:data` — repository implementations, DataStore for preferences, entity/domain mappers.

**Carries decisions:** D-002 (Room as the only source of truth), D-013 (style on the binding),
D-014 (epoch millis + zone + all-day), D-015 (calendar comparison for Today/Tomorrow).

**Done when:** the engine's test suite passes, a Room migration test runs, and repositories
expose `Flow`s the UI can collect.

---

## Milestone 3 — Event CRUD · Not Started

Home screen with the upcoming-events list, realtime search, sort (date, title, created,
category), category filtering, and the add action. Create/edit form with title, emoji picker,
category, date and time pickers, accent colour, and a live widget preview.
ViewModels expose immutable state via `StateFlow`.

**Done when:** an event can be created, edited, archived, and deleted, and survives process death.

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
