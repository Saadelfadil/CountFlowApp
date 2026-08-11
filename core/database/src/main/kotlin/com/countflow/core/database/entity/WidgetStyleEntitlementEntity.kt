package com.countflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.countflow.core.domain.model.WidgetStyle

/**
 * Storage row recording that a specific placed widget has unlocked one rewarded [WidgetStyle].
 *
 * The composite primary key is the entire model: a row's mere existence for
 * `(app_widget_id, style)` *is* the entitlement, so granting the same style twice is a harmless
 * upsert rather than a second row to reconcile, and there is no separate boolean or status column
 * that could disagree with whether the row is there.
 *
 * The foreign key targets `widget_bindings.app_widget_id`, not `events.id` — entitlements belong
 * to the placed widget, never the event (see [com.countflow.core.domain.repository.WidgetStyleEntitlementRepository]'s
 * own KDoc). `ON DELETE CASCADE` means removing a widget's binding — directly, or transitively
 * through the event it points at being deleted, which already cascades to the binding — also
 * removes every entitlement it held, with no extra application code: the same two-hop cascade
 * chain this database already relies on elsewhere (`reminders` → `events`).
 */
@Entity(
    tableName = "widget_style_entitlements",
    primaryKeys = ["app_widget_id", "style"],
    foreignKeys = [
        ForeignKey(
            entity = WidgetBindingEntity::class,
            parentColumns = ["app_widget_id"],
            childColumns = ["app_widget_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class WidgetStyleEntitlementEntity(
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int,

    @ColumnInfo(name = "style")
    val style: WidgetStyle,
)
