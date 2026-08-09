# CountFlow — Widget Review (Milestone 4.5: Widget Stabilization)

> **Superseded in part by Session 8 — read `docs/PRODUCT_REVIEW.md` and `docs/SCREENSHOT_GUIDE.md`
> first.** This document is a faithful record of what Session 7 could establish with **no working
> device at all**; every "not verifiable this session" statement below was true when written.
> Session 8 got a stable, self-controlled local emulator for the first time in this project's
> history and closed most of what this document lists as unconfirmed: real widget placement
> (TD-010, now resolved), the eleven-scenario lifecycle table in §10 (create, update, delete,
> reconfigure, app update, and reboot all now have direct device evidence — reboot survival in
> particular had never been attempted before), and it corrected one finding outright (§4's title
> truncation claim — TD-013 — turned out to be wrong on a real render). Session 8 also found and
> fixed a real, previously invisible defect this document's own reasoning could not have caught:
> the widget's actual footprint was 3×2, not the 2×2 this document (and every document before it)
> assumed throughout. Kept below unedited as the historical record of Session 7's reasoning-only
> pass; do not treat its device-dependent conclusions as current without cross-checking
> `docs/PRODUCT_REVIEW.md`.

**Session 7.** Scope: stabilize and audit the widget shipped in Milestone 4/4.5 as if it were
going into production tomorrow. No new features — see `TODO.md`'s explicit exclusion list
(2×1/4×2 sizes, multiple layouts, the circular progress renderer, Live Updates, lockscreen,
settings, premium, notifications). Everything below is either a verification result, a finding,
or a fix; nothing is a proposal for new work beyond what's already tracked in `TODO.md`.

**Read this section first if you read nothing else:** this session had **no working device**.
Every finding below is labeled by how it was actually established — a passing/failing automated
test, direct code reading, or (for anything requiring a real launcher, real memory profiler, or
real battery stats) explicitly marked **not verifiable this session**. Section 12 lists exactly
what that leaves unconfirmed. Treat any claim not labeled "verified on device" as unconfirmed by
this document, however confident the reasoning behind it.

---

## 1. Environment note

The test device from Session 6 (a GUI-mode emulator reachable at `127.0.0.1:6555`) was
unreachable for the entirety of this session — `adb connect` returned `Connection refused` on
every attempt, including after `adb kill-server`/`start-server`. No `emulator` binary or AVD is
available locally to start a replacement. This blocks every task in the brief that requires a
running device: live lifecycle verification (create/update/delete/reconfigure/restore/orientation
change/launcher restart/process death/reboot), widget update latency, memory usage, battery
impact, and testing across multiple launchers (Pixel/Samsung/third-party — only one launcher was
ever reachable even in Session 6, and that session didn't complete a placement either).

What follows instead: a rigorous static audit (architecture, SOLID, dependency graph, public API
surface), a UX/accessibility review done by reading the actual rendered values (colors, sizes,
contrast ratios, string lengths) against the constraints they'll actually hit, and the one kind of
performance measurement that doesn't need a device — the pure-Kotlin compute path, which is
real JVM-measured, not reasoned about.

---

## 2. Architecture review

**Module boundary holds.** `grep -rn "com\.countflow\.app" widget/` returns nothing — `:widget:glance`
still has zero compile-time knowledge of `:app`, confirming D-035 hasn't eroded. `:widget:engine`
remains `countflow.jvm.library`; nothing in it imports `android.*`. This is enforced by the build,
not just observed.

**Dependency graph.** Traced by hand from every module's `build.gradle.kts`:

```
:widget:glance ──► :widget:engine, :core:designsystem, :core:common
:widget:engine ──► :core:domain
:app           ──► :widget:glance, :widget:engine (both needed — :widget:glance's dependency on
                     :widget:engine is `implementation`, not `api`, so :app must declare its own
                     to inject WidgetRefreshScheduler in CountFlowApplication; this is correct
                     Gradle hygiene, not redundancy)
```

No cycle. No module reaches back "up" the graph. `:widget:engine`'s Hilt-free, dependency-free
design (`api(projects.core.domain)` plus `kotlinx-coroutines-core`, nothing else) means the
module compiles and tests in isolation — confirmed by `:widget:engine:test` needing no Robolectric
and finishing in about a second.

**Injection graph.** All widget-related Hilt bindings install into `SingletonComponent` —
appropriate, since nothing here is scoped narrower than the application (no per-widget or
per-Activity component is needed given the current design). Traced every constructor: no class
injects something that (transitively) injects it back. No cycle.

**SOLID, read against every class added in Milestone 4:**

| Class | Read |
|---|---|
| `WidgetThemeResolver` | SRP: style → theme, nothing else. OCP: an eighth style is one `when` arm plus one test. Fine. |
| `WidgetProgressEngine` | SRP: countdown + style → progress numbers. Fine. |
| `WidgetRenderMapper` | Slightly broader (mapping + the `showPercentageText` conjunction) but still one cohesive responsibility — "produce the model" — not two. Fine. |
| `WidgetRenderModelProvider` | Two methods (`observe`/`get`) are one responsibility in two shapes, not two responsibilities. Fine. |
| `WidgetLifecycleCoordinator` | SRP: what happens when widgets go away. Fine, and small (44 lines). |
| `WidgetRefreshScheduler` (interface) / `GlanceWidgetRefreshScheduler` (impl) | Textbook DIP — the engine depends on an abstraction; Milestone 8 can swap the implementation without touching `:widget:engine`. |
| `CountdownGlanceWidget` | Thin: resolve id, load once, hand off to `Content`. Fine. |
| `CountdownWidgetContent` | The biggest file (now 235 lines with this session's fixes). Still one function doing one thing — render a model — but see §7 for a considered-and-rejected extraction. |
| `WidgetConfigurationActivity`/`ViewModel`/`UiState` | Standard MVVM triad, matches the pattern already established by `EventsViewModel` elsewhere in the app. Consistent, no drift. |
| `WidgetActions` (`OpenAppAction`, `OpenConfigurationAction`) | Two small `ActionCallback`s. Considered merging into one parameterized action — rejected: they do genuinely different things, and two obvious classes read faster than one with a branch. |

**Naming and package structure.** `:widget:engine`'s packages (`model`, `theme`, `progress`,
`mapper`, `provider`, `lifecycle`, `refresh`) are single-concern, singular, and mirror the
convention `:core:domain` already established (`model`, `countdown`, `validation`,
`repository`). No drift, no inconsistency found.

**Public API surface — one real finding, fixed.** `WidgetThemeResolver`, `WidgetProgressEngine`,
and `WidgetRenderMapper` were all `public` `object`s with no consumer outside `:widget:engine`
itself (`WidgetRenderModelProvider`, in the same module, is their only real caller — confirmed by
grep before changing anything). Tightened all three to `internal`. Verified empirically, not just
reasoned: `:widget:engine:compileTestKotlin` (same-module tests still see `internal` members) and
`:widget:glance:compileDebugKotlin` (never referenced them, so nothing broke) both still compile.
This is the session's one clear "remove an abstraction's unnecessary exposure" finding — the
classes themselves were already correctly scoped in responsibility, only their visibility was
wider than any real caller needed.

---

## 3. Simplification pass

Read every class added in Milestone 4 and Session 6 against the question the brief asked:
"does this abstraction solve a problem that exists today?"

**Removed exposure (see §2):** `WidgetThemeResolver`, `WidgetProgressEngine`,
`WidgetRenderMapper` → `internal`. This is the only change that reduces surface area.

**Considered, and deliberately left alone:**

- **Extracting `CountdownWidgetContent`'s color-resolution block into a helper function or a
  small `WidgetColors` value class.** It's about 15 lines, executes once per render, top-to-bottom,
  no branching a reader has to hold in their head across a function boundary. Pulling it into a
  separate file or a new type would be indirection with no payoff — nothing else calls it, and it
  isn't independently testable in a way that matters (`CountdownWidgetContentTest` already
  exercises it through the whole render). Left inline.
- **A `WidgetColorPalette` abstraction generalizing `ForcedBackgroundPalette`.** There is exactly
  one forced-background palette today. A second one doesn't exist yet and might never need to —
  generalizing for a single case is exactly the premature abstraction the brief warns against.
  Left as the concrete `private object` it is.
- **Merging `WidgetEntryPoint` and `WidgetGlanceModule`.** Different Hilt annotation shapes
  (`@EntryPoint` interface vs. `@Module`/`@Binds` abstract class); each is already minimal
  (~20 lines). Merging them would save one file at the cost of mixing two different Hilt concepts
  in one place. Not worth it.
- **A `WidgetTypography`/`WidgetSpacing` object formalizing the `private val` constants at the
  bottom of `CountdownWidgetContent.kt`.** Nine constants, one file, one reader. A dedicated
  object earns its keep once a second file needs the same scale — `:widget:glance` has one
  renderer today. Left as plain file-level constants.

No class, interface, or file was found that solves a problem that no longer exists — the
architecture built in Milestone 4 held up under this pass. The one real finding was excess
*visibility*, not excess *structure*.

---

## 4. UX review

Read against the exact dimensions and values in the code, not a screenshot (none was available —
see §1). Each item names the actual number behind the assessment.

| Dimension | Finding |
|---|---|
| **Hierarchy** | Day count (34sp bold) > title (14sp medium) > label/percent (13sp/12sp). Correct order for a countdown widget — the number is what a user glances at first. |
| **Readability** | Emoji 20sp, title 14sp — both comfortably above the ~11sp floor where RemoteViews text starts looking cramped at typical launcher DPI. No finding. |
| **Progress visibility** | Bar height raised to 6dp this session (was Glance's unstyled default, likely 4dp) specifically so it doesn't disappear against `PROGRESS_HEIGHT`-adjacent text at small sizes. No further issue found. |
| **Emoji placement** | Leading position in the title row, 6dp gap before the title. Standard, unremarkable, no finding. |
| **Title truncation — real finding, Medium.** | `Event.MAX_TITLE_LENGTH = 120` code points, but the widget's title `Text` has `maxLines = 1` and Glance 1.1.1's `Text` composable has **no overflow/ellipsis parameter at all** (confirmed via `javap` on the AAR — the only params are text, modifier, style, maxLines). A title anywhere near the 120-character ceiling will be hard-clipped mid-character with no "…" — standard RemoteViews `TextView` behavior without an explicit `android:ellipsize`, which Glance's Text does not expose a way to set. Not a crash, not a broken layout — just an abrupt, unsignaled cutoff. See §8, ranked Medium. |
| **Long titles** | Same finding as above. `GlanceModifier.defaultWeight()` on the title (added this session) correctly keeps it from pushing the emoji or the row's bounds — the row layout itself is safe, only the *text itself* clips ungracefully past one line's width. |
| **Small widgets** | Only one size exists (`SizeMode.Single`, 180dp×110dp declared, `resizeMode="none"`). **Real finding, Low.** If a non-compliant launcher ignores `resizeMode="none"` (some OEM launchers are known to override widget size hints), Glance will still render for the single declared size regardless of the actual allotted space, since `SizeMode.Exact`/`Responsive` were never adopted. Content would clip at the RemoteViews level with no adaptive fallback. Can't be observed without a non-compliant launcher, which this session had no access to at all (see §1). |
| **Dark wallpapers** | GLASS and OLED's forced backgrounds (translucent dark / true black respectively) are designed for this case and were already fine here — the risk was always the opposite case (see next row). |
| **Light wallpapers — real finding, High, fixed this session.** | GLASS's background is a **translucent** overlay (`0x99101418` originally — 60% opaque), composited by the launcher over whatever wallpaper sits behind the widget. Over a fully light/white wallpaper, that composites to roughly mid-gray, and the white text `ForcedBackgroundPalette.onSurface` draws on top measured at **~4.9:1 contrast — barely above WCAG AA's 4.5:1 floor for normal-size text**, with no margin for a wallpaper any lighter or a display any less accurate than assumed. Fixed by raising the alpha to `0xCC` (80% opaque), which recomputes to ~10.8:1 over the same worst case — comfortably past WCAG AAA (7:1). A regression test (`WidgetThemeResolverTest`, "glass stays opaque enough...") now asserts the alpha never regresses below the floor this reasoning depends on. See DECISIONS.md D-041, KNOWN_ISSUES.md BUG-R008. |
| **OLED theme** | `TRUE_BLACK = 0xFF000000`, fully opaque — no wallpaper-bleed risk at all, unlike GLASS. Text contrast against it is maximal (21:1) by construction. No finding. |
| **Contrast, generally** | The five non-forced-background styles (Minimal, Material, Progress, Rounded, Modern) inherit `GlanceTheme.colors.*` — the system's own dynamic Material You scheme, which Android itself guarantees appropriate on/surface pairing for. No finding there; the only two styles that ever risked a self-inflicted contrast problem were the two that override the background, and both are now addressed. |
| **Touch targets** | The entire card is one `clickable` region at the widget's full footprint (180dp×110dp declared minimum) — far past Google's 48dp minimum. No finding. In the configuration picker, `EventPickerRow`'s `Card` wraps a title (`titleMedium`, ~16sp) plus optional caption (`labelSmall`) inside 16dp padding — estimated well past 48dp tall by line-height + padding math, though not measured on an actual device this session. |
| **Material 3 compliance — real finding, Medium.** | ARCHITECTURE.md D-001 explicitly flagged `appWidgetInnerCornerRadius()` — reading `android.R.dimen.system_app_widget_background_radius` so a widget's corner radius matches what the *system* actually clips it to — as a "keep, adopt close to verbatim" item from Google's canonical sample. It was never adopted. `WidgetThemeResolver`'s corner radii (16/20/28dp) are hand-picked constants with no tie to the system value, which varies by device and Android version (notably themed on Android 12+). On a device whose system radius differs meaningfully from these constants, the widget's own rounded corners and the launcher's outer clip mask could visibly disagree — either the background's straight corners peek past the clip, or the clip visibly cuts inside the drawn radius. Not attempted as a fix this session (needs an Android `Context.resources` read that belongs in `:widget:glance`, plus a product decision on which styles should track the system value versus keep an intentionally different one, e.g. ROUNDED). Tracked as new technical debt; see §9. |
| **Dynamic color** | `GlanceTheme { }` wraps the whole render (`CountdownGlanceWidget.Content`), giving every non-forced color access to the system's Material You palette. `minSdk 31` means this is always available — no fallback path needed or missing. No finding. |

---

## 5. Accessibility review

- **One `contentDescription` per card** (`GlanceModifier.semantics { contentDescription = … }`),
  built from exactly the visible fields — added Session 6, re-verified this session by reading
  `widgetContentDescription()` against every visibility flag it should respect (`showTitle`,
  `showPercentageText`). Correct: a hidden field is excluded from the description, not announced
  despite not being drawn.
- **Glance 1.1.1's semantics surface is narrow** — confirmed again this session via the same AAR:
  only `contentDescription` and `testTag` exist. There is no Compose-UI-style
  `clearAndSetSemantics` to explicitly suppress child nodes from the accessibility tree, so a
  screen reader *may* still be able to drill into the individual `Text` nodes beneath the
  card depending on the launcher's own accessibility tree construction from the resulting
  `RemoteViews` — this is a real limit of the framework surface, not a gap CountFlow left open.
  Documented, not fixable within this library version.
- **Unconfigured state** has its own description ("Tap to choose a countdown to show"), separate
  from the configured state's dynamically built one. Correct.
- **No live-region or dynamic-update accessibility concern** — the widget doesn't animate or
  change without a full `provideGlance` re-render, so there's no "silent update a screen reader
  misses" failure mode to check for at this milestone's scope (no live ticking yet — Milestone 8).
- **Not verifiable this session:** actual TalkBack behavior on the composited `RemoteViews` in a
  real launcher. Everything above is a static read of the semantics API surface and this session's
  code, not a spoken-output test.

---

## 6. Performance

**Measured, real, reproducible — the compute path.** `CountdownEngine.countdownAt` +
`WidgetRenderMapper.map` (everything that decides what a widget shows, zero I/O) — 200,000
iterations, JIT-warmed, on the development machine: **~505ns/call**, matching last session's
number exactly (the mapper's logic didn't change in a way that would move this). This remains not
a performance concern at any plausible widget count.

**Not measured this session, and not measurable without a device:**
- Cold widget creation (first `provideGlance` call after placement, including the Room query and
  the RemoteViews inflation/transport to the launcher process).
- Widget update latency end-to-end (Room write → `Flow` emission → `updateAll` → launcher redraw).
- Widget refresh cadence under real usage.
- Memory allocations during a render pass, and the widget's resident memory in the launcher
  process.
- Battery impact of `GlanceWidgetRefreshScheduler`'s live `Flow` observation while the app is
  backgrounded but not killed.

**Reasoned, not measured, about memory specifically:** this milestone's renderer draws no bitmaps
— the circular progress ring (the only bitmap-producing code planned for the widget system) is
explicitly Milestone 5 work (LIM-001, LIM-003) and does not exist yet. The `6 × screenW ×
screenH` byte budget that bitmap work will need to respect is therefore not yet a live constraint
anywhere in the current code. This is a structural observation, not a memory measurement.

---

## 7. Battery

No change to the refresh strategy this session. `GlanceWidgetRefreshScheduler` still does exactly
two things: prune orphans once at startup, and redraw on every `observeEventsWithWidgets()`
emission while the app process is alive (D-036). No polling, no `updatePeriodMillis`
(`android:updatePeriodMillis="0"` in the widget-info XML, confirmed unchanged), no animation was
added anywhere this session — the brief's explicit "no animations" held.

**Not verifiable this session:** actual battery draw. The honest statement is structural: nothing
added this session introduces a new wakeup source, a new poll loop, or new background work. The
real unmeasured cost — as already documented — is Milestone 8's territory, not this milestone's.

---

## 8. Bugs found and fixed this session

| ID | Severity | What | Fix |
|---|---|---|---|
| BUG-R008 | **High** | GLASS's translucent background could drop text contrast to ~4.9:1 (barely passing WCAG AA) over a light/white wallpaper — a case entirely outside this app's control, unlike every other style's background. | Raised the background alpha from `0x99` to `0xCC` (~10.8:1 in the same worst case). Regression test added asserting the alpha never regresses below the floor. |

No other runtime defects were found this session. The two public-API-visibility findings (§2) and
the four UX findings that were *not* fixed (title truncation, resize-mode risk, corner radius,
plus the unresolved `showDate`/`target`/`targetZone` gap noted below) are technical debt, not
defects — nothing is broken, several things are simply unfinished or unverifiable without a
device.

**One more dead-field observation, not fixed.** `WidgetRenderModel.target`, `.targetZone`, and
`.showDate` are computed by the mapper on every call but never read by `CountdownWidgetContent` —
the same *shape* of gap as last session's `isHighContrast`/`showPercentage` (BUG-R006, BUG-R007).
The difference: closing it needs new locale-aware date-formatting logic (no `DateTimeFormatter`
usage exists anywhere in the codebase yet to reuse — confirmed by search), which is materially new
work, not a one-line wire-up like `showPercentage` was. Given this session's explicit "no new
features" scope, this is recorded as a finding for Milestone 5, not fixed now. See TODO.md.

---

## 9. Technical debt (new this session)

- **TD-011 (Medium) — Widget corner radii are hand-picked constants, not tied to the system's
  actual widget-clip radius.** ARCHITECTURE.md D-001 flagged `appWidgetInnerCornerRadius()` /
  `android.R.dimen.system_app_widget_background_radius` as worth adopting; it never was. See §4.
- **TD-012 (Low) — `resizeMode="none"` is not enforced by every launcher.** A launcher that
  ignores the hint and resizes the widget anyway will see content clip, since `SizeMode.Single`
  has no adaptive fallback. Unverifiable without a non-compliant launcher, which this session had
  no access to at all.
- **TD-013 (Low) — Title truncation has no ellipsis.** Glance 1.1.1's `Text` has no overflow
  parameter; a title near `Event.MAX_TITLE_LENGTH` (120 code points) clips abruptly at one line.

Full entries with resolution paths are in `KNOWN_ISSUES.md`.

---

## 10. Widget lifecycle — verification status, scenario by scenario

The brief asked for eleven scenarios verified live. None could be — see §1. What follows is each
scenario mapped honestly to the *strongest* evidence that actually exists for it today.

| Scenario | Evidence | Confidence |
|---|---|---|
| **Create** | `WidgetConfigurationViewModel.onEventSelected` → `WidgetBindingRepository.upsertBinding` — verified on a real device in **Session 5** (database inspected directly after the write). Not re-verified this session. | Device-verified, one session old. |
| **Update** (event edited) | `GlanceWidgetRefreshScheduler` subscribes to `observeEventsWithWidgets()`; a Room-backed `Flow` re-emitting on write is standard, well-understood Room behavior, and `WidgetRenderModelProviderTest`'s "observing re-emits when the binding changes" exercises the equivalent path with a fake repository. | Unit-tested; not device-verified this or last session. |
| **Delete** (event deleted) | The `widget_bindings → events` foreign key cascades on delete (Milestone 2 schema). `WidgetRenderModelProviderTest`'s "an unbound widget produces no render model" confirms the provider's null-handling once a binding is gone; `CountdownWidgetContentTest`'s unconfigured-state test confirms the renderer's response to a null model. | Unit-tested end to end (DB cascade → provider → renderer), never chained together on a real device. |
| **Reconfigure** | Same code path as Create — `onEventSelected` reuses the existing binding row if the event is unchanged, else writes a fresh one. Verified on a real device in Session 5 for the cancel/no-orphan path specifically; the "point at a different event" case was not separately exercised on-device. | Partially device-verified (Session 5), not fully. |
| **Restore** (backup) | `WidgetLifecycleCoordinator.pruneOrphans`, run at startup, is the entire mitigation (D-037) — `WidgetLifecycleCoordinatorTest`'s "pruning keeps only the widgets the launcher still reports" and "...against an empty live set removes every binding" cover the logic directly. | Unit-tested; the actual Android backup/restore round trip has never been exercised, on any device, in any session. |
| **Orientation change** | `WidgetConfigurationActivity` declares no `android:configChanges`, so the system's default recreate-and-restore-from-ViewModel behavior applies — `hiltViewModel()` correctly survives this by design (standard Android Architecture Components behavior, not CountFlow-specific code). The widget itself doesn't rotate; `SizeMode.Single` means it wouldn't adapt even if the launcher's available space changed (e.g., a foldable unfolding) — a known, already-documented limitation, not new. | Reasoned from code + platform guarantees; not observed. |
| **Launcher restart** | No CountFlow-side state lives in the launcher process — bindings are in Room, not launcher memory. A restarting launcher re-requests `RemoteViews` through the system's normal `APPWIDGET_UPDATE` mechanism, which `CountdownGlanceWidgetReceiver` already listens for. | Reasoned from architecture (no launcher-side state to lose); not observed. |
| **Process death** | `provideGlance` reads fresh from `WidgetRenderModelProvider` → Room on every invocation; nothing is cached in a singleton the way Google's original sample's `object WeatherRepo` was (the specific defect ARCHITECTURE.md D-002 was written to avoid). Correctly stateless by construction. | Reasoned from architecture; not observed. |
| **Device reboot** | No custom `ACTION_BOOT_COMPLETED` receiver exists — none is registered in the manifest, confirmed this session. Relying entirely on the platform's own default behavior of re-triggering `APPWIDGET_UPDATE` for existing widgets after boot, independent of `updatePeriodMillis`. This is standard Android platform behavior, not something CountFlow implements itself, and is the correct baseline for a widget that doesn't yet need `ACTION_BOOT_COMPLETED` for anything else ( `Chronometer` re-basing is Milestone 8-only, and doesn't exist yet). | Relies on platform-default behavior; genuinely unverified by anyone, any session, since it needs an actual reboot. |
| **Multiple launchers** (Pixel / Samsung / third-party) | None available this session — only one emulator was ever reachable in Session 6, and even that session didn't complete a placement (see SESSION_SUMMARY.md Session 6). | Not attempted; no access at all. |

**Net read:** every scenario has *some* evidence — none has full, live, device-verified coverage
for this specific milestone. Create/Reconfigure are the strongest (real Session-5 device
evidence). Device reboot and multi-launcher are the weakest (pure platform-default reliance,
zero direct observation ever). This asymmetry is the single most important input to the
production-readiness verdict in §11.

---

## 11. Production readiness

Reading the evidence in §§2–10 together, not any one section in isolation:

**What is genuinely solid:** the architecture (module boundary, dependency graph, injection
graph — all traced and clean), the domain-side correctness (countdown math, unchanged and still
at 97% coverage), the binding lifecycle's *logic* (thoroughly unit-tested, and the two most
security/data-integrity-sensitive paths — no-orphan-bindings, cascade-on-delete — have real
Session-5 device evidence behind them), and this session's one concrete defect (GLASS contrast)
found and fixed with a regression test guarding it.

**What is not yet solid enough to call "verified":** anything that only a real device, a real
launcher, or real elapsed wall-clock time (a reboot) can prove. That is a wide net — it includes
ordinary widget update latency, which is the single most basic thing a "does this widget work"
smoke test would check, and which no session has yet measured on a device.

**Verdict is in the Final Report below, ranked, not buried in prose here.**

---

## 12. What this session could not verify, restated plainly

Everything in this list is a real gap in *evidence*, not a known defect — the distinction matters
and is preserved throughout this document rather than collapsed into one undifferentiated "risk"
bucket:

- Live widget placement through a real `AppWidgetHost` (TD-010, still open, three sessions running).
- Widget update/creation/refresh latency, on any device, ever.
- Memory usage, on any device, ever.
- Battery impact, on any device, ever.
- TalkBack's actual spoken output for the widget's `contentDescription`.
- Rendering across Pixel Launcher, Samsung One UI, or any third-party launcher — only one has ever
  been reached (Session 6), and not to completion.
- A real orientation change, launcher restart, process death, or device reboot, observed rather
  than reasoned about.
