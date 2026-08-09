# CountFlow — Widget Design Guide

**Session 9, Milestone 5A.** This document explains *why* each of the seven `WidgetStyle` values
looks the way it does — the design philosophy, the information hierarchy, and when a user should
actually choose it. `CountdownWidgetLayouts.kt` is the execution of the reasoning below, not a
second copy of it; where the two would drift, this document and the code's own KDoc are meant to
say the same thing, and code review should treat a mismatch as a bug in one of them.

This is the only widget size in scope. No 2×1, no 4×2 — see `docs/WIDGET_DESIGN_REVIEW.md`'s
Final Report for why that boundary held for the whole session.

---

## The problem this session solved

Session 8's `docs/PRODUCT_REVIEW.md` pixel-sampled four of the seven named styles — Minimal,
Material, Progress, Modern — and found them **RGB-identical** in every state tested. A user paying
for "Modern" got a widget indistinguishable from the free "Minimal" default. Two more styles
(Glass, OLED) differed only in background color, not layout. Only Rounded's larger corner radius
was a real structural difference, and even that was one number.

The brief for this session was explicit: seven styles need seven different *layout philosophies*,
not seven different paint jobs on the same tree. Below is what each one actually is now, and why.

---

## Shared foundation: the content hierarchy, before any style is applied

Every layout receives the same `WidgetHeadline` — computed once, in `CountdownWidgetContent.kt`'s
`resolveHeadline`, before any style-specific drawing happens. This is what closes the two
redundancy problems the brief named directly:

- **"Tomorrow / Tomorrow."** For a near-term countdown (Today, Tomorrow, Yesterday, Starting
  soon), a bare day count is either noise or wrong — `showsMeaningfulDayCount` already says so.
  The old renderer drew the label as a *second*, separate line beneath a headline that had already
  used the same word. The new one recognizes this case and never lets it happen: `primary` is the
  label word itself, and `secondary` is either the event's clock time (for a timed event) or
  nothing at all (for an all-day one) — a fact the label didn't already say, or no second line.
- **"7 / Next week," used well instead of redundantly.** For an ordinary countdown, `primary` is
  the bare day count and `unit` is its pluralized caption ("day"/"days"), styled separately so a
  layout can make the number huge and the unit a whisper. `secondary` — the "Next week" line — is
  populated **only** when the countdown's label is genuinely `NextWeek`. For every other in-range
  count (`CountdownLabel.InDays`), the label text is "In 7 days" — the same fact the headline
  number already states — so `secondary` is left `null` rather than repeating it. "Next week" is
  the one label that says something the number alone doesn't (*which* calendar week), so it's the
  one case worth a second line.
- **Completed and expired events flip the whole hierarchy**, not just the color. The status word
  ("Completed," "Expired") becomes `primary`; the event's own title — no longer useful as an
  identity row when the status word already dominates the card — moves down into `secondary`
  instead, and the identity row (`showIdentity`) is suppressed so the title isn't drawn twice.

This logic lives once, in the renderer, and every one of the seven layouts below consumes its
output verbatim. A style can choose *how* to draw `primary`/`unit`/`secondary` — size, color,
whether the unit is even shown — but never re-derives *what* they should say.

**A real bug this hierarchy work surfaced, not introduced.** `MINIMAL_HEADLINE_SIZE` (46sp) was
tuned for a 1–3 digit number. The first on-device screenshot of a completed event showed
"Completed" wrapped mid-word into "Compl" / "eted" — Glance has no autosizing text (`LIM-001`'s
sibling limitation, `LIM-004`), so a size tuned for digits doesn't shrink for a longer word.
Fixed with `WidgetHeadline.isNumeric` and a `headlineSize()` selector in every layout that picks a
smaller size for word-shaped headlines, plus `maxLines = 1` everywhere so anything still too long
ellipsizes cleanly (confirmed on-device to actually ellipsize, not hard-clip — the same underlying
`RemoteViews` behavior `TD-013` already established) instead of wrapping. See
`docs/WIDGET_DESIGN_REVIEW.md` for the before/after screenshots.

---

## The seven styles

### Minimal — typography-first

**Philosophy.** The countdown value is the entire point; everything else is a whisper around it.
No progress bar — a bar is a second thing to compete for attention, and Minimal's whole premise is
that there is only one thing to look at.

**Hierarchy.** Centered identity row (optional) → a large, bold, centered headline (46sp for a
number, 26sp for a word) → its unit caption → its secondary line, all centered.

**What differentiates it.** The absence of a progress bar, full stop. Every other style that shows
progress at all uses one; Minimal deliberately doesn't, on the theory that a bar competes with the
number for the one thing this style exists to showcase.

**Corner radius.** System-tracked (`null` in `WidgetTheme`, resolved against
`android.R.dimen.system_app_widget_background_radius` at render time) — Minimal has no opinion
about shape, only about restraint, so it shouldn't assert a radius of its own.

**Choose it when** you want the countdown number itself to be the entire home-screen moment — a
single trip, a single deadline — with nothing else drawing the eye.

### Material — the safe, balanced default

**Philosophy.** Identity, headline, supporting context, and progress all present, dynamic-colored,
nothing fighting for attention. If a user has no reason to pick a different style, this is the one
that should never look like a mistake.

**Hierarchy.** Left-aligned identity row → headline with its unit inline on the same row (not
stacked, unlike Minimal) → secondary line → target date (when enabled) → a labeled progress row
with an optional percent readout beside it.

**What differentiates it.** It's the only style that shows *everything* the render model can show,
left-aligned rather than centered — the layout a settings screen's live preview defaults new users
into, and the one every other style is a deliberate subtraction or addition from.

**Corner radius.** System-tracked — Material's entire premise is being the native-feeling default,
which means matching the shape every other widget on the same home screen already uses.

**Choose it when** you want the countdown to read like a normal, dependable piece of the home
screen — the "no strong opinion, just correct" option.

### Progress — the ring is the widget

**Philosophy.** The progress visualization *is* the widget; everything else supports it. A
determinate circular ring fills most of the card, with the headline sitting inside it the way a
stopwatch's face holds its own reading.

**Implementation.** Glance's own `CircularProgressIndicator` is indeterminate-only (`LIM-001`);
there is no library path to a determinate ring. `CircularProgressRenderer` draws one to a `Bitmap`
via `Canvas`/`Paint.Style.STROKE` with rounded caps, quantized to a whole `percent` (0–100) so the
same ring is never redrawn twice for the same visual state, and cached in a 32-entry LRU
(`LinkedHashMap(accessOrder = true)`) keyed on size/percent/colors/stroke width. Worst case — every
cache slot a distinct combination, never the common case — is `225KB × 32 ≈ 7.2MB`, comfortably
inside the `6 × screenWidthPx × screenHeightPx` budget a host silently enforces by dropping the
widget with no error (`LIM-003`). Ring diameter is `62%` of the shorter cell dimension, clamped to
`64–120dp`, computed from `LocalSize` so it scales sensibly across whatever the host actually grants
this size class.

**The word-headline exception.** The ring only draws for a numeric headline — cramming "Completed"
into an ~80dp circle produces worse results than falling back to the plain centered text every
other centered style uses, no matter how small the font gets. A day count is compact by
construction; a status word is not. `ProgressLayout` checks `headline.isNumeric` and falls back
explicitly rather than forcing a shape that doesn't fit.

**Corner radius.** System-tracked — like Minimal, Progress has no opinion about shape.

**Choose it when** the fraction-of-the-way-there matters as much as the number itself — trip
countdowns, savings goals, anything where "how far along" is worth a glance on its own.

### OLED — as stark as the display technology it's named for

**Philosophy.** One number, one line beneath it if that, nothing competing for the handful of lit
sub-pixels this theme's entire purpose is minimizing. No identity row at all — the burn-in-safe
theme is the one place a user has explicitly chosen bare information over context.

**Hierarchy.** No emoji, no title, ever — headline (50sp number / 28sp word, the largest of any
style) → unit → secondary. Nothing else.

**What differentiates it.** True black (`0xFF000000`), forced and never dynamic — a Material You
dark-mode surface tone cannot guarantee true black even in dark mode, and this theme's entire
reason to exist is minimizing lit sub-pixels on an OLED panel. `isHighContrast = true` in
`WidgetTheme` also routes its muted-text color through the higher-contrast on-surface token rather
than the softer variant every other style uses.

**Corner radius.** System-tracked — OLED's identity is entirely about color, not shape.

**Choose it when** the widget sits on an OLED panel and battery/burn-in matters, or simply for the
starkest possible read — this is the loudest number on the smallest footprint of ink.

### Glass — lighter than Material in every sense

**Philosophy.** Normal (not medium/bold) type weights, softer color use, more air between elements
— meant to feel like it's sitting *above* the wallpaper rather than replacing it, the way a
frosted pane suggests depth without hiding what's behind it.

**Implementation reality.** RemoteViews cannot blur what's behind a widget, so "glass" is
approximated with a translucent dark surface (`0xCC101418`, 80% opaque) rather than a true frosted
effect — noted plainly rather than oversold. Contrast is never sacrificed for that lightness: this
alpha exists specifically because a lighter one (`0x99`, 60%) measured only ~4.9:1 against white
text over a light wallpaper — barely above WCAG AA's 4.5:1 floor — and `D-041` raised it to ~10.8:1
in the same worst case. Every layout decision this session made for Glass inherits that guarantee
unconditionally; lightness is a typographic and spacing choice here, never a contrast one.

**Hierarchy.** Centered identity (normal weight) → centered headline (34sp/22sp, normal weight,
distinct from Material's bold) → unit → secondary → a thin 4dp progress bar, more generously spaced
than Material's.

**Corner radius.** Fixed at 20dp, deliberately not system-tracked — a translucent surface reads
more like glass with a touch more roundness at its edges than a sharp system corner would give it.

**Choose it when** you want the countdown to feel like it's floating over your wallpaper rather
than sitting in an opaque card — best over a wallpaper with some visual interest behind it.

### Rounded — friendly, not just round

**Philosophy.** The brief asked for something beyond corner radius alone, and this style delivers
it: supporting text sits inside a pill-shaped chip rather than bare on the background — the one
structural difference from Material beyond shape.

**Hierarchy.** Centered identity → centered bold headline (34sp/22sp) → unit → the secondary line,
when present, inside a `cornerRadius(999.dp)` pill chip with its own background — a small piece of
real structure, not a paint change → a progress bar.

**Corner radius.** Fixed at 28dp — the softest of any style, and deliberately *not*
system-tracked: "rounder than default" is this style's entire reason to exist, so it cannot track a
system value it's specifically supposed to exceed.

**Choose it when** you want the friendliest, least severe-looking card on the home screen — well
suited to personal or lighthearted countdowns rather than deadlines.

### Modern — editorial, not decorative

**Philosophy.** Top-and-start anchored like a masthead, not centered like every other style —
dense enough that title, headline, target date, and percentage can all coexist without feeling
cluttered. The one layout in this set built around *alignment* as the differentiator, not just type
or color.

**Hierarchy.** Left-aligned identity (12sp, the smallest of any style) → left-aligned headline
(30sp/20sp) → unit → secondary → target date (when enabled, 11sp) → percent readout (accent-colored,
when enabled) → a thin progress bar. Every element that can be shown, is — the only style besides
Material that shows the target date and percentage together, but stacked densely instead of spread
out.

**Corner radius.** Fixed at 8dp, deliberately tighter than system — Modern's editorial density
reads better with a crisper corner than the softer system default, the same way a magazine masthead
uses sharp rules, not rounded ones. `isHighContrast = true` here too.

**Choose it when** you want maximum information density — every fact the render model can produce,
visible at once, read top-to-bottom like a small dashboard rather than glanced at like a single
number.

---

## Corner radius: TD-011 resolved

`android.R.dimen.system_app_widget_background_radius` (API 31+) is the dimension Android itself
uses to clip a widget's outer bounds — it varies by device and, from Android 12 onward, by theme.
`WidgetTheme.cornerRadiusDp` is now nullable: `null` means "track the system value," resolved in
`CountdownWidgetContent.widgetCornerRadius()` via Glance's `cornerRadius(Int resId)` overload,
which accepts a resource id directly rather than requiring a resolved dp value at composition time.

Four styles (Minimal, Material, Progress, OLED) track the system value — each has "no opinion"
about shape for a reason specific to that style, documented inline in `WidgetThemeResolver`. Three
styles (Glass 20dp, Rounded 28dp, Modern 8dp) intentionally override it, each for a reason that
follows from what the style is *for*, not an arbitrary number — see each style's section above.
Full reasoning: `DECISIONS.md` D-045.

---

## The widget picker preview

Confirmed, on device, as a real gap in Session 8 (`TD-014`): CountFlow's entry in the Pixel
Launcher's widget tray showed its app icon centered on a blank card, while every other widget in
the same list showed live-styled content. The brief called this "mandatory."

`android:previewLayout` (API 31+) is the correct mechanism — a plain Android XML layout the
launcher inflates *directly*, not a Glance composition (Glance has no way to render a live
composition into the picker itself). `res/layout/widget_preview.xml` approximates the Material
style with realistic content ("✈️ Trip to Kyoto / 7 / days / Next week" plus a progress bar) over
`res/drawable/widget_preview_background.xml`, a solid rounded rect matching the pixel-sampled
Material background color from Session 8's screenshots. Wired via
`countdown_widget_info.xml`'s `android:previewLayout` attribute. Confirmed on-device this session
— see `docs/WIDGET_DESIGN_REVIEW.md`'s picker screenshot.

---

## The configuration screen's live preview

The brief required the configuration screen to show a live preview that "reacts immediately" with
no save required. `WidgetConfigurationViewModel` now runs a two-step flow — pick an event, then
customize style/progress/toggles/accent — where every control in step two calls
`WidgetRenderModelProvider.preview(event, binding)`, a pure, synchronous, no-I/O function that
reuses the exact production rendering pipeline (`CountdownEngine` → `WidgetRenderMapper`) for a
binding that has not been, and may never be, written to the database. This is what keeps the
screen's "no orphan bindings" guarantee intact while still letting every keystroke redraw the
preview: nothing is persisted until `onConfirm()`, the one method in the ViewModel that writes
anything at all.

`WidgetPreviewCard` draws that model with plain Compose (`WidgetConfigurationActivity` is a normal
Activity; Glance cannot render a live composition inline inside one) — deliberately **not** a
pixel-identical reproduction of the seven Glance layouts above. It reuses the same
`resolveHeadline` decision the real renderer uses, so *what* it shows is never faked, but *how* it
draws varies by style through a single simplified card (alignment, background, corner radius, ring
vs. bar) rather than seven independent compositions. Close enough to make style, toggle, and color
choices meaningful before saving; not a substitute for a real on-device screenshot.

---

## Accent color

The picker (`core/designsystem/…/component/AccentColorPicker.kt`) offers Dynamic Material You (an
outlined circle with an "A" glyph — `material-icons-extended` is deliberately excluded from this
project for bundle size, so no icon glyph was available) plus eight curated preset colors,
deliberately not an unrestricted RGB picker — consistent with the brief's MVP scope. Wired into
both the create/edit form (`AccentColor` persisted on the event itself) and the configuration
screen's step two (`accentColorOverride`, a per-widget override on top of the event's own default,
following the same override-else-default precedence `WidgetBinding` already applies to style and
progress style, D-013).

---

## Force Stop / BUG-011 — investigated, not defeated

The brief asked whether the generic Glance loading spinner could be replaced with something
branded, and whether the underlying force-stop recovery gap could be fixed. Both were investigated
honestly:

- **The spinner is now branded.** `res/layout/widget_initial_layout.xml` — a plain Android XML
  layout, "CountFlow" plus "Tap to refresh," styled to match the app's dark surface — replaces
  Glance's generic `@layout/glance_default_loading_layout` as `android:initialLayout`.
- **The underlying recovery gap is unchanged, and this document says so plainly rather than
  implying otherwise.** `android:initialLayout` is shown before the first `provideGlance` call
  completes *and*, per Session 8's finding, whatever a widget falls back to after Force Stop with
  no periodic trigger to redraw it. Replacing the layout changes what the stuck state *looks like*
  — no longer a generic spinner, now a branded prompt that at least tells the user what app it
  belongs to — but does not make the widget self-heal. Android's Force Stop semantics cancel all of
  an app's scheduled work by design; defeating that would mean fighting the platform, which this
  session was explicitly told not to do. `KNOWN_ISSUES.md` BUG-011 remains open, its resolution
  path unchanged: Milestone 8's alarm-based refresh infrastructure, or a deliberate "tap to retry"
  affordance, whichever is prioritized first.
