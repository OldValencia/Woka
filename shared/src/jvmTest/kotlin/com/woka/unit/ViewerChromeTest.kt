package com.woka.unit

import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.ViewerChrome
import com.woka.support.contrast
import com.woka.support.flatten
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

    private val scrims =
        listOf(
            "hudScrim" to ViewerChrome.hudScrim,
            "popoverScrim" to ViewerChrome.popoverScrim,
            "tooltipScrim" to ViewerChrome.tooltipScrim
        )

    @Test
    fun `primary text clears AA on every scrim this app draws`() {
        scrims.forEach { (name, scrim) ->
            val ratio = contrast(ViewerChrome.onScrim, flatten(scrim, MID))
            assertTrue(ratio >= 4.5, "onScrim over $name measured $ratio")
        }
    }

    @Test
    fun `secondary text clears large-text AA on every scrim`() {
        scrims.forEach { (name, scrim) ->
            val ratio = contrast(ViewerChrome.onScrimSecondary, flatten(scrim, MID))
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
        val primary = contrast(ViewerChrome.onScrim, flatten(scrim, MID))
        val secondary = contrast(ViewerChrome.onScrimSecondary, flatten(scrim, MID))
        val disabled = contrast(ViewerChrome.onScrimDisabled, flatten(scrim, MID))

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
