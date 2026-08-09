package com.countflow.widget.glance

/**
 * Which of the three supported footprints a widget is currently rendering at.
 *
 * Not tied to [androidx.glance.LocalSize] or any Android type on purpose: [CountdownGlanceWidget]
 * classifies a live [androidx.compose.ui.unit.DpSize] every recomposition (`SizeMode.Exact` reports
 * the widget's exact current size, not a snapped breakpoint — D-053), while
 * `configuration.WidgetConfigurationActivity` classifies a plain `AppWidgetManager` options
 * `Bundle` outside any composition at all. One function on two plain [Float] dp values serves both
 * without either caller depending on the other's framework.
 *
 * @property COMPACT the 2×1 footprint — one short row. Glanceability only: a single fact, not a
 *   dashboard.
 * @property STANDARD the 2×2 footprint. The Session 9 redesign's target size, unchanged in intent.
 * @property WIDE the 4×2 footprint — same height budget as [STANDARD], double the width — a
 *   second column of room, not stretched text.
 */
internal enum class WidgetSizeClass { COMPACT, STANDARD, WIDE }

/**
 * Classifies a widget's current size from its raw dp dimensions.
 *
 * Height is decided first: a widget short enough to be [WidgetSizeClass.COMPACT] gets that
 * treatment regardless of width, since a single-row layout is the safer fallback for limited
 * vertical space than a two-column one built for [WidgetSizeClass.STANDARD]'s full height.
 *
 * ### Where the thresholds actually come from — corrected on a real device, Session 10
 *
 * The first version of this function set [COMPACT_MAX_HEIGHT_DP]/[WIDE_MIN_WIDTH_DP] from
 * Android's documented cell-size formula (`dp = 70×cells − 30`, the same formula BUG-R009 exists
 * to remind every session to verify against a real device rather than trust). That was itself a
 * BUG-R009-shaped mistake: the formula describes the `minWidth`/`minHeight` XML *declaration*
 * convention, not what a real launcher's grid actually measures in dp at render time. Confirmed
 * directly on a real device this session — `CountdownWidgetContent` briefly logged its resolved
 * [androidx.glance.LocalSize] into the widget's own content description during this
 * investigation — a real, default-placed 2×2 CountFlow widget on this session's Pixel Launcher
 * measured **172×224dp**, not the formula's 110×110dp; the same widget resized down by one grid
 * row (confirmed "2×1" by both the launcher's own resize-handle affordance and
 * `AppWidgetManager`'s options bundle) measured **172×104dp**, not 110×40dp. The formula's numbers
 * were never close on either axis. [COMPACT_MAX_HEIGHT_DP] is now the midpoint of the two real
 * measured heights (104 and 224 → 164dp). No real 4×2 measurement was obtained this session
 * (getting a real device into a wider-than-2-column resize state was attempted and did not
 * succeed within this session's device-automation time budget — see
 * `docs/RESPONSIVE_WIDGET_REVIEW.md`); [WIDE_MIN_WIDTH_DP] is instead reasoned from the one real
 * width measurement that does exist (172dp for 2 columns, ⇒ ~86dp/column) extrapolated to 4
 * columns and given a conservative margin for shared gutters not doubling — stated here as
 * reasoning, not measurement, so a future session knows which of these two constants still needs
 * real confirmation.
 *
 * Both thresholds sit at a midpoint rather than at a measured value itself, for the same reason
 * the original (wrong) formula-based version did: a real host rarely reproduces an exact number
 * (rounding, density, per-launcher grid quirks all nudge it), so snapping to "nearest supported
 * footprint" is more robust than an equality check would be.
 */
internal fun classifyWidgetSize(widthDp: Float, heightDp: Float): WidgetSizeClass = when {
    heightDp < COMPACT_MAX_HEIGHT_DP -> WidgetSizeClass.COMPACT
    widthDp >= WIDE_MIN_WIDTH_DP -> WidgetSizeClass.WIDE
    else -> WidgetSizeClass.STANDARD
}

/** Midpoint of two real on-device measurements this session: a real 2×1 resize at 104dp tall, a real 2×2 default placement at 224dp tall. */
internal const val COMPACT_MAX_HEIGHT_DP = 164f

/** Reasoned, not measured (see the class doc above): ~86dp/column × 4 columns, with a margin below the naive ×2 of the real 172dp 2-column width for shared gutters. */
internal const val WIDE_MIN_WIDTH_DP = 300f
