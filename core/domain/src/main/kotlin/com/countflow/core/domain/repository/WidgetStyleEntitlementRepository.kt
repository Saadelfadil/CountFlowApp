package com.countflow.core.domain.repository

import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.WidgetStyle

/**
 * Whether a specific placed widget has unlocked a rewarded [WidgetStyle], and the one place that
 * unlock is ever granted.
 *
 * Deliberately widget-scoped, not event-scoped: the same "the widget, not the event, owns its own
 * appearance" boundary [WidgetBinding][com.countflow.core.domain.model.WidgetBinding] already
 * draws for Style/Progress/Accent (DECISIONS.md D-013) applies here too — unlocking a rewarded
 * style for one widget must never unlock it for another widget, even one showing the same event.
 *
 * A future rewarded-ad provider (AdMob or otherwise) is never the source of truth for
 * entitlements; it is only ever a caller of [grantRewardedStyle], once its own reward has
 * genuinely completed. Nothing in this interface, or anything that implements it, may depend on
 * an ads SDK.
 */
interface WidgetStyleEntitlementRepository {

    /**
     * Whether [appWidgetId] may render with [style] right now.
     *
     * Every style with [WidgetStyle.isRewarded] false is always unlocked, unconditionally, with
     * no persistence lookup at all — only a rewarded style can ever be locked, and free styles
     * must never appear locked no matter what state the entitlement store is in.
     */
    suspend fun isStyleUnlocked(appWidgetId: AppWidgetId, style: WidgetStyle): Boolean

    /**
     * Grants [appWidgetId] a persistent, per-widget unlock of [style] — the one operation a
     * future rewarded-ad completion callback needs to call, and the only way an entitlement is
     * ever created. No expiration: once granted, an entitlement holds until the widget itself is
     * removed.
     *
     * @throws IllegalArgumentException if [style] is not a rewarded style ([WidgetStyle.isRewarded]
     *   false) — a style nothing ever locks has no entitlement to grant, and accepting one anyway
     *   would let a caller mistakenly create an entitlement record for an enum value the rest of
     *   this system can never actually ask about.
     */
    suspend fun grantRewardedStyle(appWidgetId: AppWidgetId, style: WidgetStyle)
}
