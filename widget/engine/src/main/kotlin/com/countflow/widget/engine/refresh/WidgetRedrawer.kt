package com.countflow.widget.engine.refresh

/**
 * The Glance-specific half of background refresh: actually redrawing every placed widget.
 *
 * The seam that keeps `CountdownGlanceWidget`/`updateAll` out of this module (D-033) —
 * implemented in `:widget:glance` as a one-line wrapper. [WidgetRefreshCoordinator] redraws
 * unconditionally rather than tracking per-widget dirty state: a render pass costs about half a
 * microsecond of CPU (`docs/WIDGET_ARCHITECTURE.md` §3), so "redraw everything, every refresh
 * cycle" is already cheaper than the bookkeeping an exactly-which-widgets-changed check would
 * cost.
 */
interface WidgetRedrawer {

    /** Redraws every currently placed widget instance. */
    suspend fun redrawAll()
}
