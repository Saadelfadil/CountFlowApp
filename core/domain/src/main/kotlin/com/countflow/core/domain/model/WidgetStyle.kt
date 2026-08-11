package com.countflow.core.domain.model

/**
 * The visual treatment a widget uses.
 *
 * Lives in the domain rather than the widget layer because it is persisted with an event (as a
 * default) and with a widget binding (as an override), and the data layer must be able to map it
 * without depending on Glance.
 *
 * @property isPremium whether this style requires a premium (subscription/billing) entitlement.
 *   Premium is not implemented yet — the flag exists so gating can be written and tested before
 *   billing lands, per DECISIONS.md (D-009). Deliberately a separate axis from [isRewarded]: the
 *   two model different products (a subscription vs. a per-widget rewarded-ad unlock) that happen
 *   to gate styles, not one shared "paid" concept — see [isRewarded]'s own KDoc.
 * @property isRewarded whether this style requires a per-widget rewarded-ad entitlement (see
 *   [com.countflow.core.domain.repository.WidgetStyleEntitlementRepository]) before a widget may
 *   render with it. Independent of [isPremium] on purpose: Billing/subscriptions are still
 *   entirely unimplemented and out of scope wherever this flag is used, and conflating the two
 *   would mean a future billing feature and this rewarded-style feature fighting over the same
 *   bit for two different unlock mechanisms.
 */
enum class WidgetStyle(val isPremium: Boolean, val isRewarded: Boolean) {
    /** Type only, no chrome. The default. */
    MINIMAL(isPremium = false, isRewarded = false),

    /** Material 3 surface with dynamic colour. */
    MATERIAL(isPremium = false, isRewarded = false),

    /** Translucent, blurred-looking background. */
    GLASS(isPremium = true, isRewarded = true),

    /** True black, for OLED always-on displays. */
    OLED(isPremium = false, isRewarded = false),

    /**
     * Progress-led: the bar or ring is the primary element.
     *
     * No longer offered as a choice — [selectable] excludes it. A style and its progress
     * visualization are now independent settings (any [selectable] style combines with any
     * [ProgressStyle]), which made a dedicated "this style's whole identity is its progress
     * graphic" style redundant with just picking [ProgressStyle.CIRCULAR]. Kept as a real enum
     * constant, not deleted, purely so a `WidgetStyle` persisted before this change (Room stores
     * enums by name, never by ordinal — see `Converters.kt`) still resolves to a real value
     * instead of an unknown-name fallback; [com.countflow.widget.engine.mapper.WidgetRenderMapper]
     * is the one place that reinterprets it for rendering. Not rewarded — it is not selectable at
     * all, so there is nothing for an entitlement to gate.
     */
    PROGRESS(isPremium = false, isRewarded = false),

    /** Heavily rounded card. */
    ROUNDED(isPremium = false, isRewarded = true),

    /** High-contrast editorial layout. */
    MODERN(isPremium = true, isRewarded = true),
    ;

    companion object {
        /** The style applied when the user does not choose one. */
        val Default: WidgetStyle = MINIMAL

        /**
         * Styles a user can actually choose. Excludes [PROGRESS] — see its own KDoc. The
         * configuration screen's Style picker iterates this, never [entries] directly, so
         * [PROGRESS] can never be newly selected again while still resolving safely wherever it
         * was chosen before this list existed.
         */
        val selectable: List<WidgetStyle> get() = entries.filter { it != PROGRESS }

        /** Styles available without a premium entitlement. */
        val free: List<WidgetStyle> get() = entries.filter { !it.isPremium }

        /** Styles that require a per-widget rewarded-ad entitlement — see [isRewarded]. */
        val rewarded: List<WidgetStyle> get() = entries.filter { it.isRewarded }
    }
}

/**
 * How completion progress is drawn, if at all.
 *
 * [CIRCULAR] is significant for implementation: Glance has no determinate circular progress
 * indicator, so it must be rendered as a Canvas-drawn bitmap. See KNOWN_ISSUES.md (LIM-001).
 */
enum class ProgressStyle {
    /** No progress indicator. */
    NONE,

    /** A horizontal determinate bar. */
    LINEAR,

    /** A ring around or behind the countdown value. */
    CIRCULAR,
    ;

    companion object {
        /** The progress style applied when the user does not choose one. */
        val Default: ProgressStyle = LINEAR
    }
}
