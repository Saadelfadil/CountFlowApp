package com.countflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.countflow.core.database.entity.WidgetStyleEntitlementEntity
import com.countflow.core.domain.model.WidgetStyle

/** Queries over the per-widget rewarded-style entitlement table. */
@Dao
interface WidgetStyleEntitlementDao {

    @Query(
        "SELECT EXISTS(SELECT 1 FROM widget_style_entitlements WHERE app_widget_id = :appWidgetId AND style = :style)",
    )
    suspend fun hasEntitlement(appWidgetId: Int, style: WidgetStyle): Boolean

    /** Upsert, not insert: granting an already-granted style is a harmless no-op, not an error. */
    @Upsert
    suspend fun grantEntitlement(entitlement: WidgetStyleEntitlementEntity)
}
