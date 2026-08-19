package com.woka.unit

import com.woka.feature.editor.recipe.IsoBmffRecipe
import com.woka.feature.editor.recipe.TAIL_WINDOW
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val PAYLOAD = "{\"ops\":[]}"

/**
 * The `uuid` box surgery on its own, against files built here rather than photographs
 * (ME-108).
 *
 * `HeicRecipeTest` proves it works on what a phone produces; this proves what it does
 * to a file that is **wrong** — a box lying about its size, somebody else's `uuid`, a
 * container that is not ISOBMFF at all. Every one must read as "no recipe" and none of
 * them may throw, because the alternative is a photograph the app refuses to open.
 */
class IsoBmffRecipeTest {

    private fun sizeOf(total: Int) = byteArrayOf(
        ((total shr 24) and 0xFF).toByte(),
        ((total shr 16) and 0xFF).toByte(),
        ((total shr 8) and 0xFF).toByte(),
        (total and 0xFF).toByte()
    )

    private fun box(type: String, body: ByteArray): ByteArray =
        sizeOf(8 + body.size) + type.toByteArray(Charsets.US_ASCII) + body

    /** `ftyp` first, then whatever the case needs — the shape of every real file. */
    private fun iso(vararg boxes: ByteArray): ByteArray =
        box("ftyp", "heic".toByteArray(Charsets.US_ASCII) + ByteArray(8)) +
            boxes.fold(ByteArray(0)) { all, part -> all + part }

    private fun theirs(): ByteArray = box("uuid", ByteArray(16) { 0x7F } + "not ours".toByteArray())

    @Test
    fun `a payload round-trips through an appended box`() {
        val written = assertNotNull(IsoBmffRecipe.with(iso(box("mdat", ByteArray(64))), PAYLOAD))

        assertEquals(PAYLOAD, IsoBmffRecipe.scan(written).payload)
    }

    /** Appended, never inserted: every byte of the original keeps its offset. */
    @Test
    fun `the original bytes are untouched in front of it`() {
        val original = iso(box("mdat", ByteArray(64)))
        val written = assertNotNull(IsoBmffRecipe.with(original, PAYLOAD))

        assertContentEquals(original, written.copyOfRange(0, original.size))
        assertContentEquals(original, assertNotNull(IsoBmffRecipe.without(written)))
    }

    @Test
    fun `a file carrying nothing has nothing to remove`() {
        val original = iso(box("mdat", ByteArray(16)))

        assertNull(IsoBmffRecipe.scan(original).payload)
        assertNull(IsoBmffRecipe.without(original))
    }

    /** A `uuid` box is a shared slot: Canon, Adobe and GoPro all write them. */
    @Test
    fun `somebody else's uuid box is neither read nor removed`() {
        val other = iso(theirs())

        assertNull(IsoBmffRecipe.scan(other).payload)
        assertNull(IsoBmffRecipe.without(other))
    }

    @Test
    fun `ours is found with another uuid box beside it`() {
        val written = assertNotNull(IsoBmffRecipe.with(iso(theirs()), PAYLOAD))

        assertEquals(PAYLOAD, IsoBmffRecipe.scan(written).payload)
        assertContentEquals(iso(theirs()), assertNotNull(IsoBmffRecipe.without(written)))
    }

    @Test
    fun `a box whose size runs past the end of the file reads as nothing`() {
        val written = assertNotNull(IsoBmffRecipe.with(iso(), PAYLOAD))
        // The size field of our own box, made to claim a kilobyte more than there is.
        val at = written.size - (PAYLOAD.length + 24)
        written[at + 2] = 0x04

        assertNull(IsoBmffRecipe.scan(written).payload)
        assertNull(IsoBmffRecipe.without(written))
    }

    @Test
    fun `a container that is not ISOBMFF is refused rather than appended to`() {
        assertNull(IsoBmffRecipe.with(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0), PAYLOAD))
        assertNull(IsoBmffRecipe.with(ByteArray(0), PAYLOAD))
    }

    /**
     * The rule that makes a tail read a real answer: a recipe that would not fit the
     * window a reader reads is refused at write time rather than written where nothing
     * will look for it.
     */
    @Test
    fun `a payload larger than the read window is refused`() {
        val huge = "x".repeat(TAIL_WINDOW)

        assertNull(IsoBmffRecipe.with(iso(), huge))
    }

    /** Truncation is a head carrier's problem; our box is the last thing in the file. */
    @Test
    fun `a scan never asks for a second read`() {
        assertEquals(false, IsoBmffRecipe.scan(iso(box("mdat", ByteArray(8)))).truncated)
    }
}
