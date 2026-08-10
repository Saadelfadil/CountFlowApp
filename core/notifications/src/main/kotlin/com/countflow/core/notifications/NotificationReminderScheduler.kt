package com.countflow.core.notifications

/**
 * The seam `CountFlowApplication` starts once, at process start.
 *
 * Mirrors `WidgetRefreshScheduler` (`:widget:engine`, Session 12) on purpose: same shape, same
 * "start once, react to everything through one coordinator" design — deliberately not the same
 * class, since widget redraw and reminder delivery are different outcomes with different
 * consumers (see DECISIONS.md D-067 for why the two systems share the *pattern*, not the code).
 */
interface NotificationReminderScheduler {
    fun start()
}
