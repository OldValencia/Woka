package com.woka.unit

import com.woka.feature.editor.recipe.MatroskaRecipe
import com.woka.feature.editor.recipe.TAIL_WINDOW
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

/**
 * ME-282's byte contract over hand-built EBML — the corpus header's shape: an EBML header
 * of a one-byte size, then a `Segment` with a fixed-width 8-byte size that ends at EOF.
 */
class MatroskaRecipeTest {

    private val ebmlHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x83.toByte(), 0x42, 0x86.toByte(), 0x81.toByte())

    private fun segment(body: ByteArray, unknown: Boolean = false): ByteArray {
        val size = if (unknown) {
            byteArrayOf(0x01) + ByteArray(7) { 0xFF.toByte() }
        } else {
            byteArrayOf(0x01) + ByteArray(7) { index -> ((body.size.toLong() shr (8 * (6 - index))) and 0xFF).toByte() }
        }
        return byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67) + size + body
    }

    private fun mkv(unknown: Boolean = false) = ebmlHeader + segment(ByteArray(40) { it.toByte() }, unknown)

    /** Our id and a well-formed length, but somebody else's sixteen bytes inside. */
    private fun theirs(): ByteArray {
        val body = ByteArray(16) { 0x7F } + "not ours".toByteArray()
        val length = byteArrayOf(0x01) + ByteArray(7) { index -> ((body.size.toLong() shr (8 * (6 - index))) and 0xFF).toByte() }
        return byteArrayOf(0x1E, 0x57, 0x6B, 0x45) + length + body
    }

    @Test
    fun `a payload round-trips and the bytes in front of it are untouched`() {
        val original = mkv()
        val written = assertNotNull(MatroskaRecipe.with(original, PAYLOAD))

        assertEquals(PAYLOAD, MatroskaRecipe.scan(written).payload)
        assertContentEquals(original, written.copyOfRange(0, original.size))
        assertContentEquals(original, MatroskaRecipe.without(written))
    }

    @Test
    fun `a file carrying nothing has nothing to remove`() {
        assertNull(MatroskaRecipe.without(mkv()))
        assertNull(MatroskaRecipe.scan(mkv()).payload)
    }

    @Test
    fun `a streamed Segment of unknown size is refused`() {
        assertFalse(MatroskaRecipe.accepts(mkv(unknown = true)))
        assertNull(MatroskaRecipe.with(mkv(unknown = true), PAYLOAD))
        assertTrue(MatroskaRecipe.accepts(mkv()))
    }

    @Test
    fun `a container that is not EBML is refused`() {
        val riff = "RIFF".toByteArray() + ByteArray(4) + "AVI ".toByteArray() + ByteArray(40)
        assertFalse(MatroskaRecipe.accepts(riff))
        assertNull(MatroskaRecipe.with(riff, PAYLOAD))
    }

    @Test
    fun `boxIn spans the element and reaches the end`() {
        val original = mkv()
        val box = assertNotNull(MatroskaRecipe.box(PAYLOAD))
        val written = original + box

        val range = assertNotNull(MatroskaRecipe.boxIn(written))
        assertEquals(original.size, range.first)
        assertEquals(written.size, range.last + 1)
    }

    @Test
    fun `box refuses a payload past the tail window`() {
        assertNull(MatroskaRecipe.box("x".repeat(TAIL_WINDOW)))
    }

    @Test
    fun `an element with our id but not our identifier is neither read nor removed`() {
        val foreign = mkv() + theirs()

        assertNull(MatroskaRecipe.scan(foreign).payload)
        assertNull(MatroskaRecipe.without(foreign))
        assertNull(MatroskaRecipe.boxIn(foreign))
    }
}
