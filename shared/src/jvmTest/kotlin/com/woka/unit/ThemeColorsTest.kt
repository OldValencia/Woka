package com.woka.unit

import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.Colors
import com.woka.core.ui.theme.DarkColors
import com.woka.core.ui.theme.LightColors
import com.woka.support.contrast
import com.woka.support.flatten
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The themed colours, measured rather than judged by eye (RD-101) — the same arithmetic as
 * `ViewerChromeTest`. What regresses here is a number: a secondary text darker than the
 * tertiary one, a hover nobody can see, the editor orange set as text where it cannot be read.
 */
class ThemeColorsTest {

    private val themes = listOf("light" to LightColors, "dark" to DarkColors)

    /** The two opaque grounds text is drawn on: the window and a card. */
    private fun grounds(colors: Colors): List<Pair<String, Color>> =
        listOf(
            "background" to colors.background,
            "surfaceElevated" to colors.surfaceElevated
        )

    @Test
    fun `text keeps its hierarchy on every ground`() {
        themes.forEach { (theme, colors) ->
            grounds(colors).forEach { (ground, color) ->
                val primary = contrast(colors.textPrimary, color)
                val secondary = contrast(colors.textSecondary, color)
                val tertiary = contrast(colors.textTertiary, color)
                assertTrue(primary > secondary, "$theme $ground: primary $primary vs secondary $secondary")
                assertTrue(secondary > tertiary, "$theme $ground: secondary $secondary vs tertiary $tertiary")
            }
        }
    }

    @Test
    fun `dark secondary text clears AA`() {
        grounds(DarkColors).forEach { (ground, color) ->
            val ratio = contrast(DarkColors.textSecondary, color)
            assertTrue(ratio >= 4.5, "dark textSecondary on $ground measured $ratio")
        }
    }

    @Test
    fun `dark hover and hairline are visible`() {
        assertTrue(DarkColors.surfaceHover.alpha >= 0.05f, "a hover nobody can see")
        assertTrue(DarkColors.border.alpha >= 0.06f, "a hairline nobody can see")
        val hovered = flatten(DarkColors.surfaceHover, DarkColors.background)
        val lift = contrast(hovered, DarkColors.background)
        assertTrue(lift >= 1.05, "dark hover lifts the background by only $lift")
    }

    @Test
    fun `the glowing border stays stronger than the plain one`() {
        themes.forEach { (theme, colors) ->
            assertTrue(colors.borderGlow.alpha > colors.border.alpha, "$theme borderGlow vs border")
        }
    }

    @Test
    fun `the editor orange as text is readable`() {
        val light = LightColors.accentEditorText
        val onCard = contrast(light, LightColors.surfaceElevated)
        val onBackground = contrast(light, LightColors.background)
        assertTrue(onCard >= 4.5, "light accentEditorText on surfaceElevated measured $onCard")
        // The one reader today is a glyph, which needs the non-text 3 : 1.
        assertTrue(onBackground >= 3.0, "light accentEditorText on background measured $onBackground")
        val dark = contrast(DarkColors.accentEditorText, DarkColors.surfaceElevated)
        assertTrue(dark >= 4.5, "dark accentEditorText on surfaceElevated measured $dark")
    }

    /** The fill keeps the editor's orange; only its text tone darkens in the light theme. */
    @Test
    fun `the editor fill is the same orange in both themes`() {
        assertTrue(LightColors.accentEditor == DarkColors.accentEditor, "accentEditor differs by theme")
    }
}
