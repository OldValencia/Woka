package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.tools.filter.PhotoUndertone
import com.woka.feature.editor.tools.filter.undertoneMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The undertone half of Styles (ME-141), asserted the way `FilterMatrixTest` asserts
 * a look — because they are the same kind of thing and are tuned against each other.
 *
 * Nothing here renders: an undertone is a `ColorMatrix` and a matrix is twenty
 * numbers, so the claims are arithmetic and belong in `unit/`.
 */
class UndertoneMatrixTest {

    private val toned = PhotoUndertone.entries.filter { it != PhotoUndertone.Neutral }

    @Test
    fun `Neutral is the absence of an undertone at any palette`() {
        // Null rather than the identity, so a picture with no undertone contributes
        // nothing to the product at all — the rule every tool follows.
        listOf(0f, 50f, 100f).forEach { palette ->
            assertNull(undertoneMatrix(PhotoUndertone.Neutral, palette), "Neutral at $palette")
        }
    }

    @Test
    fun `every other undertone changes the picture at full palette`() {
        toned.forEach { undertone ->
            val matrix = assertNotNull(undertoneMatrix(undertone, 100f), "$undertone answered null")
            assertTrue(
                !matrix.values.contentEquals(ColorMatrix().values),
                "$undertone is the identity, so it is not an undertone"
            )
        }
    }

    @Test
    fun `a palette of zero is no undertone`() {
        toned.forEach { undertone ->
            assertNull(undertoneMatrix(undertone, 0f), "$undertone at 0 %")
        }
    }

    @Test
    fun `half a palette sits exactly halfway to the identity`() {
        val identity = ColorMatrix().values
        toned.forEach { undertone ->
            val full = assertNotNull(undertoneMatrix(undertone, 100f)).values
            val half = assertNotNull(undertoneMatrix(undertone, 50f)).values
            full.indices.forEach { at ->
                assertEquals(
                    identity[at] + (full[at] - identity[at]) / 2f,
                    half[at],
                    0.0001f,
                    "$undertone entry $at is not the midpoint"
                )
            }
        }
    }

    @Test
    fun `a palette past the ends is clamped rather than extrapolated`() {
        val full = assertNotNull(undertoneMatrix(PhotoUndertone.Gold, 100f)).values
        val over = assertNotNull(undertoneMatrix(PhotoUndertone.Gold, 400f)).values
        assertTrue(full.contentEquals(over), "400 % went past the undertone rather than stopping at it")
        assertNull(undertoneMatrix(PhotoUndertone.Gold, -50f), "a negative palette inverted the tone")
    }
}
