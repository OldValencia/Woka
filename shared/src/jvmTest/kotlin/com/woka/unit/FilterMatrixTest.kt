package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.feature.editor.tools.filter.filterMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The eight looks as arithmetic (ME-112).
 *
 * **Intensity lerps the matrix, not two renders.** That is what keeps a look as cheap as
 * an adjustment — one matrix, rebuilt when the number moves — and it is asserted on a
 * coefficient rather than on the whole array, because "halfway" is a property of each
 * number and not of the array's identity.
 */
class FilterMatrixTest {

    private fun ColorMatrix.channel(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun row(at: Int) =
            values[at] * r + values[at + 1] * g + values[at + 2] * b + values[at + 4]
        return Triple(row(0), row(5), row(10))
    }

    @Test
    fun `Original is the absence of a look at any intensity`() {
        listOf(0f, 40f, 100f).forEach { intensity ->
            assertNull(
                filterMatrix(PhotoFilter.Original, intensity),
                "Original composed something at $intensity"
            )
        }
    }

    @Test
    fun `zero intensity composes nothing for every filter`() {
        PhotoFilter.entries.forEach { filter ->
            assertNull(filterMatrix(filter, 0f), "$filter composed something at zero")
        }
    }

    @Test
    fun `every other filter does something at full strength`() {
        PhotoFilter.entries.filter { it != PhotoFilter.Original }.forEach { filter ->
            assertNotNull(filterMatrix(filter, 100f), "$filter did nothing at full strength")
        }
    }

    /** Halfway is halfway: each coefficient sits between the identity and the look. */
    @Test
    fun `intensity lerps toward the identity`() {
        val full = assertNotNull(filterMatrix(PhotoFilter.Vivid, 100f)).values
        val half = assertNotNull(filterMatrix(PhotoFilter.Vivid, 50f)).values
        val identity = ColorMatrix().values

        full.indices.forEach { at ->
            val wanted = identity[at] + (full[at] - identity[at]) * 0.5f
            assertTrue(
                abs(wanted - half[at]) < 0.001f,
                "coefficient $at: wanted $wanted, got ${half[at]}"
            )
        }
    }

    /** A monochrome look answers the same number on all three channels. */
    @Test
    fun `Mono and Noir turn a colour grey`() {
        listOf(PhotoFilter.Mono, PhotoFilter.Noir).forEach { filter ->
            val (r, g, b) = assertNotNull(filterMatrix(filter, 100f)).channel(200f, 100f, 50f)

            assertTrue(abs(r - g) < 0.5f && abs(g - b) < 0.5f, "$filter left colour in")
        }
    }

    /** Silvertone is monochrome with a cast: equal luminance, unequal channels. */
    @Test
    fun `Silvertone is toned rather than neutral`() {
        val (r, _, b) = assertNotNull(filterMatrix(PhotoFilter.Silvertone, 100f))
            .channel(150f, 150f, 150f)

        assertTrue(abs(r - b) > 1f, "Silvertone came out neutral")
    }

    /** Warm and Cool move the same axis in opposite directions. */
    @Test
    fun `Warm and Cool disagree about red and blue`() {
        val (warmR, _, warmB) = assertNotNull(filterMatrix(PhotoFilter.Warm, 100f))
            .channel(120f, 120f, 120f)
        val (coolR, _, coolB) = assertNotNull(filterMatrix(PhotoFilter.Cool, 100f))
            .channel(120f, 120f, 120f)

        assertTrue(warmR > warmB, "Warm was not warm")
        assertTrue(coolB > coolR, "Cool was not cool")
    }

    /** The full-strength matrix is the constant itself, not a blend of it. */
    @Test
    fun `full strength is the look's own matrix`() {
        val constant = assertNotNull(filterMatrix(PhotoFilter.Dramatic))
        val atFull = assertNotNull(filterMatrix(PhotoFilter.Dramatic, 100f))

        assertContentEquals(constant.values, atFull.values)
    }
}
