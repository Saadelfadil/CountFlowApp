package com.countflow.core.data.mapper

import com.countflow.core.database.entity.ReminderEntity
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId

internal fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = ReminderId(id),
    eventId = EventId(eventId),
    type = type,
    timeOfDay = timeOfDay,
    isEnabled = isEnabled,
    deliveredForScheduledTime = deliveredForScheduledTime,
)

internal fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id.value,
    eventId = eventId.value,
    type = type,
    timeOfDay = timeOfDay,
    isEnabled = isEnabled,
    deliveredForScheduledTime = deliveredForScheduledTime,
)
