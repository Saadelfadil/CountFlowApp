# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 10

- [ ] **Approve starting 2×1/4×2 size work (the rest of Milestone 5).** Milestone 5A (this
      session, Session 9) was deliberately scoped to the existing 2×2 widget's visual quality
      only — see `docs/WIDGET_DESIGN_REVIEW.md`'s Final Report for the YES verdict and its
      explicit STOP before any size work began. Read that report before approving continuation.
- [ ] **Decide on BUG-011** (widget sticks on a loading spinner after Force Stop until reopened).
      Session 9 gave it a branded initial layout ("CountFlow / Tap to refresh") but deliberately
      did not attempt to fix the underlying recovery gap, per instruction not to defeat Android's
      force-stop semantics. Closing it for real still needs either Milestone 8's refresh
      infrastructure or a deliberate new "tap to retry" affordance — worth a product call on
      whether it's worth pulling forward.
- [ ] **Confirm the countdown label policy** — carried over from Sessions 3 and 4, still
      unanswered. One line in `CountdownConfig` today; now rendered through seven distinct
      layouts (Session 9), which makes the policy question more visible, not less.

---

## P1 — Rest of Milestone 5: additional sizes, multiple independent widgets

- [ ] Sizes 2×1 and 4×2 alongside the existing (now genuinely correct, and now genuinely
      well-designed — Milestone 5A) 2×2, with `SizeMode.Exact` and breakpoint ranges — this also
      closes TD-012 (a launcher ignoring `resizeMode="none"` today has no adaptive fallback to
      fall into).
- [ ] Verify two widgets on the same event can show different styles via
      `WidgetBinding.resolveWidgetStyle` — the mapper and domain model already support this
      (tested), and the configuration screen's per-widget style override (Session 9) exercises
      this path for the first time through real UI, but no test has placed two widgets on the same
      event simultaneously.
- [ ] Verify emoji rendering on a physical device — a real emulator now exists and is stable, but
      emoji glyph coverage is specifically a launcher/OEM font concern the emulator can't stand in
      for (LIM-006).
- [ ] Live widget preview **in the create/edit form itself** — Session 9 built a live preview for
      the widget *configuration* screen (`WidgetPreviewCard`, D-049), which is arguably the more
      valuable surface since it previews the actual per-widget style/toggle/accent choices, but
      the create/edit form (`CreateEventScreen`) still has no preview of its own. Lower priority
      now that the configuration screen has one; revisit if user feedback specifically wants it
      earlier in the flow.
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
      live-observation approach. Would also naturally resolve BUG-011 (stuck loading spinner after
      Force Stop) as a side effect.
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass. **This is also where
      real widget performance/memory/battery numbers finally get measured** — still zero
      measurements from any session as of Session 8, which had a stable device but spent it on
      lifecycle/visual verification instead, deliberately (see `docs/PRODUCT_REVIEW.md`). Pull a
      first rough measurement forward if a stable device is available before Milestone 8.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings. Scheduled for Milestone 6.
- [ ] **TD-002 (Low)** — three empty scaffold modules remain (`:core:notifications`,
      `:core:analytics`, `:core:billing`).
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-008 (Low)** — no archive/complete/delete gesture. Scheduled for the rest of Milestone 5.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested.
- [ ] **TD-012 (Low)** — no adaptive fallback if a launcher ignores `resizeMode="none"`. Closes
      as a side effect of Milestone 5's remaining multi-size work.

Resolved this session: TD-011 (system-tracked corner radius, D-045), TD-014 (mandatory widget-picker
preview), TD-015 (unused vertical space, closed as a side effect of the per-style redesign) — see
`KNOWN_ISSUES.md` Resolved section.

## Open bugs

- [ ] **BUG-011 (High, open since Session 8)** — the widget sticks on a loading spinner after
      Force Stop until the app is reopened by any means. Session 9 replaced the generic spinner
      with a branded "Tap to refresh" prompt but deliberately did not attempt to fix the
      underlying recovery gap (out of scope: defeating Android's force-stop semantics). See P0
      above and `KNOWN_ISSUES.md` for the full, precisely-scoped account.

## Testing gaps

- [ ] No Compose UI tests for the app's own screens (the events feature). The Glance widget
      layer now has this (`CountdownWidgetContentTest`); the app screens are still verified by
      a device script that lives outside the repository.
- [ ] No test for `WidgetConfigurationViewModel` directly — its cancel/confirm/no-orphan
      behaviour was verified on-device in Session 5 (which is how BUG-R005 was found), not by a
      unit test. Worth adding one now that the real behaviour is understood precisely.
- [ ] No real-device instrumented test exists for the widget lifecycle end to end — every
      individual step now has manual real-device confirmation (Session 8) or unit-test coverage,
      but nothing automates the full chain against a real `AppWidgetHost`. Now that a stable local
      emulator is known to exist, worth considering a scripted (not manual) device test here.
- [ ] Glance 1.1.1's `glance-testing` library has no way to assert a composable's resolved
      `ColorProvider` value — found while trying to write a regression test for BUG-R010's fix.
      Both this session's color fixes are verified visually only. Not fixable without either a
      newer Glance version or new test infrastructure this project hasn't needed before.
- [ ] No widget update latency, memory, CPU, or battery measurement exists from any session, even
      though Session 8 had a stable device — deliberately deprioritized in favor of lifecycle and
      visual verification, which had zero prior evidence at all. See `docs/PRODUCT_REVIEW.md`
      "Performance."
- [ ] Glance's Robolectric-based testing API cannot observe actual text wrapping/ellipsis
      behavior — BUG-R011 (word-shaped headline wrapping mid-word) was only regression-tested for
      its classification logic (`WidgetHeadline.isNumeric`), not the visual wrapping itself, which
      needs a real device screenshot to see at all. Same class of gap as the `ColorProvider`
      testing hole found in Session 8 (BUG-R010).

---

## Continuous

- [ ] Update all documents — including `AI_CONTEXT.md`, `docs/WIDGET_ARCHITECTURE.md`,
      `docs/WIDGET_REVIEW.md`, `docs/PRODUCT_REVIEW.md`, `docs/SCREENSHOT_GUIDE.md`,
      `docs/WIDGET_DESIGN_GUIDE.md`, and `docs/WIDGET_DESIGN_REVIEW.md` — at the end of every
      session.
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
- [ ] **Read a manifest/XML value's real-world effect from the actual platform formula, not just
      from what a comment claims.** BUG-R009 (widget rendered 3×2, not 2×2, for the entire
      project's history) existed because `minWidth="180dp"` was never checked against Android's
      documented `dp = 70×cells − 30` cell-size formula until a real launcher's own picker label
      exposed it. The same discipline applies to any other dp value asserted to correspond to a
      specific cell/unit count.
- [ ] **A library's public API surface is not proof of its runtime behavior.** TD-013 (title
      ellipsis) was a correct reading of Glance's Kotlin API that turned out to be an incomplete
      picture of what the underlying `RemoteViews` actually does. Verify claims about rendering
      behavior against one real render before writing them down as fact, not just against
      `javap` output.
