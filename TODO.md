# CountFlow — TODO

Prioritized. P0 blocks the next session; P1 is the next milestone's work; P2 and below are
scheduled but not imminent.

---

## P0 — Blocks Session 9

- [ ] **Use the local emulator, not the remote one.** Session 8 found a stable local AVD
      (`Pixel_9`) and `emulator` binary already on this machine
      (`~/Library/Android/sdk/emulator/emulator -avd Pixel_9`), launched it directly, and got a
      fully stable device for the whole session — a first, after three sessions of trouble with a
      remote device at `127.0.0.1:6555`. Check for the local binary explicitly
      (`ls ~/Library/Android/sdk/emulator/emulator`), not just `which emulator` — that's exactly
      the check Session 7 skipped, which is why it wrongly concluded no local emulator existed.
- [ ] **Approve starting Milestone 5.** The widget has now been through a finishing pass
      (Session 6), a stabilization audit (Session 7), and a real-device product validation
      (Session 8) — read `docs/PRODUCT_REVIEW.md`'s ranked findings before deciding, since two of
      its High-severity items (style differentiation, the missing picker preview) are exactly
      what Milestone 5 already plans to build.
- [ ] **Decide on BUG-011** (widget sticks on a loading spinner after Force Stop until reopened).
      Not fixed in Session 8 by design — closing it needs either Milestone 8's refresh
      infrastructure or a deliberate new "tap to retry" affordance. Worth a product call on
      whether it's worth a small, self-contained fix sooner.
- [ ] **Confirm the countdown label policy** — carried over from Sessions 3 and 4, still
      unanswered. One line in `CountdownConfig` today; two surfaces and their tests once
      Milestone 5 adds more widget styles that render it.

---

## P1 — Milestone 5: multiple widgets, themes, sizes

- [ ] **Render all seven `WidgetStyle` values distinctly.** No longer just a theoretical gap —
      Session 8 pixel-sampled real device screenshots and confirmed Minimal, Material, Progress,
      and Modern are **exactly** RGB-identical in every state tested. Modern is marked
      `isPremium = true`; a user paying for it currently gets pixels identical to the free
      Minimal default. See `docs/PRODUCT_REVIEW.md` finding #3.
- [ ] **TD-014 — add a widget-picker preview.** Confirmed on a real Pixel Launcher: CountFlow
      shows a blank icon-only card while every other widget in the same picker shows real content.
      `android:previewLayout` (a plain Android XML layout, not Glance) is the documented
      mechanism. High first-impression impact — this is literally the first thing a browsing user
      sees. See `docs/PRODUCT_REVIEW.md` finding #4.
- [ ] **TD-015 — use the widget's vertical space deliberately.** Every screenshot in
      `docs/SCREENSHOT_GUIDE.md` shows roughly a third of the card empty above the content and
      more below. Worth deciding alongside the per-style layout work above, not separately.
- [ ] **TD-011 — read the system's actual widget corner radius**
      (`android.R.dimen.system_app_widget_background_radius` via Glance's `cornerRadius(Int resId)`
      overload, confirmed to exist) instead of the hand-picked 16/20/28dp constants in
      `WidgetThemeResolver`. Decide per-style whether to track the system value or keep an
      intentional difference (ROUNDED's whole premise is being rounder than default).
- [ ] Canvas-drawn circular progress ring for `ProgressStyle.CIRCULAR`, quantized to whole
      percent and cached, budgeted against `6 × screenW × screenH` bytes (LIM-001, LIM-003).
      `WidgetProgress.percent` is already the cache-key shape.
- [ ] Sizes 2×1 and 4×2 alongside the existing (now genuinely correct) 2×2, with `SizeMode.Exact`
      and breakpoint ranges — this also closes TD-012 (a launcher ignoring `resizeMode="none"`
      today has no adaptive fallback to fall into).
- [ ] Verify two widgets on the same event can show different styles via
      `WidgetBinding.resolveWidgetStyle` — the mapper and domain model already support this
      (tested), but no UI lets a user set an override yet.
- [ ] Adopt Google's font-measurement utility from the original sample, but move it off the
      render path, cache it, and replace the deprecated `scaledDensity` (LIM-004).
- [ ] Verify emoji rendering on a physical device — a real emulator now exists and is stable, but
      emoji glyph coverage is specifically a launcher/OEM font concern the emulator can't stand in
      for (LIM-006).
- [ ] A settings/binding-editor surface for `showTitle` / `showEmoji` / `showTargetDate` /
      `showPercentage` — all four are modelled, persisted, and fully wired to the renderer, but
      nothing lets a user set any of them independently of the `WidgetBinding.inheriting()`
      defaults yet.
- [ ] Wire `WidgetRenderModel.target`/`.targetZone`/`.showDate` to an actual rendered date —
      identified in Session 7 as the same *shape* of gap as `showPercentage` was (computed, never
      read), but deliberately not fixed then: it needs new date-formatting logic (no
      `DateTimeFormatter` usage exists anywhere in the codebase yet). Now that TD-015 (unused
      vertical space) is confirmed, this is also a candidate for filling that space usefully.

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

- [ ] **TD-011 (Medium)** — widget corner radii don't track the system's actual clip radius.
      Scheduled for the start of Milestone 5.
- [ ] **TD-014 (Medium, new Session 8)** — no preview image in the widget picker.
- [ ] **TD-015 (Medium, new Session 8)** — significant unused vertical space in every widget
      state.
- [ ] **TD-001 (High)** — migrate off `android.builtInKotlin=false` / `android.newDsl=false`
      before any AGP 10 upgrade. Budget a full session.
- [ ] **TD-007 (Medium)** — localise the remaining UI strings. Scheduled for Milestone 6.
- [ ] **TD-002 (Low)** — three empty scaffold modules remain (`:core:notifications`,
      `:core:analytics`, `:core:billing`).
- [ ] **TD-005 (Low)** — build output noise; disappears with TD-001.
- [ ] **TD-006 (Low)** — title search is ASCII-case-insensitive only.
- [ ] **TD-008 (Low)** — no archive/complete/delete gesture. Scheduled for Milestone 5.
- [ ] **TD-009 (Low)** — the date picker's UTC conversion is comment-guarded but untested.
- [ ] **TD-012 (Low)** — no adaptive fallback if a launcher ignores `resizeMode="none"`. Closes
      as a side effect of Milestone 5's multi-size work.

## Open bugs

- [ ] **BUG-011 (High, open, new Session 8)** — the widget sticks on a loading spinner after
      Force Stop until the app is reopened by any means. See P0 above and `KNOWN_ISSUES.md` for
      the full, precisely-scoped account (confirmed against Force Stop specifically; the gentler
      ordinary-process-death case could not be tested on this device's non-rootable system image).

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

---

## Continuous

- [ ] Update all documents — including `AI_CONTEXT.md`, `docs/WIDGET_ARCHITECTURE.md`,
      `docs/WIDGET_REVIEW.md`, `docs/PRODUCT_REVIEW.md`, and `docs/SCREENSHOT_GUIDE.md` — at the
      end of every session.
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
