package com.countflow.widget.engine.theme

import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.WidgetStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Every style resolves to a distinct, deterministic theme. */
class WidgetThemeResolverTest {

    @Test
    fun `a fixed accent is honoured regardless of style`() {
        // The user chose that colour for this event specifically; no theme should override it.
        val fixed = AccentColor.Fixed(0xFF00695C.toInt())

        WidgetStyle.entries.forEach { style ->
            val theme = WidgetThemeResolver.resolve(style, fixed)
            assertThat(theme.accentColorArgb).isEqualTo(0xFF00695C.toInt())
        }
    }

    @Test
    fun `a dynamic accent leaves the colour null for the renderer to resolve`() {
        WidgetStyle.entries.forEach { style ->
            val theme = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
            assertThat(theme.accentColorArgb).isNull()
        }
    }

    @Test
    fun `oled is forced true black regardless of accent`() {
        val theme = WidgetThemeResolver.resolve(WidgetStyle.OLED, AccentColor.Dynamic)

        assertThat(theme.backgroundColorArgb).isEqualTo(0xFF000000.toInt())
        assertThat(theme.isHighContrast).isTrue()
    }

    @Test
    fun `glass gets a translucent forced background`() {
        val theme = WidgetThemeResolver.resolve(WidgetStyle.GLASS, AccentColor.Dynamic)

        assertThat(theme.backgroundColorArgb).isNotNull()
        // Not true black — must be distinguishable from OLED.
        assertThat(theme.backgroundColorArgb).isNotEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `glass stays opaque enough for text to stay legible over a light wallpaper`() {
        // GLASS is the one style that composites over content this app does not control. Below
        // MIN_ALPHA_FOR_RELIABLE_CONTRAST, a white wallpaper behind the widget pulls the
        // effective background light enough that the white text drawn on top of it (see
        // CountdownWidgetContent's ForcedBackgroundPalette) drops below WCAG AA contrast — found
        // during Session 7's UX review, not by a failing test, which is exactly why this exists
        // now.
        val theme = WidgetThemeResolver.resolve(WidgetStyle.GLASS, AccentColor.Dynamic)
        val alpha = (theme.backgroundColorArgb!! ushr 24) and 0xFF

        assertThat(alpha).isAtLeast(WidgetThemeResolver.MIN_ALPHA_FOR_RELIABLE_CONTRAST)
    }

    @Test
    fun `styles with no forced background stay dynamic`() {
        listOf(WidgetStyle.MINIMAL, WidgetStyle.MATERIAL, WidgetStyle.PROGRESS, WidgetStyle.ROUNDED, WidgetStyle.MODERN)
            .forEach { style ->
                val theme = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
                assertThat(theme.backgroundColorArgb).isNull()
            }
    }

    @Test
    fun `styles with no reason to differ from the system radius leave it null`() {
        // D-045: most styles should look like whatever corner every other widget on the same
        // home screen uses, not assert a number of their own.
        listOf(WidgetStyle.MINIMAL, WidgetStyle.MATERIAL, WidgetStyle.PROGRESS, WidgetStyle.OLED)
            .forEach { style ->
                val theme = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
                assertThat(theme.cornerRadiusDp).isNull()
            }
    }

    @Test
    fun `rounded, glass, and modern each resolve a fixed radius distinct from one another`() {
        val rounded = WidgetThemeResolver.resolve(WidgetStyle.ROUNDED, AccentColor.Dynamic).cornerRadiusDp
        val glass = WidgetThemeResolver.resolve(WidgetStyle.GLASS, AccentColor.Dynamic).cornerRadiusDp
        val modern = WidgetThemeResolver.resolve(WidgetStyle.MODERN, AccentColor.Dynamic).cornerRadiusDp

        assertThat(rounded).isNotNull()
        assertThat(glass).isNotNull()
        assertThat(modern).isNotNull()
        assertThat(setOf(rounded, glass, modern)).hasSize(3)
        // Rounded's entire premise is being the roundest; Modern's is being crisper than system.
        assertThat(rounded!!).isGreaterThan(glass!!)
        assertThat(modern!!).isLessThan(glass)
    }

    @Test
    fun `every style resolves to itself in the theme`() {
        WidgetStyle.entries.forEach { style ->
            assertThat(WidgetThemeResolver.resolve(style, AccentColor.Dynamic).style).isEqualTo(style)
        }
    }

    @Test
    fun `resolving is deterministic`() {
        WidgetStyle.entries.forEach { style ->
            val first = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
            val second = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
            assertThat(first).isEqualTo(second)
        }
    }
}
