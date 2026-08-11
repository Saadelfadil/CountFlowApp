package com.countflow.core.data.repository

import com.countflow.core.common.di.CountFlowDispatcher
import com.countflow.core.common.di.Dispatcher
import com.countflow.core.database.dao.WidgetStyleEntitlementDao
import com.countflow.core.database.entity.WidgetStyleEntitlementEntity
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.WidgetStyleEntitlementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [WidgetStyleEntitlementRepository]. */
@Singleton
internal class WidgetStyleEntitlementRepositoryImpl @Inject constructor(
    private val dao: WidgetStyleEntitlementDao,
    @Dispatcher(CountFlowDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : WidgetStyleEntitlementRepository {

    override suspend fun isStyleUnlocked(appWidgetId: AppWidgetId, style: WidgetStyle): Boolean {
        // Free styles never touch the database at all — their unlocked state cannot depend on
        // whatever the entitlement table happens to contain.
        if (!style.isRewarded) return true
        return withContext(ioDispatcher) { dao.hasEntitlement(appWidgetId.value, style) }
    }

    override suspend fun grantRewardedStyle(appWidgetId: AppWidgetId, style: WidgetStyle) {
        require(style.isRewarded) {
            "Cannot grant a rewarded-style entitlement for $style — it is not a rewarded style."
        }
        withContext(ioDispatcher) {
            dao.grantEntitlement(WidgetStyleEntitlementEntity(appWidgetId.value, style))
        }
    }
}
