# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 15

- [ ] **Approve Final MVP Release Audit, Billing/Live Updates, or further Milestone 5/8 work,
      after Session 14's essential-settings completion report.** Event CRUD, responsive widgets,
      widget customization, background refresh, basic reminders, and now essential settings are
      all complete and real-device verified — confirm the next milestone before starting it.
- [ ] **Get a real, final privacy-policy URL and wire it into `AboutUiState.privacyPolicyUrl`**
      (Session 14, D-073) — a genuine release blocker, not an engineering task. The About screen's
      Privacy Policy row ships correctly disabled ("Not yet available") until this exists; no
      placeholder or fake URL was substituted.
- [ ] **Get a real on-device `WIDE` (4×2) measurement and screenshot** (TD-016, TD-017) — still
      the one significant Milestone 5 gap, carried over from Session 10. Not attempted this
      session either, which was explicitly scoped to settings, not widget sizing.

Resolved in prior sessions (kept here only as a pointer, not re-litigated): 2×1/4×2 size work
approved and delivered (Session 10); the countdown label policy confirmed permanent (D-051);
archive/complete/delete gestures delivered with a full accessible menu alternative (Session 11,
TD-008 resolved); create/edit live widget preview delivered (Session 11); the coalesced-alarm
background refresh scheduler D-008 always planned, delivered and real-device verified (Session 12,
D-062/D-063); basic event reminders (30/7/1-day/day-of), delivered and real-device verified
(Session 13, D-065 through D-068); essential settings (appearance, notification status, About),
delivered and real-device verified (Session 14, D-069 through D-073). BUG-011 (Force Stop
recovery) remains open by explicit decision — see below; nothing this session changes that
decision.

---

## P1 — Rest of Milestone 5: multiple independent widgets, remaining polish

- [ ] Verify two widgets on the same event can show different styles via
      `WidgetBinding.resolveWidgetStyle` — the mapper and domain model already support this
      (tested), and the configuration screen's per-widget style override (Session 9) exercises
      this path for the first time through real UI, but no test has placed two widgets on the same
      event simultaneously. (Session 10 confirmed the *different-events* case on two different
      size classes at once; the same-event case remains unit-tested only.)
- [ ] Verify emoji rendering on a physical device — a real emulator now exists and is stable, but
      emoji glyph coverage is specifically a launcher/OEM font concern the emulator can't stand in
      for (LIM-006).
- [ ] **Re-measure `WidgetSizeClass` thresholds on a physical device and a second launcher**
      (TD-016) — this session's real numbers (172×224dp for 2×2, 172×104dp for 2×1) are confirmed
      for exactly one emulator/launcher combination; nothing guarantees a different host agrees.
- [ ] Migrate `EventCard`'s swipe gesture off the deprecated `confirmValueChange` parameter
      (TD-018, Session 11) — low priority, still functions correctly, no drop-in replacement API.

---

## P2 — Post-MVP settings scope

Milestone 6's essential scope (theme, dynamic color, notification status, About) is complete
(Session 14). Everything below was explicitly deferred out of MVP by the brief, not forgotten:

- [ ] Backup and restore, and an account/sign-in concept if one is ever needed — neither exists yet
      and neither was in Session 14's scope.
- [ ] **Do the full localisation pass here (TD-007).** Sort names, validation messages,
      empty-state copy, and every field label are currently hard-coded in Kotlin.
- [ ] A real open-source-license enumeration mechanism (Session 14, D-073) — the About screen's
      "Open-source licenses" row ships correctly disabled ("Coming soon"); adding Google's
      `play-services-oss-licenses` plugin is the natural option but is a new dependency, not
      something to pull in "merely" for this per the brief's own instruction — worth deciding on
      its own merits, not as a rushed addition.
- [ ] Revisit `data_extraction_rules.xml` — see D-037. Not a blocker; a second Room database for
      widget bindings only becomes worth it on its own merits.

---

## P3 — Milestones 8 to 9

- [ ] Recurring reminders and custom offsets, if ever prioritized — explicitly out of Session 13's
      MVP scope (`docs/NOTIFICATION_ARCHITECTURE.md`'s own scope note). Would need a real product
      decision on UI shape, not just an engineering extension of `ReminderType`.
- [ ] The remaining half of D-008: a launcher-ticked `Chronometer` for the final 24 hours
      (`CountdownStatus.needsLiveTicking` already marks these), giving second-level ticking on top
      of the coalesced-alarm scheduler delivered in Session 12 (`docs/WIDGET_REFRESH_ARCHITECTURE.md`).
- [ ] R8 keep rules, Baseline Profiles, macrobenchmarks, accessibility pass. **This is also where
      real widget performance/memory/battery numbers finally get measured** — still zero
      profiler-measured numbers from any session; Session 12 gives a reasoned (alarm-count-based)
      battery answer, not an instrumented one (`docs/WIDGET_REFRESH_ARCHITECTURE.md` §11). Pull a
      first rough measurement forward if a stable device is available before this work starts.
- [ ] Firebase, AdMob, Play Billing behind the existing interfaces.

---

## Technical debt

- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings, including Session 14's new Settings
      and About screens. Milestone 6 shipped without this pass — see P2 above.
- [ ] **TD-002 (Low)** — two empty scaffold modules remain (`:core:analytics`, `:core:billing`).
      `:core:notifications` filled in Session 13, `:feature:settings` filled in Session 14 (it was
      already a real module with placeholder screens, not an empty scaffold, so it was never part
      of this entry), and neither is part of this entry.
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested.
- [ ] **TD-016 (Medium, Session 10)** — `WidgetSizeClass` thresholds are calibrated against
      one emulator's one launcher, not confirmed portable. See P1 above.
- [ ] **TD-017 (Medium, Session 10)** — 4×2 (`WIDE`) has no real-device visual confirmation,
      Robolectric only. See P0 above.
- [ ] **TD-018 (Low, new Session 11)** — `EventCard`'s swipe gesture uses a deprecated Material 3
      parameter (`confirmValueChange`); works correctly, no drop-in replacement exists yet.

Resolved this session: TD-008 (archive/complete/delete gesture, full accessible menu alternative).
Resolved in Session 10: TD-012 (`resizeMode="none"` — moot now that resizing is fully supported,
D-053/D-056). Resolved in Session 9: TD-011 (system-tracked corner radius, D-045), TD-014
(widget-picker preview), TD-015 (unused vertical space). See `KNOWN_ISSUES.md` Resolved section
for full detail.

## Open bugs

- [ ] **BUG-011 (High, open since Session 8, decision final per D-052)** — the widget sticks on a
      loading spinner after Force Stop until the app is reopened by any means. Session 9 replaced
      the generic spinner with a branded "Tap to refresh" prompt; the owner has since directly
      confirmed (Session 10, D-052) that no further engineering time goes toward this. Session 12
      built Milestone 8's real alarm-based refresh infrastructure and confirmed, by design and per
      D-052's own standing instruction, that it does **not** change this: Force Stop cancels this
      app's `AlarmManager` alarms and `WorkManager` work exactly as it cancels everything else the
      app scheduled, so the new scheduler is not a fix for this bug and was never meant to be one.
      Still open, unchanged severity — see `KNOWN_ISSUES.md` for the full, precisely-scoped
      account.

## Testing gaps

- [ ] No Compose UI tests for the app's own screens (the events feature). The Glance widget
      layer now has this (`CountdownWidgetContentTest`); the app screens are still verified by
      a device script that lives outside the repository.
- [ ] No test for `WidgetConfigurationViewModel` directly — its cancel/confirm/no-orphan
      behaviour was verified on-device in Session 5 (which is how BUG-R005 was found), not by a
      unit test. Worth adding one now that the real behaviour is understood precisely.
      (`EditEventViewModel`, the form-side sibling of this same "preview never persists" pattern,
      closed the equivalent gap for itself in Session 11 — `EditEventViewModelTest.kt` — but the
      widget configuration screen's own ViewModel remains untested by this same standard.)
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
      `docs/WIDGET_REFRESH_ARCHITECTURE.md`, `docs/NOTIFICATION_ARCHITECTURE.md`,
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
- [ ] **A `@Singleton` built from a "live system value" API can still freeze that value at
      construction.** `Clock.systemDefaultZone()` reads the system's current default zone once,
      when the `Clock` is built, not on every call — a real, nine-session-old bug (D-064) that only
      a genuine, running-process timezone change could surface, which is exactly why it went
      unnoticed until Session 12's first real device timezone test. Worth checking any other
      `@Singleton`-scoped value sourced from "the current X" (not just time/zone) for the same
      construction-time-freeze risk before assuming a live system read stays live.
- [ ] **A value's own "zone-pinned" design intent does not automatically propagate to every
      calculation derived from it.** `EventTarget` has been correctly zone-pinned for a timed
      event since D-014 (Milestone 2), but `Reminder.scheduledTime`, added the same milestone,
      still used the device's current zone unconditionally for its own calendar-day subtraction —
      unnoticed for eleven sessions because nothing before Session 13 both activated a reminder on
      a timed event *and* exercised a real device timezone change (D-065). Worth checking any other
      "N days/hours before X" calculation for the same "which zone does this specific derived
      calculation use" question, independently of what zone the value it derives from uses.
- [ ] **A stale generated value can go unnoticed for exactly as long as nothing displays it.** The
      app's `versionCode`/`versionName` had been frozen at `1`/`"0.1.0"` since Milestone 1 —
      correct-looking Kotlin, never wrong in any test, silently wrong in fact for thirteen real
      releases — because no screen ever read it back until Session 14's About screen did (D-072).
      The same class of gap as D-039/D-040 (a render-model field with no reader), one level up: a
      *build* value with no reader is invisible the same way, for the same reason, and just as long.
      Worth checking any other value sourced once at project setup and never revisited (an app id, a
      package name, a static config default) the same way before assuming "it compiled and nothing
      complained" means it's still correct.
