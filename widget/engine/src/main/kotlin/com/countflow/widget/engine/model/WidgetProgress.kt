package com.countflow.widget.engine.model

import com.countflow.core.domain.model.ProgressStyle

/**
 * The resolved progress state for one widget's render pass.
 *
 * [fraction] and [percent] are the same underlying number in two shapes a renderer needs —
 * `0f..1f` for a progress bar's `progress` parameter, `0..100` for display and for a bitmap
 * cache key. Neither the fraction nor the percentage says anything about *how* to draw it; that
 * is [style]'s job, and the two circular- versus linear-rendering paths (the latter arrives in
 * Milestone 5, per KNOWN_ISSUES.md LIM-001) both read the same numbers.
 *
 * @property style which representation the widget should draw, or [ProgressStyle.NONE] to draw
 *   none at all.
 * @property fraction completion from the event's creation to its target, `0f..1f`.
 * @property percent the same value as a whole number.
 * @property percentText [percent] formatted for display, for example `"42%"`. Safe to compute
 *   here without Android resources — unlike a countdown label, a percentage has no plural forms
 *   and no translation.
 * @property isVisible whether a progress indicator should be drawn at all. False exactly when
 *   [style] is [ProgressStyle.NONE]; kept as its own field so a renderer branches on one boolean
 *   rather than repeating the enum comparison.
 */
data class WidgetProgress(
    val style: ProgressStyle,
    val fraction: Float,
    val percent: Int,
    val percentText: String,
    val isVisible: Boolean,
)
