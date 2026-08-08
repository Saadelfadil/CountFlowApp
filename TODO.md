# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 4

- [ ] **Approve starting Milestone 3.** Standing instruction is not to begin without explicit
      approval.
- [ ] **Confirm the countdown label policy.** The engine's thresholds are now real behaviour and
      cheap to change today, expensive once screens and widgets depend on them:
      "starting soon" under 1 hour; a plain day count for 2–6 days; "next week" for anything in
      the following calendar week; a day count beyond that; "yesterday" then a day count back to
      7 days, then "expired". All live in `CountdownConfig`.

---

## P1 — Milestone 3: event CRUD

### Open with the testing gap (TD-003)
- [ ] Add Robolectric to the test convention plugin.
- [ ] DAO tests against an in-memory database: the four `CASE WHEN` sort arms, the empty-set
      behaviour of the category filter, the foreign-key cascade actually deleting bindings and
      reminders, and the `getActiveReminders` join requiring both switches.
- [ ] Repository tests with Turbine over the returned `Flow`s.

### Screens
- [ ] Home: upcoming list, realtime search, sort, category filter, empty state.
- [ ] Create/edit form: title, emoji picker, category, date picker, time picker, all-day toggle,
      accent colour, reminders.
- [ ] ViewModels exposing immutable state through `StateFlow`.
- [ ] Archive, complete, and delete flows, with undo for delete.

### Presentation of domain tokens
- [ ] Map every `CountdownLabel` token to a string resource. Use `plurals`, not concatenation —
      `InDays(1)` and `InDays(2)` are different strings in most languages.
- [ ] Map `AccentColor.Dynamic` to the Material You scheme and `Fixed` to its colour.

---

## P2 — Milestone 4: widget engine

- [ ] Widget configuration activity: `EXTRA_APPWIDGET_ID` handling, `RESULT_OK` echoing the id,
      a cancel path that leaves no orphan binding.
- [ ] `widgetFeatures="reconfigurable|configuration_optional"` and
      `widgetCategory="home_screen|keyguard"`.
- [ ] `EntryPointAccessors` for dependencies inside `provideGlance` — Hilt cannot inject
      `GlanceAppWidget` (LIM-005).
- [ ] Call `pruneOrphanedBindings` at startup against the launcher's live id list.
- [ ] Clean up bindings in `GlanceAppWidgetReceiver.onDeleted`.
- [ ] Exclude `widget_bindings` from `data_extraction_rules.xml` — `appWidgetId` values are
      device-local and restoring them elsewhere points widgets at the wrong events.
- [ ] Verify emoji rendering on real hardware, not just the emulator (LIM-006).

---

## P3 — Milestone 5: multiple widgets

- [ ] Canvas-drawn circular progress ring, quantized to whole percent and cached, budgeted
      against `6 × screenW × screenH` bytes (LIM-001, LIM-003). `percentCompleteWhole` on
      `CountdownResult` is already the cache key.
- [ ] Seven widget styles; sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges.
- [ ] Honour `WidgetBinding.resolveWidgetStyle` / `resolveProgressStyle` so two widgets on one
      event can look different.
- [ ] Adopt Google's font-measurement utility, but move it off the render path, cache it, and
      replace the deprecated `scaledDensity` (LIM-004).

---

## P4 — Milestones 6 to 9

- [ ] Settings, backup and restore, About.
- [ ] Notifications. `Reminder.scheduledTime` already computes fire times by calendar rather
      than by millisecond offset, so DST will not drift them.
- [ ] The full D-008 refresh strategy: launcher-ticked `Chronometer` for the final 24 hours
      (`CountdownStatus.needsLiveTicking` already marks these), one coalesced alarm, and
      event-driven invalidation.
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-003 (Medium)** — DAO and repository integration tests. Scheduled for Milestone 3.
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only. Needs an ICU collation or
      a normalised lowercase shadow column.

---

## Continuous

- [ ] Update all seven documents at the end of every session.
- [ ] Keep lint at 0 errors and `:core:domain` coverage above the 95% gate.
- [ ] Never enable `fallbackToDestructiveMigration` (D-024). Every version bump gets a real
      migration and a test that walks real data forward.
