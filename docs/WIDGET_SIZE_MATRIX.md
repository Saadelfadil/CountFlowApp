# CountFlow — Widget Style × Size Matrix

**Session 10, Milestone 5B.** Seven styles (`docs/WIDGET_DESIGN_GUIDE.md`) times three sizes
(`WidgetSizeClass.kt`) is 21 combinations. This document names the intended hierarchy for every
one of them — not "does it compile," but "what is this specific combination *for*." The
implementation is `CountdownWidgetLayouts.kt`'s 21 `<Style>Layout[Compact|Wide]` composables; this
document is why each one looks the way it does.

## The three sizes, and the real numbers behind them

| Size class | Footprint | Real measured/reasoned dp (this session) | What it's for |
|---|---|---|---|
| `COMPACT` | 2×1 | **172×104dp — measured** on a real device, real resize | Glanceability: one fact, not a dashboard |
| `STANDARD` | 2×2 | **172×224dp — measured** on a real device, default placement | The Session 9 design target, unchanged |
| `WIDE` | 4×2 | **~320×224dp — reasoned**, not measured this session | A second column of room, not stretched text |

**Read this before the table below.** The first version of `WidgetSizeClass.kt`'s thresholds came
from Android's `dp = 70×cells − 30` cell-size formula — the same formula BUG-R009 (Session 8)
exists to remind every session to verify against a real device, not trust. This session repeated
that exact mistake once, on a new axis, and then corrected it the same way BUG-R009 was corrected:
a real device disagreed with the formula. A real 2×2 CountFlow widget on this session's Pixel
Launcher measured 172×224dp — not the formula's 110×110dp. The same widget resized to a real,
launcher-confirmed 2×1 measured 172×104dp — not 110×40dp. Both axes were wrong, on every style,
in every layout constant this session originally wrote before catching it. `WidgetSizeClass.kt`'s
thresholds are now calibrated against the real numbers; every dp figure below reflects that
correction. The `WIDE` width is the one number in this document that is *reasoned* (extrapolated
from the one real width measurement, ~86dp/column × 4) rather than measured — a real device could
not be gotten into a wide-resized state within this session's device-automation time budget
despite three genuine attempts (`docs/RESPONSIVE_WIDGET_REVIEW.md` has the full account). Treat
the `WIDE` column below as design intent confirmed by Robolectric (every style renders without
duplicating or losing content at a `WIDE_SIZE` `DpSize`) but not yet confirmed by a real launcher's
own rendering.

## Content-fit rules that apply across every cell below

Two rules apply uniformly, stated once here rather than repeated 21 times:

- **`COMPACT` never draws `WidgetHeadline.secondary`, a target date, a percentage readout, or any
  progress indicator**, regardless of style or of what the user's toggles ask for. A single row has
  room for one fact. The accessibility content description honors this too (`docs/WIDGET_DESIGN_REVIEW.md`) —
  a screen reader never announces something the layout didn't draw.
- **Content-fit type scaling** (`WidgetHeadline.contentFitScale`, `CountdownWidgetLayouts.kt`)
  applies inside every cell: a 4+ digit day count or an unusually long status word scales its
  style's base headline size down, so the numbers in the "Typography scale" column below are each
  style's *base* size — the actual rendered size for a specific countdown can be smaller for a
  longer number or word. Confirmed on a real device this session at both the "4-digit count" case
  (`docs/screenshots/responsive_4digit_modern.png`, a 1000-day event) and under 200% system font
  scale (`docs/screenshots/responsive_fontscale_200.png`), where the same mechanism plus
  `maxLines = 1` ellipsis kept every element inside its card with no overflow.

---

## Minimal

Typography-first at every size — the one style whose `WIDE` form deliberately stays one column
rather than adding a second (`docs/WIDGET_DESIGN_GUIDE.md`'s own reasoning: a second column would
itself be a second thing to look at).

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline only | — | Never | Centered | Identity, unit reduced to 40% headline size | 28sp |
| Standard | Headline | Secondary line, if any | Never (style-defining) | Centered | Progress bar (by design) | 46sp / 26sp word |
| Wide | Headline, larger | Secondary line, if any | Never | Centered, single column | Progress bar; second column | 58sp / 32sp word |

## Material

The "shows everything" default at every size — the only style whose `Compact` form keeps identity
*and* headline in the same row, and whose `Wide` form is the brief's literal "LEFT identity + date
/ RIGHT countdown" pattern.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline + unit, row-end | Identity, row-start (weighted, truncates) | Never | Split row | Secondary line, date, progress | 22sp / 16sp word |
| Standard | Headline + unit | Secondary, date | Linear bar + % | Left column | Nothing (shows everything) | 32sp / 22sp word |
| Wide | Headline + unit, right column | Identity + secondary + date, left column | Linear bar, right column | Two columns | Nothing | 40sp / 26sp word |

## Progress

The ring is the differentiator — and the one style whose `Compact` form has no differentiator at
all, on purpose: a ring below `MIN_RING_DP` isn't a progress indicator, it's a smudge.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline only (no ring) | — | **None — converges on Minimal's compact form** | Centered | Ring, identity, unit reduced | 28sp |
| Standard | Headline inside ring | Secondary line, below ring | **Circular ring** (~68dp, `RING_FRACTION_OF_CELL`) | Centered | Linear bar (ring replaces it) | 26sp (in-ring) |
| Wide | Headline as text, left | Secondary, left | **Circular ring, right column, no text inside it** (~86dp, larger than Standard's) | Two columns | Duplicate headline in the ring (D-054's fix) | 40sp / 26sp word (left) |

## OLED

No identity, ever, at any size — the style's entire premise. The largest type scale of any style,
at every size, since nothing else ever competes with the number for the card.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline only | — | Never | Centered | Identity (never shown, any size) | 30sp |
| Standard | Headline | Secondary line, if any | Never (style-defining) | Centered | Identity | 50sp / 28sp word |
| Wide | Headline, largest of any cell | Secondary line, if any | Never | Centered, single column | Identity; second column | 62sp / 34sp word |

## Glass

Normal type weight at every size — the one property that survives every size for this style, per
its own `docs/WIDGET_DESIGN_GUIDE.md` entry. Translucent background is a card-level property
(`WidgetTheme.backgroundColorArgb`), unaffected by size class.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline, row-end | Identity, row-start (weighted) | Never | Split row | Secondary, progress | 22sp / 16sp word, normal weight |
| Standard | Headline | Secondary line | Thin linear bar (4dp) | Centered | Nothing | 34sp / 22sp word, normal weight |
| Wide | Headline, right column | Identity, left column | Thin linear bar, right column | Two columns | Nothing | 42sp / 26sp word, normal weight |

## Rounded

The pill-chip is the differentiator, and it survives at every size — `Compact`'s single available
slot goes to the pill (secondary if present, else the unit) rather than dropping it, the one style
that treats its own signature element as non-negotiable even under the tightest size.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline | Secondary-or-unit, in pill | Never | Centered row | Identity | 22sp / 16sp word |
| Standard | Headline | Secondary, in pill | Linear bar | Centered | Nothing | 34sp / 22sp word |
| Wide | Headline, right column | Secondary, in pill, right column | Linear bar, right column | Two columns (identity left, everything else right) | Nothing | 42sp / 26sp word |

## Modern

The most information-dense style at `Standard`, and the one that benefits most from `Wide` — the
only style whose compact form drops identity entirely (it has the most to give up) in exchange for
flush-left alignment, the one alignment choice unique to this style at every size.

| | Primary | Secondary | Progress | Alignment | Hidden | Typography (base) |
|---|---|---|---|---|---|---|
| Compact | Headline + unit | — | Never | Flush-left row | Identity, secondary, date, percentage, progress | 22sp / 16sp word |
| Standard | Headline + unit | Secondary, date | Linear bar + % | Flush-left, top-anchored | Nothing (shows everything) | 30sp / 20sp word |
| Wide | Headline + unit, right column | Identity + secondary + date + %, left column | Linear bar, right column | Two columns, top-anchored | Nothing | 38sp / 24sp word |

---

## What this matrix does not claim

Every `Compact` and `Standard` row above is backed by a real on-device screenshot or a passing
Robolectric assertion this session (`docs/RESPONSIVE_WIDGET_REVIEW.md` has the specific evidence
per style). Every `Wide` row is backed by Robolectric only — real-device confirmation of the `WIDE`
size class specifically was attempted and not achieved this session; see that document's Final
Report for exactly what was tried and why it's stated as a gap rather than quietly assumed.
