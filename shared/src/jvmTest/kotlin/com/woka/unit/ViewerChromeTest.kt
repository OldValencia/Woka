package com.woka.unit

import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.ViewerChrome
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** A mid-grey stand-in for "an average photograph", which is what a scrim lands on. */
private val MID = Color(0xFF808080)

/**
 * The on-scrim foregrounds, measured rather than asserted by eye (ME-121).
 *
 * **Why arithmetic and not a UI test.** `Theme.viewer` is a plain getter returning the
 * `ViewerChrome` object, so "the same colour under both themes" is true by construction
 * and a rendered assertion would be testing the compiler. What can actually regress is a
 * token being dimmed — and that is a number, so this is a number.
 *
 * The ratios in `ViewerChrome`'s own KDoc assume a particular backdrop; these assert the
 * thresholds and the ordering instead, which survive a change of assumption.
 */
class ViewerChromeTest {

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    /** [over] composited under [color]'s own alpha — what a scrim does to the picture. */
    private fun flatten(color: Color, over: Color): Color {
        val a = color.alpha
        return Color(
            red = color.red * a + over.red * (1 - a),
            green = color.green * a + over.green * (1 - a),
            blue = color.blue * a + over.blue * (1 - a)
        )
    }

    private fun contrast(foreground: Color, scrim: Color): Double {
        val background = flatten(scrim, MID)
        val a = luminance(foreground) + 0.05
        val b = luminance(background) + 0.05
        return if (a > b) a / b else b / a
    }

    private val scrims =
        listOf(
            "hudScrim" to ViewerChrome.hudScrim,
            "popoverScrim" to ViewerChrome.popoverScrim,
            "tooltipScrim" to ViewerChrome.tooltipScrim
        )

    @Test
    fun `primary text clears AA on every scrim this app draws`() {
        scrims.forEach { (name, scrim) ->
            val ratio = contrast(ViewerChrome.onScrim, scrim)
            assertTrue(ratio >= 4.5, "onScrim over $name measured $ratio")
        }
    }

    @Test
    fun `secondary text clears large-text AA on every scrim`() {
        scrims.forEach { (name, scrim) ->
            val ratio = contrast(ViewerChrome.onScrimSecondary, scrim)
            assertTrue(ratio >= 3.0, "onScrimSecondary over $name measured $ratio")
        }
    }

    /**
     * Disabled is *meant* to be dim — the point of the assertion is that it stays below
     * secondary, so a later edit cannot quietly make "unavailable" look available.
     */
    @Test
    fun `the three foregrounds stay in their order`() {
        val scrim = ViewerChrome.hudScrim
        val primary = contrast(ViewerChrome.onScrim, scrim)
        val secondary = contrast(ViewerChrome.onScrimSecondary, scrim)
        val disabled = contrast(ViewerChrome.onScrimDisabled, scrim)

        assertTrue(primary > secondary, "primary $primary did not beat secondary $secondary")
        assertTrue(secondary > disabled, "secondary $secondary did not beat disabled $disabled")
    }

    /**
     * The fills are the other half of the pair and must stay fills: visible as a state,
     * never mistakable for a foreground.
     */
    @Test
    fun `hover is visible and selected is stronger than hover`() {
        assertTrue(ViewerChrome.onScrimHover.alpha > 0.05f, "a hover nobody can see")
        assertTrue(
            ViewerChrome.onScrimSelected.alpha > ViewerChrome.onScrimHover.alpha,
            "selected must read stronger than hover"
        )
    }
}
