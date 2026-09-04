package com.woka.unit

import com.woka.feature.editor.recipe.IsoBmffRecipe
import com.woka.feature.editor.recipe.TAIL_WINDOW
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

/**
 * The three members the in-place write is expressed in (ME-280) — `boxIn`, `accepts` and
 * `box`, over array literals.
 *
 * A second file rather than four more cases in `IsoBmffRecipeTest`, which is at the size
 * mandate's ceiling and whose subject is different: that one is about what the carrier
 * does to a file that is **wrong**, and this is about the offsets `RecipeInPlace` seeks
 * to. Its own builders for the same reason — the shipped test stays byte-identical.
 */
class IsoBmffTailTest {

    private fun sizeOf(total: Int) = byteArrayOf(
        ((total shr 24) and 0xFF).toByte(),
        ((total shr 16) and 0xFF).toByte(),
        ((total shr 8) and 0xFF).toByte(),
        (total and 0xFF).toByte()
    )

    private fun box(type: String, body: ByteArray): ByteArray =
        sizeOf(8 + body.size) + type.toByteArray(Charsets.US_ASCII) + body

    private fun iso(vararg boxes: ByteArray): ByteArray =
        box("ftyp", "heic".toByteArray(Charsets.US_ASCII) + ByteArray(8)) +
            boxes.fold(ByteArray(0)) { all, part -> all + part }

    private fun theirs(): ByteArray = box("uuid", ByteArray(16) { 0x7F } + "not ours".toByteArray())

    @Test
    fun `boxIn spans exactly the box a write appended`() {
        val original = iso(box("mdat", ByteArray(64)))
        val written = assertNotNull(IsoBmffRecipe.with(original, PAYLOAD))

        assertNull(IsoBmffRecipe.boxIn(original), "found a box in a file carrying nothing")
        val span = assertNotNull(IsoBmffRecipe.boxIn(written))
        assertEquals(original.size, span.first)
        // The **last index**, so `last + 1 == size` is what says "the box ends the file" —
        // the check `RecipeInPlace` refuses to truncate without.
        assertEquals(written.size - 1, span.last)
    }

    /** Somebody appending after ours is the one shape that must not be truncated away. */
    @Test
    fun `boxIn does not reach the end when foreign bytes follow ours`() {
        val written = assertNotNull(IsoBmffRecipe.with(iso(), PAYLOAD)) + theirs()

        val span = assertNotNull(IsoBmffRecipe.boxIn(written))
        assertTrue(span.last + 1 < written.size, "ours was taken for the last box in the file")
    }

    @Test
    fun `accepts reads the container's magic and nothing else`() {
        assertTrue(IsoBmffRecipe.accepts(iso()))
        assertTrue(!IsoBmffRecipe.accepts(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0)))
        assertTrue(!IsoBmffRecipe.accepts(ByteArray(0)))
    }

    /** One builder, two roads: the appended box and the in-place one cannot differ. */
    @Test
    fun `box is byte-identical to what a byte-path write appended`() {
        val original = iso(box("mdat", ByteArray(64)))
        val written = assertNotNull(IsoBmffRecipe.with(original, PAYLOAD))

        val standalone = assertNotNull(IsoBmffRecipe.box(PAYLOAD))

        assertContentEquals(written.copyOfRange(original.size, written.size), standalone)
    }

    /** The refusal that makes a 64 KB tail read a real answer, asked of `box` directly. */
    @Test
    fun `box refuses a payload larger than the read window`() {
        assertNull(IsoBmffRecipe.box("x".repeat(TAIL_WINDOW)))
    }
}
