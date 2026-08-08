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
    fun `styles with no forced background stay dynamic`() {
        listOf(WidgetStyle.MINIMAL, WidgetStyle.MATERIAL, WidgetStyle.PROGRESS, WidgetStyle.ROUNDED, WidgetStyle.MODERN)
            .forEach { style ->
                val theme = WidgetThemeResolver.resolve(style, AccentColor.Dynamic)
                assertThat(theme.backgroundColorArgb).isNull()
            }
    }

    @Test
    fun `rounded has a larger corner radius than the default`() {
        val rounded = WidgetThemeResolver.resolve(WidgetStyle.ROUNDED, AccentColor.Dynamic)
        val minimal = WidgetThemeResolver.resolve(WidgetStyle.MINIMAL, AccentColor.Dynamic)

        assertThat(rounded.cornerRadiusDp).isGreaterThan(minimal.cornerRadiusDp)
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
