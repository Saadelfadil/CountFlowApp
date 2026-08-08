# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 5

- [ ] **Approve starting Milestone 4** (widget engine).
- [ ] **Confirm the countdown label policy.** Still unanswered from Session 3, and now visible on
      a real screen: an event seven days out reads "7 / Next week". Current thresholds —
      "starting soon" under 1 hour; a plain day count for 2–6 days; "next week" for anything in
      the following calendar week; "yesterday" then a day count back to 7 days, then "expired".
      All live in `CountdownConfig`, so each is a one-line change today and a migration-shaped
      change once widgets render them.

---

## P1 — Milestone 4: widget engine

- [ ] Widget configuration activity: `EXTRA_APPWIDGET_ID` handling, `RESULT_OK` echoing the id,
      a cancel path that leaves no orphan binding.
- [ ] `widgetFeatures="reconfigurable|configuration_optional"` and
      `widgetCategory="home_screen|keyguard"`.
- [ ] `EntryPointAccessors` for dependencies inside `provideGlance` — Hilt cannot inject
      `GlanceAppWidget` (LIM-005).
- [ ] Reuse `CountdownLabelFormatter.format(resources, label)` in the widget layer. It exists
      precisely so widgets and the app agree on wording; do not write a second mapping.
- [ ] Call `pruneOrphanedBindings` at startup against the launcher's live id list.
- [ ] Clean up bindings in `GlanceAppWidgetReceiver.onDeleted`.
- [ ] Exclude `widget_bindings` from `data_extraction_rules.xml` — `appWidgetId` values are
      device-local and restoring them elsewhere points widgets at the wrong events.
- [ ] Verify emoji rendering on real hardware, not just the emulator (LIM-006).

---

## P2 — Milestone 5: multiple widgets and the deferred UI

Widget work:
- [ ] Canvas-drawn circular progress ring, quantized to whole percent and cached, budgeted
      against `6 × screenW × screenH` bytes (LIM-001, LIM-003). `EventCardUiModel.progressPercent`
      is already the cache key shape.
- [ ] Seven widget styles; sizes 2×1, 2×2, 4×2 with `SizeMode.Exact` and breakpoint ranges.
- [ ] Honour `WidgetBinding.resolveWidgetStyle` so two widgets on one event can differ.
- [ ] Adopt Google's font-measurement utility, but move it off the render path, cache it, and
      replace the deprecated `scaledDensity` (LIM-004).

Deferred from Milestone 3, because both should preview a renderer that will finally exist:
- [ ] Accent-colour picker in the create/edit form. `AccentColor.Fixed` is already modelled,
      persisted, mapped, and tested — only the picker is missing.
- [ ] Live widget preview in the form.
- [ ] Archive, complete, and delete gestures on the list (TD-008). The ViewModel methods exist
      and are tested; only the gesture is missing.

---

## P3 — Milestone 6: settings

- [ ] Theme mode, dynamic-colour toggle, notification preferences, backup and restore, About.
- [ ] **Do the full localisation pass here (TD-007).** Sort names, validation messages,
      empty-state copy, and every field label are currently hard-coded in Kotlin. Doing it
      alongside Settings means one pass over the whole app rather than two.
- [ ] Wire `PreferencesRepository` to the theme — `ThemeMode` and `useDynamicColor` are stored
      and tested but nothing reads them yet; `CountFlowTheme` still takes its defaults.

---

## P4 — Milestones 7 to 9

- [ ] Notifications. `Reminder.scheduledTime` already computes fire times by calendar rather than
      millisecond offset, so DST will not drift them.
- [ ] The full D-008 refresh strategy: launcher-ticked `Chronometer` for the final 24 hours
      (`CountdownStatus.needsLiveTicking` already marks these), one coalesced alarm, and
      event-driven invalidation.
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings. Scheduled for Milestone 6.
- [ ] **TD-002 (Low)** — five empty scaffold modules remain.
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-008 (Low)** — no archive/complete/delete gesture. Scheduled for Milestone 5.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested; needs
      an instrumented test once Compose UI testing is set up.

## Testing gaps

- [ ] No Compose UI tests. The screens are verified by a 14-step device script, which is real
      coverage but lives outside the repository. Worth converting to instrumented tests.
- [ ] No tests for `EditEventViewModel`. Its validation path is covered indirectly through
      `EventValidatorTest` and the device script, not directly.

---

## Continuous

- [ ] Update all seven documents at the end of every session.
- [ ] Keep lint at 0 errors and `:core:domain` coverage above the 95% gate.
- [ ] Never enable `fallbackToDestructiveMigration` (D-024).
- [ ] Never call `Instant.now()` or `LocalDate.now()` outside the DI module — inject `Clock`.
