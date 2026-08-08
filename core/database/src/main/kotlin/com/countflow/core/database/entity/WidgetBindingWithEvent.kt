package com.countflow.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A widget binding joined to the event it points at.
 *
 * [event] is non-null in practice: the foreign key cascade deletes the binding when its event
 * goes, so a binding without an event cannot persist. Room types the relation as nullable
 * regardless, and the mapper treats a null as "skip this row" rather than asserting — a crash
 * in a widget's render path is far worse than one missing widget.
 */
data class WidgetBindingWithEvent(
    @Embedded
    val binding: WidgetBindingEntity,

    @Relation(parentColumn = "event_id", entityColumn = "id")
    val event: EventEntity?,
)
