package com.countflow.widget.engine.model

import com.countflow.core.domain.model.WidgetStyle

/**
 * The resolved visual treatment for one widget's render pass.
 *
 * Produced by [com.countflow.widget.engine.theme.WidgetThemeResolver] and consumed as-is by the
 * Glance layer — the renderer applies these values, it does not decide them. That split is what
 * lets a new widget style be added in one place (the resolver, plus its test) without touching
 * any rendering code.
 *
 * Colours are nullable ARGB integers, not a Compose or Glance `Color`: this module has no
 * Android dependency, and packed ARGB is how the domain already represents a fixed colour
 * ([com.countflow.core.domain.model.AccentColor.Fixed]). Null means "derive it from the dynamic
 * Material You palette at render time" — the same null-means-dynamic convention used throughout
 * the app, so a renderer never has to guess which kind of absence it is looking at.
 *
 * @property style which named theme this is, kept alongside the resolved values so a renderer
 *   can special-case something the resolver does not express as a colour or radius — a layout
 *   difference for [WidgetStyle.PROGRESS], for instance.
 * @property accentColorArgb a fixed accent, or null to use the dynamic Material You primary.
 * @property backgroundColorArgb a fixed background, or null to use the dynamic surface colour.
 *   Forced to true black for [WidgetStyle.OLED], which is the entire point of that theme —
 *   burn-in prevention on an always-on display, not an aesthetic choice a dynamic colour could
 *   satisfy.
 * @property cornerRadiusDp the corner radius to apply to the widget's background.
 * @property isHighContrast whether text and dividers should use a stronger contrast pass.
 */
data class WidgetTheme(
    val style: WidgetStyle,
    val accentColorArgb: Int?,
    val backgroundColorArgb: Int?,
    val cornerRadiusDp: Int,
    val isHighContrast: Boolean,
)
