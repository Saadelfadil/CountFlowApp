# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 8

- [ ] **Get a stable device, before anything else.** Sessions 5, 6, and 7 in sequence: no device
      → an unstable device that got partway through verification → no device at all. This is now
      the single biggest thing blocking real confidence in the widget, ahead of any remaining
      code work. Open the session with `adb devices -l`, then `adb shell appwidget grantbind
      --package com.countflow --user 0` and `adb shell dumpsys user`, and confirm the connection
      survives a few minutes of idle time before relying on it for anything else.
- [ ] **Verify real widget placement** (TD-010) once a device is confirmed stable — drag from the
      widget picker onto a real home screen, confirm it renders, edit the bound event and confirm
      the widget updates, remove it and confirm the binding is cleaned up, screenshot each step.
      This is the only piece of Milestone 4/4.5 with no device evidence at all beyond Session 5's
      partial confirm/cancel-path work.
- [ ] **Approve starting Milestone 5** (multiple widgets, themes, sizes). The widget has now been
      through both a finishing pass (Session 6) and a stabilization audit (Session 7) — see
      `docs/WIDGET_REVIEW.md` for the full read on what's solid and what isn't before deciding
      whether to build more on top of it.
- [ ] **Confirm the countdown label policy** — carried over from Sessions 3 and 4, still
      unanswered. One line in `CountdownConfig` today; two surfaces and their tests once
      Milestone 5 adds more widget styles that render it.

---

## P1 — Milestone 5: multiple widgets, themes, sizes

- [ ] **Start with device verification and TD-010** (see P0) before anything else in this
      milestone — every item below builds more widget surface on top of a rendering path that has
      still never been confirmed through a real launcher.
- [ ] **TD-011 — read the system's actual widget corner radius** (`android.R.dimen.system_app_widget_background_radius`
      via Glance's `cornerRadius(Int resId)` overload, confirmed to exist) instead of the
      hand-picked 16/20/28dp constants in `WidgetThemeResolver`, for the styles that should track
      it. Decide per-style whether to track the system value or keep an intentional difference
      (ROUNDED's whole premise is being rounder than default).
- [ ] Canvas-drawn circular progress ring for `ProgressStyle.CIRCULAR`, quantized to whole
      percent and cached, budgeted against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
      `WidgetProgress.percent` is already the cache-key shape.
- [ ] Render all seven `WidgetStyle` values distinctly — `WidgetThemeResolver` already resolves
      correct colours, corner radii, and (as of Session 6/7) correct forced-background/
      high-contrast text colours for each; the renderer applies all of that but does not yet
      differentiate *layout* (e.g. `PROGRESS`'s emphasis, `MODERN`'s editorial density).
- [ ] Sizes 2×1 and 4×2 alongside the existing 2×2, with `SizeMode.Exact` and breakpoint ranges —
      this also closes TD-012 (a launcher ignoring `resizeMode="none"` today has no adaptive
      fallback to fall into).
- [ ] Verify two widgets on the same event can show different styles via
      `WidgetBinding.resolveWidgetStyle` — the mapper and domain model already support this
      (tested), but no UI lets a user set an override yet.
- [ ] Adopt Google's font-measurement utility from the original sample, but move it off the
      render path, cache it, and replace the deprecated `scaledDensity` (LIM-004).
- [ ] Verify emoji rendering on real hardware, not just the emulator (LIM-006).
- [ ] A settings/binding-editor surface for `showTitle` / `showEmoji` / `showTargetDate` /
      `showPercentage` — all four are modelled, persisted, and (as of Session 6) fully wired to
      the renderer, but nothing lets a user set any of them independently of the
      `WidgetBinding.inheriting()` defaults yet.
- [ ] Wire `WidgetRenderModel.target`/`.targetZone`/`.showDate` to an actual rendered date —
      identified in Session 7 as the same *shape* of gap as `showPercentage` was (computed,
      never read), but deliberately not fixed then: it needs new date-formatting logic (no
      `DateTimeFormatter` usage exists anywhere in the codebase yet), unlike `showPercentage`'s
      one-line wire-up. First real formatting work this surface will need.

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
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass. **This is also where
      real widget performance/memory/battery numbers finally get measured** — no session has
      produced one yet (Sessions 6 and 7 both lacked a working device); if a stable device is
      available earlier, pull a first rough measurement forward rather than waiting for Milestone 8.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-010 (Medium)** — no widget verified through a real launcher flow. See P0 above — this
      is the top blocker now, three sessions running.
- [ ] **TD-011 (Medium, new Session 7)** — widget corner radii don't track the system's actual
      clip radius. Scheduled for the start of Milestone 5.
- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings. Scheduled for Milestone 6.
- [ ] **TD-002 (Low)** — three empty scaffold modules remain (`:core:notifications`,
      `:core:analytics`, `:core:billing`).
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-008 (Low)** — no archive/complete/delete gesture. Scheduled for Milestone 5.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested.
- [ ] **TD-012 (Low, new Session 7)** — no adaptive fallback if a launcher ignores
      `resizeMode="none"`. Closes as a side effect of Milestone 5's multi-size work.
- [ ] **TD-013 (Low, new Session 7)** — long widget titles clip with no ellipsis; Glance 1.1.1's
      `Text` has no overflow parameter to set one.

## Testing gaps

- [ ] No Compose UI tests for the app's own screens (the events feature). The Glance widget
      layer now has this (`CountdownWidgetContentTest`); the app screens are still verified by
      a device script that lives outside the repository.
- [ ] No test for `WidgetConfigurationViewModel` directly — its cancel/confirm/no-orphan
      behaviour was verified on-device in Session 5 (which is how BUG-R005 was found), not by a
      unit test. Worth adding one now that the real behaviour is understood precisely.
- [ ] No real-device instrumented test exists for the widget lifecycle end to end (create → widget
      updates, edit → widget updates, delete → widget disappears, remove → binding removed,
      reconfigure → binding updated). Every step is covered individually by unit tests or by
      direct database verification, but nothing exercises the full chain through a real
      `AppWidgetHost`. Depends on TD-010 being closed first.
- [ ] No widget update latency, memory, or battery measurement exists from any session. See
      `docs/WIDGET_REVIEW.md` §12 for the complete list of what a device would be needed to close.

---

## Continuous

- [ ] Update all documents — including `AI_CONTEXT.md`, `docs/WIDGET_ARCHITECTURE.md`, and
      `docs/WIDGET_REVIEW.md` — at the end of every session.
- [ ] Keep lint at 0 errors and `:core:domain` coverage above the 95% gate.
- [ ] Never enable `fallbackToDestructiveMigration` (D-024).
- [ ] Never call `Instant.now()` or `LocalDate.now()` outside the DI module — inject `Clock`.
- [ ] Never duplicate a presentation rule between the app and the widget layer — put it in
      `:core:domain` once, as D-034 did for `showsMeaningfulDayCount` and `defaultEmoji`.
- [ ] When a render model gains a field, verify something actually reads it before calling the
      work done — D-039 and D-040 exist because two fields didn't, for a whole milestone.
- [ ] Any widget color that composites over content the app doesn't draw itself (a launcher
      wallpaper behind a translucent background, so far — GLASS only) needs its worst-case
      contrast reasoned through explicitly, not just checked against one emulator's one
      wallpaper. See D-041, BUG-R008.
