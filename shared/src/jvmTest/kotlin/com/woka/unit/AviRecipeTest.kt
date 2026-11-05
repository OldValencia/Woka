package com.woka.unit

import com.woka.feature.editor.recipe.AviRecipe
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Odd on purpose: the chunk then needs its pad byte, which its size does not count. */
private const val PAYLOAD = "{\"ops\":[1]}"

/**
 * ME-283's byte contract over a hand-built `RIFF AVI ` — the chunk at the end, the pad
 * byte, and the size at offset 4 that counts everything after itself.
 */
class AviRecipeTest {

    private fun le32(value: Int) = ByteArray(4) { byte -> ((value shr (8 * byte)) and 0xFF).toByte() }

    private fun avi(body: ByteArray = ByteArray(36) { it.toByte() }): ByteArray =
        "RIFF".toByteArray() + le32(4 + body.size) + "AVI ".toByteArray() + body

    private fun riffSize(bytes: ByteArray): Int =
        (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or ((bytes[7].toInt() and 0xFF) shl 24)

    @Test
    fun `a payload round-trips, padded, and the RIFF size follows it`() {
        val original = avi()
        val written = assertNotNull(AviRecipe.with(original, PAYLOAD))

        assertEquals(PAYLOAD, AviRecipe.scan(written).payload)
        assertEquals(0, written.size % 2, "the chunk was not padded to an even length")
        assertEquals(written.size - 8, riffSize(written))
        assertContentEquals(original, AviRecipe.without(written))
    }

    @Test
    fun `a file carrying nothing has nothing to remove`() {
        assertNull(AviRecipe.without(avi()))
        assertNull(AviRecipe.scan(avi()).payload)
    }

    @Test
    fun `a RIFF that does not end at the end of the file is refused`() {
        // An OpenDML file: a second `RIFF AVIX` after the first.
        val chained = avi() + "RIFF".toByteArray() + le32(4) + "AVIX".toByteArray()
        assertTrue(AviRecipe.accepts(chained))
        assertFalse(AviRecipe.fits(chained, chained.size.toLong()))
        assertNull(AviRecipe.with(chained, PAYLOAD))
    }

    @Test
    fun `a RIFF that is not an AVI is refused`() {
        val webp = "RIFF".toByteArray() + le32(4) + "WEBP".toByteArray()
        assertFalse(AviRecipe.accepts(webp))
    }

    @Test
    fun `boxIn and the length patch agree with the byte path`() {
        val original = avi()
        val box = assertNotNull(AviRecipe.box(PAYLOAD))
        val range = assertNotNull(AviRecipe.boxIn(original + box))

        assertEquals(original.size, range.first)
        assertEquals(original.size + box.size, range.last + 1)
        val patch = AviRecipe.lengthPatch((original.size + box.size).toLong())
        assertEquals(4L, patch.at)
        assertContentEquals(le32(original.size + box.size - 8), patch.bytes)
    }
}
