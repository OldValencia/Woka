package com.woka.unit

import com.woka.feature.editor.recipe.TiffRecipe
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

private const val TAG = 0xA057

/**
 * The IFD surgery on its own (ME-139), in **both byte orders** — a big-endian TIFF read as
 * little-endian is not a missing recipe, it is a destroyed file, so every case here runs twice.
 *
 * The assertion that matters most is the boring one: a clear gives back the exact original bytes.
 * That is what ME-107 promises, and this carrier is the only one that has to move a pointer in the
 * header to keep it.
 */
class TiffRecipeTest {

    /** `II`/`MM` + 42 + IFD0 offset, then some picture data, then IFD0 itself. */
    private fun tiff(little: Boolean, entries: List<Int> = listOf(0x0100, 0x0101), next: Int = 0): ByteArray {
        val data = ByteArray(24) { (it + 1).toByte() }
        val ifdAt = 8 + data.size
        val ifd = ByteArray(2 + entries.size * 12 + 4)
        putShort(ifd, 0, entries.size, little)
        entries.forEachIndexed { at, tag ->
            val entry = 2 + at * 12
            putShort(ifd, entry, tag, little)
            putShort(ifd, entry + 2, 3, little)
            putInt(ifd, entry + 4, 1, little)
            putInt(ifd, entry + 8, 7, little)
        }
        putInt(ifd, ifd.size - 4, next, little)

        val out = ByteArray(8 + data.size + ifd.size)
        out[0] = if (little) 'I'.code.toByte() else 'M'.code.toByte()
        out[1] = out[0]
        putShort(out, 2, 42, little)
        putInt(out, 4, ifdAt, little)
        data.copyInto(out, 8)
        ifd.copyInto(out, ifdAt)
        return out
    }

    private fun putShort(bytes: ByteArray, at: Int, value: Int, little: Boolean) {
        bytes[at] = (if (little) value and 0xFF else (value shr 8) and 0xFF).toByte()
        bytes[at + 1] = (if (little) (value shr 8) and 0xFF else value and 0xFF).toByte()
    }

    private fun putInt(bytes: ByteArray, at: Int, value: Int, little: Boolean) {
        for (byte in 0..3) {
            val shift = if (little) 8 * byte else 8 * (3 - byte)
            bytes[at + byte] = ((value shr shift) and 0xFF).toByte()
        }
    }

    private fun readShort(bytes: ByteArray, at: Int, little: Boolean): Int =
        if (little) {
            (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        } else {
            ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
        }

    private fun readInt(bytes: ByteArray, at: Int, little: Boolean): Int =
        (0..3).fold(0) { all, byte ->
            val shift = if (little) 8 * byte else 8 * (3 - byte)
            all or ((bytes[at + byte].toInt() and 0xFF) shl shift)
        }

    private fun bothOrders(block: (Boolean) -> Unit) = listOf(true, false).forEach(block)

    @Test
    fun `a payload round-trips in both byte orders`() = bothOrders { little ->
        val written = assertNotNull(TiffRecipe.with(tiff(little), PAYLOAD))

        assertEquals(PAYLOAD, TiffRecipe.scan(written).payload, "little=$little")
    }

    @Test
    fun `a clear returns the exact original bytes`() = bothOrders { little ->
        val original = tiff(little)
        val written = assertNotNull(TiffRecipe.with(original, PAYLOAD))

        assertContentEquals(original, assertNotNull(TiffRecipe.without(written)), "little=$little")
    }

    @Test
    fun `the header points at a rebuilt IFD0 that keeps every entry, in tag order`() =
        bothOrders { little ->
            val written = assertNotNull(TiffRecipe.with(tiff(little), PAYLOAD))

            val ifdAt = readInt(written, 4, little)
            assertEquals(3, readShort(written, ifdAt, little), "an entry was dropped")
            val tags = (0 until 3).map { readShort(written, ifdAt + 2 + it * 12, little) }
            assertEquals(listOf(0x0100, 0x0101, TAG), tags, "entries left ascending tag order")
        }

    /** A page chain that lost its link is a three-page scan showing one page. */
    @Test
    fun `the link to the next IFD survives the rebuild`() = bothOrders { little ->
        val written = assertNotNull(TiffRecipe.with(tiff(little, next = 4096), PAYLOAD))

        val ifdAt = readInt(written, 4, little)
        val link = readInt(written, ifdAt + 2 + 3 * 12, little)
        assertEquals(4096, link, "little=$little")
    }

    @Test
    fun `our entry addresses the payload where it actually lies`() = bothOrders { little ->
        val written = assertNotNull(TiffRecipe.with(tiff(little), PAYLOAD))

        val ifdAt = readInt(written, 4, little)
        val ours = (0 until 3).map { ifdAt + 2 + it * 12 }
            .first { readShort(written, it, little) == TAG }
        val at = readInt(written, ours + 8, little)
        val count = readInt(written, ours + 4, little)
        assertEquals(PAYLOAD, String(written, at, count, Charsets.UTF_8))
    }

    @Test
    fun `a second write replaces rather than adding a second tag`() = bothOrders { little ->
        val once = assertNotNull(TiffRecipe.with(tiff(little), PAYLOAD))
        val cleared = assertNotNull(TiffRecipe.without(once))

        val twice = assertNotNull(TiffRecipe.with(cleared, "{\"ops\":[2]}"))

        val ifdAt = readInt(twice, 4, little)
        assertEquals(3, readShort(twice, ifdAt, little))
        assertEquals("{\"ops\":[2]}", TiffRecipe.scan(twice).payload)
    }

    @Test
    fun `anything that is not a plain TIFF is refused and reads as nothing`() {
        val bigTiff = tiff(true).also { putShort(it, 2, 43, true) }
        val notTiff = ByteArray(32) { 0x7F }

        assertNull(TiffRecipe.with(bigTiff, PAYLOAD), "BigTIFF offsets are 64-bit")
        assertNull(TiffRecipe.with(notTiff, PAYLOAD))
        assertNull(TiffRecipe.scan(notTiff).payload)
        assertNull(TiffRecipe.without(notTiff))
    }

    /**
     * Cut **into the payload**, not off the end of the file: the block is identifier, the offset it
     * replaced, the length, the payload, and then the rebuilt IFD — so trimming the tail takes the
     * IFD and leaves a perfectly readable recipe. The first version of this test did that and passed
     * for the wrong reason.
     */
    @Test
    fun `a payload cut short reads as nothing rather than throwing`() {
        val original = tiff(true)
        val written = assertNotNull(TiffRecipe.with(original, PAYLOAD))
        val cut = written.copyOfRange(0, original.size + 20 + 4)

        assertNull(TiffRecipe.scan(cut).payload)
        assertTrue(TiffRecipe.scan(ByteArray(0)).payload == null)
    }
}
