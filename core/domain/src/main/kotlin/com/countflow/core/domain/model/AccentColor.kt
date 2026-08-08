package com.countflow.core.domain.model

/**
 * The accent applied to an event and the widgets showing it.
 *
 * Modelled as a sealed type rather than a nullable colour integer so that "follow the system
 * wallpaper" is a first-class, explicitly stored choice rather than the absence of one. A null
 * colour would be ambiguous: it could mean "not set yet" or "deliberately dynamic", and those
 * behave differently when Material You is disabled in Settings.
 */
sealed interface AccentColor {

    /** Derive the accent from the Material You wallpaper palette. */
    data object Dynamic : AccentColor

    /**
     * Use a specific colour.
     *
     * @property argb packed 0xAARRGGBB. Stored as an `Int` rather than a Compose `Color` so the
     *   domain stays free of UI types.
     */
    data class Fixed(val argb: Int) : AccentColor

    companion object {
        /** The accent applied when the user does not choose one. */
        val Default: AccentColor = Dynamic
    }
}
