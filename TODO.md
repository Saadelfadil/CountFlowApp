# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 6

- [ ] **Approve starting Milestone 5** (multiple widgets, themes, sizes).
- [ ] **Verify real widget placement on a GUI emulator or physical device** (TD-010). Every
      piece of Milestone 4 was verified except this — dragging the widget onto an actual home
      screen and confirming it renders. Do this *before* building more widget styles on top of
      an unconfirmed rendering path.
- [ ] **Confirm the countdown label policy** — carried over from Sessions 3 and 4, still
      unanswered. One line in `CountdownConfig` today; two surfaces and their tests once
      Milestone 5 adds more widget styles that render it.

---

## P1 — Milestone 5: multiple widgets, themes, sizes

- [ ] **Start with TD-010** (see P0) before anything else in this milestone.
- [ ] Canvas-drawn circular progress ring for `ProgressStyle.CIRCULAR`, quantized to whole
      percent and cached, budgeted against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
      `WidgetProgress.percent` is already the cache-key shape.
- [ ] Render all seven `WidgetStyle` values distinctly — `WidgetThemeResolver` already resolves
      correct colours and corner radii for each; the renderer currently applies them but does
      not yet differentiate *layout* (e.g. `PROGRESS`'s emphasis, `MODERN`'s editorial density).
- [ ] Sizes 2×1 and 4×2 alongside the existing 2×2, with `SizeMode.Exact` and breakpoint ranges
      — moving off the single-size default this milestone deliberately used.
- [ ] Verify two widgets on the same event can show different styles via
      `WidgetBinding.resolveWidgetStyle` — the mapper and domain model already support this
      (tested), but no UI lets a user set an override yet.
- [ ] Adopt Google's font-measurement utility from the original sample, but move it off the
      render path, cache it, and replace the deprecated `scaledDensity` (LIM-004).
- [ ] Verify emoji rendering on real hardware, not just the emulator (LIM-006).

Deferred from Milestone 3, since both should preview a renderer that only now exists:
- [ ] Accent-colour picker in the create/edit form. `AccentColor.Fixed` is already modelled,
      persisted, mapped, and now flows correctly into `WidgetTheme` — only the picker UI is
      missing.
- [ ] Live widget preview in the form.
- [ ] Archive, complete, and delete gestures on the list (TD-008). The ViewModel methods exist
      and are tested; only the gesture is missing.

---

## P2 — Milestone 6: settings

- [ ] Theme mode, dynamic-colour toggle, notification preferences, backup and restore, About.
- [ ] **Do the full localisation pass here (TD-007).** Sort names, validation messages,
      empty-state copy, and every field label are currently hard-coded in Kotlin.
- [ ] Wire `PreferencesRepository` to the theme — `ThemeMode` and `useDynamicColor` are stored
      and tested but nothing reads them yet.
- [ ] Revisit `data_extraction_rules.xml` — see D-037. Not a blocker; a second Room database for
      widget bindings only becomes worth it on its own merits.

---

## P3 — Milestones 7 to 9

- [ ] Notifications. `Reminder.scheduledTime` already computes fire times by calendar rather
      than millisecond offset, so DST will not drift them.
- [ ] The full D-008 refresh strategy: launcher-ticked `Chronometer` for the final 24 hours
      (`CountdownStatus.needsLiveTicking` already marks these), one coalesced alarm, and
      event-driven invalidation — replacing `GlanceWidgetRefreshScheduler`'s Milestone 4
      live-observation approach, which only covers the app-alive case (D-036).
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-010 (Medium, new)** — no widget verified through a real launcher flow. Scheduled
      first in Milestone 5.
- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings. Scheduled for Milestone 6.
- [ ] **TD-002 (Low)** — three empty scaffold modules remain (`:core:notifications`,
      `:core:analytics`, `:core:billing`).
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-008 (Low)** — no archive/complete/delete gesture. Scheduled for Milestone 5.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested.

## Testing gaps

- [ ] No Compose UI tests for the app's own screens (the events feature). The Glance widget
      layer now has this (`CountdownWidgetContentTest`); the app screens are still verified by
      a device script that lives outside the repository.
- [ ] No test for `WidgetConfigurationViewModel` directly — its cancel/confirm/no-orphan
      behaviour was verified on-device this session (which is how BUG-R005 was found), not by a
      unit test. Worth adding one now that the real behaviour is understood precisely.

---

## Continuous

- [ ] Update all documents — including `AI_CONTEXT.md` — at the end of every session.
- [ ] Keep lint at 0 errors and `:core:domain` coverage above the 95% gate.
- [ ] Never enable `fallbackToDestructiveMigration` (D-024).
- [ ] Never call `Instant.now()` or `LocalDate.now()` outside the DI module — inject `Clock`.
- [ ] Never duplicate a presentation rule between the app and the widget layer — put it in
      `:core:domain` once, as D-034 did for `showsMeaningfulDayCount` and `defaultEmoji`.
