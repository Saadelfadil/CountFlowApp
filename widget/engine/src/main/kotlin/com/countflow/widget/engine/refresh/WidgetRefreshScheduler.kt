package com.countflow.widget.engine.refresh

/**
 * Keeps widgets redrawn as time passes and data changes.
 *
 * This interface is a seam, not a schedule. The real strategy — a launcher-ticked `Chronometer`
 * for the final 24 hours plus one coalesced `AlarmManager` alarm for the whole app, replacing
 * `updatePeriodMillis` entirely — is Milestone 8's work (ARCHITECTURE.md D-008; TODO.md).
 *
 * Milestone 4's implementation, bound in `:widget:glance`, is deliberately simpler: it keeps
 * widgets current only while the app process is alive, by observing bound events and redrawing
 * on change. That covers the success criterion this session cares about — editing an event
 * updates its widgets — without building the alarm infrastructure a background-refresh strategy
 * needs. See DECISIONS.md for why that gap is acceptable for now: Room is always the source of
 * truth (D-002), so a widget is never wrong, only stale until the next trigger, and closing that
 * staleness window is exactly what Milestone 8 exists to do.
 */
interface WidgetRefreshScheduler {

    /**
     * Begins whatever this implementation does to keep widgets current. Idempotent — safe to
     * call once at application startup and never again.
     */
    fun start()
}
