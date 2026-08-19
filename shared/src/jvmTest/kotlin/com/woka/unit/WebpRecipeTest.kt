package com.woka.unit

import com.woka.feature.editor.recipe.WebpRecipe
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

/**
 * The RIFF surgery on its own, against files built here (ME-137).
 *
 * Two things are asserted on every path and they are the two the format punishes: the
 * `RIFF` size counts everything after itself, and a chunk of odd length is followed by
 * a pad byte its own size field does not count. A file that gets either wrong opens
 * nowhere.
 */
class WebpRecipeTest {

    private fun le32(value: Int) = ByteArray(4) { ((value shr (8 * it)) and 0xFF).toByte() }

    private fun le24(value: Int) = ByteArray(3) { ((value shr (8 * it)) and 0xFF).toByte() }

    private fun chunk(type: String, body: ByteArray): ByteArray =
        type.toByteArray(Charsets.US_ASCII) + le32(body.size) + body + ByteArray(body.size and 1)

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { all, part -> all + part }
        return "RIFF".toByteArray(Charsets.US_ASCII) +
            le32(4 + body.size) +
            "WEBP".toByteArray(Charsets.US_ASCII) +
            body
    }

    /** A key frame of 640×480: frame tag, start code, then the two 14-bit fields. */
    private fun lossy(width: Int = 640, height: Int = 480): ByteArray = chunk(
        "VP8 ",
        byteArrayOf(0x00, 0x00, 0x00, 0x9D.toByte(), 0x01, 0x2A) +
            byteArrayOf((width and 0xFF).toByte(), (width shr 8).toByte()) +
            byteArrayOf((height and 0xFF).toByte(), (height shr 8).toByte()) +
            ByteArray(8)
    )

    private fun vp8x(width: Int = 640, height: Int = 480): ByteArray =
        chunk("VP8X", byteArrayOf(0, 0, 0, 0) + le24(width - 1) + le24(height - 1))

    private fun ByteArray.riffSize(): Int =
        (this[4].toInt() and 0xFF) or ((this[5].toInt() and 0xFF) shl 8) or
            ((this[6].toInt() and 0xFF) shl 16) or ((this[7].toInt() and 0xFF) shl 24)

    private fun ByteArray.typeAt(at: Int) = String(this, at, 4, Charsets.US_ASCII)

    @Test
    fun `a simple file is promoted to the extended container on the way in`() {
        val written = assertNotNull(WebpRecipe.with(webp(lossy()), PAYLOAD))

        assertEquals("VP8X", written.typeAt(12), "a simple file may not carry our chunk")
        assertEquals(PAYLOAD, WebpRecipe.scan(written).payload)
        // 640×480 written as width-1 and height-1, three bytes each.
        assertContentEquals(le24(639), written.copyOfRange(24, 27))
        assertContentEquals(le24(479), written.copyOfRange(27, 30))
    }

    @Test
    fun `an already extended file gains no second VP8X`() {
        val written = assertNotNull(WebpRecipe.with(webp(vp8x(), lossy()), PAYLOAD))

        assertEquals(1, written.count(4) { it.typeAt(0) == "VP8X" }, "a second VP8X was written")
        assertEquals(PAYLOAD, WebpRecipe.scan(written).payload)
    }

    @Test
    fun `the RIFF size matches the file after a write and after a clear`() {
        val written = assertNotNull(WebpRecipe.with(webp(lossy()), PAYLOAD))
        assertEquals(written.size - 8, written.riffSize())

        val cleared = assertNotNull(WebpRecipe.without(written))
        assertEquals(cleared.size - 8, cleared.riffSize())
        assertNull(WebpRecipe.scan(cleared).payload)
    }

    /** The pad byte is what a naive walk trips over on the very next chunk. */
    @Test
    fun `an odd payload is padded and still reads back`() {
        val odd = "{\"ops\":[1]}"
        assertEquals(1, odd.length and 1)

        val written = assertNotNull(WebpRecipe.with(webp(lossy()), odd))

        assertEquals(0, written.size and 1, "the file did not end on an even boundary")
        assertEquals(odd, WebpRecipe.scan(written).payload)
    }

    @Test
    fun `a lossless file carries its own dimensions into the VP8X`() {
        val bits = (799) or (599 shl 14) or (1 shl 28)
        val lossless = chunk("VP8L", byteArrayOf(0x2F) + le32(bits) + ByteArray(4))

        val written = assertNotNull(WebpRecipe.with(webp(lossless), PAYLOAD))

        assertContentEquals(le24(799), written.copyOfRange(24, 27))
        assertContentEquals(le24(599), written.copyOfRange(27, 30))
        // The alpha hint travels with them, or a reader loses transparency.
        assertEquals(0x10, written[20].toInt() and 0xFF)
    }

    @Test
    fun `a bitstream this cannot measure is refused rather than guessed at`() {
        val unknown = webp(chunk("VP8 ", ByteArray(16)))

        assertNull(WebpRecipe.with(unknown, PAYLOAD))
    }

    @Test
    fun `a file that is not a webp is refused and reads as nothing`() {
        val notRiff = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(20)

        assertNull(WebpRecipe.with(notRiff, PAYLOAD))
        assertNull(WebpRecipe.scan(notRiff).payload)
        assertNull(WebpRecipe.without(notRiff))
    }

    @Test
    fun `a chunk running past the end asks for a second read`() {
        val written = assertNotNull(WebpRecipe.with(webp(lossy()), PAYLOAD))
        val cut = written.copyOfRange(0, written.size - 4)

        val scan = WebpRecipe.scan(cut)

        assertNull(scan.payload)
        assertTrue(scan.truncated, "a truncated walk must ask for the whole file")
    }

    private fun ByteArray.count(from: Int, of: (ByteArray) -> Boolean): Int {
        var found = 0
        var at = 12
        while (at + 8 <= size) {
            val chunkSize = copyOfRange(at + 4, at + 8).let { le ->
                (le[0].toInt() and 0xFF) or ((le[1].toInt() and 0xFF) shl 8) or
                    ((le[2].toInt() and 0xFF) shl 16) or ((le[3].toInt() and 0xFF) shl 24)
            }
            if (of(copyOfRange(at, at + from))) found++
            at += 8 + chunkSize + (chunkSize and 1)
        }
        return found
    }
}
