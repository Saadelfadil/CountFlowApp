package com.countflow.core.notifications

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.Reminder

/** Delivers exactly one reminder notification. The Android-specific half of a delivery. */
interface NotificationSender {
    suspend fun send(reminder: Reminder, event: Event)
}
