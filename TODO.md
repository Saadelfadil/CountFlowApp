# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 3

- [ ] **Confirm "Kotlin Native" meant native Android, not Kotlin/Native.** The Session 2 brief
      listed "Use Kotlin Native" alongside an entirely Android-specific stack (Glance, Room,
      Hilt, Compose). It was built as a native Android app in Kotlin. If Kotlin Multiplatform
      was actually intended, that is a foundational change and Milestone 1 needs revisiting
      before any more code lands.
- [ ] **Approve starting Milestone 2.** Standing instruction is not to begin without explicit
      approval.

---

## P1 — Milestone 2: database, repositories, countdown engine

### Domain (`:core:domain`, pure Kotlin)
- [ ] `Event` model: id (UUID), title, emoji, optional icon, category, `targetEpochMillis`,
      `targetZoneId`, `isAllDay`, `createdAt`, accent colour, default widget style, default
      progress style, notifications enabled, archived, completed. Per D-014.
- [ ] `WidgetBinding` model: `appWidgetId`, `eventId`, style overrides. Per D-013.
- [ ] `AccentColor` as a sealed type with `Dynamic` and `Fixed(argb)` so Material You is a
      first-class per-event choice.
- [ ] `Clock` abstraction so time is injectable and tests are deterministic.
- [ ] Repository interfaces: `EventRepository`, `WidgetBindingRepository`,
      `PreferencesRepository`.

### Countdown engine (`:core:domain`)
- [ ] `CountdownEngine` producing days, weeks, months, hours, minutes, percent complete,
      elapsed, and remaining.
- [ ] Friendly labels: Today, Tomorrow, Next Week, Yesterday, Completed — computed by comparing
      `LocalDate` in the target zone, never by dividing a duration. Per D-015.
- [ ] Table-driven test suite covering DST transitions (both directions), leap years, all-day
      versus timed events, targets in a different zone from the device, past events, and the
      exact boundaries where each friendly label flips.

### Database (`:core:database`)
- [ ] `EventEntity`, `WidgetBindingEntity` with a foreign key and cascade delete, DAOs, type
      converters.
- [ ] Room schema export, plus a migration test harness starting at version 1.
- [ ] Add a `countflow.android.room` convention plugin once Room is actually applied.

### Data (`:core:data`)
- [ ] Repository implementations returning `Flow`.
- [ ] DataStore for theme, dark mode, dynamic colour, default widget style, premium status.
- [ ] Entity ↔ domain mappers with round-trip tests.

---

## P2 — Milestone 3: event CRUD

- [ ] Home: upcoming list, realtime search, sort by date/title/created/category, category filter.
- [ ] Create/edit form: title, emoji picker, category, date picker, time picker, accent colour,
      live widget preview.
- [ ] ViewModels exposing immutable state through `StateFlow`.
- [ ] Archive, complete, and delete flows.

---

## P3 — Milestone 4: widget engine

- [ ] Widget configuration activity with correct `EXTRA_APPWIDGET_ID` handling, `RESULT_OK`
      echoing the id, and a cancel path that leaves no orphan binding.
- [ ] `widgetFeatures="reconfigurable|configuration_optional"` so a placed widget can be
      re-pointed without deleting it.
- [ ] `widgetCategory="home_screen|keyguard"` from the start.
- [ ] `EntryPointAccessors` for dependencies inside `provideGlance` — Hilt cannot inject
      `GlanceAppWidget` (LIM-005).
- [ ] Clean up bindings in `GlanceAppWidgetReceiver.onDeleted`.
- [ ] Exclude widget bindings from `data_extraction_rules.xml` — `appWidgetId` values are
      device-local.
- [ ] Verify emoji rendering on real hardware, not just the emulator (LIM-006).

---

## P4 — Milestone 5 and beyond

- [ ] Canvas-drawn circular progress ring, quantized to whole percent and cached, budgeted
      against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
- [ ] Seven widget themes; sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges.
- [ ] Adopt Google's font-measurement utility, but move it off the render path, cache it, and
      replace the deprecated `scaledDensity` (LIM-004).
- [ ] Settings, backup/restore, notifications, the full D-008 refresh strategy, R8 keep rules,
      Baseline Profiles, accessibility pass, then Firebase/AdMob/Billing.

---

## Continuous

- [ ] Update `SESSION_SUMMARY.md`, `PROJECT_STATUS.md`, `CHANGELOG.md`, `DECISIONS.md`,
      `KNOWN_ISSUES.md`, `ROADMAP.md`, and this file at the end of every session.
- [ ] Keep lint at 0 errors.
- [ ] Resolve TD-001 (AGP built-in Kotlin) before any AGP 10 upgrade.
