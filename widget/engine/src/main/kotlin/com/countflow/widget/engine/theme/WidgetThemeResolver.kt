package com.countflow.widget.engine.theme

import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetTheme

/**
 * Decides what each named [WidgetStyle] looks like.
 *
 * The one place theme logic lives. A Glance composable reads the [WidgetTheme] this produces and
 * applies it; it never switches on [WidgetStyle] itself. That split is the whole point of a
 * theme *resolver* rather than a `when` block scattered across the render tree — adding an
 * eighth style is a change in one function plus its test, not a hunt through every composable
 * that might care what style is active.
 *
 * A pure function of its two inputs, so it is an `object` rather than an injectable class —
 * there is no per-instance state a test would need to fake around.
 */
internal object WidgetThemeResolver {

    /**
     * Resolves the theme for [style] with [accentColor] applied.
     *
     * @param style which named theme to resolve.
     * @param accentColor the event's accent — [AccentColor.Dynamic] leaves the accent for the
     *   renderer to take from the Material You palette; [AccentColor.Fixed] is honoured
     *   regardless of style, because the user chose that colour for this specific event and a
     *   theme should not override a deliberate choice.
     */
    fun resolve(style: WidgetStyle, accentColor: AccentColor): WidgetTheme {
        val accentArgb = (accentColor as? AccentColor.Fixed)?.argb

        return when (style) {
            WidgetStyle.MINIMAL -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                backgroundColorArgb = null,
                cornerRadiusDp = CORNER_RADIUS_DEFAULT,
                isHighContrast = false,
            )

            WidgetStyle.MATERIAL -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                backgroundColorArgb = null,
                cornerRadiusDp = CORNER_RADIUS_DEFAULT,
                isHighContrast = false,
            )

            WidgetStyle.GLASS -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                // RemoteViews cannot blur what is behind the widget, so "glass" is approximated
                // with a translucent dark surface rather than a true frosted effect. Revisit if
                // a platform API ever exposes real backdrop blur to widgets.
                backgroundColorArgb = TRANSLUCENT_DARK_SURFACE,
                cornerRadiusDp = CORNER_RADIUS_GLASS,
                isHighContrast = false,
            )

            WidgetStyle.OLED -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                // Forced true black, never dynamic — an OLED theme's entire purpose is burning
                // the fewest possible sub-pixels, which a Material You surface tone cannot
                // guarantee even in dark mode.
                backgroundColorArgb = TRUE_BLACK,
                cornerRadiusDp = CORNER_RADIUS_DEFAULT,
                isHighContrast = true,
            )

            WidgetStyle.PROGRESS -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                backgroundColorArgb = null,
                cornerRadiusDp = CORNER_RADIUS_DEFAULT,
                isHighContrast = false,
            )

            WidgetStyle.ROUNDED -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                backgroundColorArgb = null,
                cornerRadiusDp = CORNER_RADIUS_ROUNDED,
                isHighContrast = false,
            )

            WidgetStyle.MODERN -> WidgetTheme(
                style = style,
                accentColorArgb = accentArgb,
                backgroundColorArgb = null,
                cornerRadiusDp = CORNER_RADIUS_DEFAULT,
                isHighContrast = true,
            )
        }
    }

    private const val CORNER_RADIUS_DEFAULT = 16
    private const val CORNER_RADIUS_GLASS = 20
    private const val CORNER_RADIUS_ROUNDED = 28

    private const val TRUE_BLACK = 0xFF000000.toInt()

    // Alpha chosen for a worst case the design can't see coming: unlike every other style, GLASS
    // renders over whatever wallpaper is behind the widget, not a color this app controls. At the
    // 0x99 (60%) alpha this constant originally shipped with, a fully white wallpaper composites
    // to roughly mid-gray — measured at ~4.9:1 contrast against the white text
    // ForcedBackgroundPalette.onSurface draws, only barely above WCAG AA's 4.5:1 floor for normal
    // text. 0xCC (80%) composites the same worst case to a near-black gray instead, comfortably
    // above 10:1. MIN_ALPHA_FOR_RELIABLE_CONTRAST documents the floor this reasoning depends on,
    // so a future change to this constant doesn't silently drift back below it.
    private const val TRANSLUCENT_DARK_SURFACE = 0xCC101418.toInt()
    const val MIN_ALPHA_FOR_RELIABLE_CONTRAST = 0xCC
}
