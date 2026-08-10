package com.countflow.core.notifications

import java.time.Instant

/** What one [ReminderNotificationCoordinator] cycle did, for logging. */
data class ReminderCycleOutcome(
    val remindersDelivered: Int,
    val nextReminderAt: Instant?,
)
