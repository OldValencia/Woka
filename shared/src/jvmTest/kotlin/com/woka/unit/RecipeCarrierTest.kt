package com.woka.unit

import com.woka.feature.editor.recipe.JpegRecipe
import com.woka.feature.editor.recipe.PngRecipe
import com.woka.feature.editor.recipe.RecipeCarrier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

/**
 * The byte surgery on its own, against headers built here rather than real files.
 *
 * `EditRecipeTest` proves the carriers work on pictures a camera produced; this
 * proves what they do to a file that is **wrong** — truncated, lying about a
 * length, not the format it claims. Every one of those must read as "no recipe"
 * and never throw, because the alternative is an unopenable photograph.
 */
class RecipeCarrierTest {

    private fun joined(parts: Array<out ByteArray>): ByteArray =
        parts.fold(ByteArray(0)) { all, part -> all + part }

    private fun jpeg(vararg segments: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            joined(segments) +
            byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02)

    /** An `APP1` holding nothing in particular — something to insert around. */
    private fun app1(): ByteArray = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x08) +
        "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)

    private fun png(vararg chunks: ByteArray): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", ByteArray(13)) +
            joined(chunks) +
            chunk("IDAT", ByteArray(4))

    private fun chunk(type: String, data: ByteArray): ByteArray =
        byteArrayOf(
            ((data.size shr 24) and 0xFF).toByte(),
            ((data.size shr 16) and 0xFF).toByte(),
            ((data.size shr 8) and 0xFF).toByte(),
            (data.size and 0xFF).toByte()
        ) + type.toByteArray(Charsets.US_ASCII) + data + ByteArray(4)

    private fun roundTrip(carrier: RecipeCarrier, container: ByteArray) {
        val written = assertNotNull(carrier.with(container, PAYLOAD))
        assertEquals(PAYLOAD, carrier.scan(written).payload)
        assertContentEquals(container, carrier.without(written))
    }

    @Test
    fun `a jpeg round-trips and comes back the size it started`() =
        roundTrip(JpegRecipe, jpeg(app1()))

    @Test
    fun `a png round-trips and comes back the size it started`() = roundTrip(PngRecipe, png())

    /** §5: `APP1` stays first, so an EXIF reader looking at segment one still finds it. */
    @Test
    fun `ours goes after the leading app run, not before it`() {
        val written = assertNotNull(JpegRecipe.with(jpeg(app1()), "{}"))
        assertEquals(0xE1.toByte(), written[3])
    }

    @Test
    fun `a segment that is not ours is left alone`() {
        val other = byteArrayOf(0xFF.toByte(), 0xE8.toByte(), 0x00, 0x08) +
            "Other!".toByteArray(Charsets.US_ASCII)
        val container = jpeg(other)
        assertNull(JpegRecipe.scan(container).payload)
        assertNull(JpegRecipe.without(container))
    }

    @Test
    fun `a container carrying nothing has nothing to remove`() {
        assertNull(JpegRecipe.without(jpeg(app1())))
        assertNull(PngRecipe.without(png()))
    }

    @Test
    fun `nothing here throws on rubbish`() {
        listOf<ByteArray>(
            ByteArray(0),
            ByteArray(3),
            byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
            // A length field that runs past the end of the file.
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE8.toByte(), 0x7F, 0x00),
            // A length field of zero, which cannot be right — it counts itself.
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE8.toByte(), 0x00, 0x00),
            png().copyOfRange(0, 20)
        ).forEach { bytes ->
            assertNull(JpegRecipe.scan(bytes).payload)
            assertNull(PngRecipe.scan(bytes).payload)
            assertNull(JpegRecipe.without(bytes))
            assertNull(PngRecipe.without(bytes))
        }
    }

    @Test
    fun `a payload too large for a jpeg segment is refused rather than truncated`() {
        assertNull(JpegRecipe.with(jpeg(app1()), "x".repeat(70_000)))
        // A PNG chunk is 32-bit; the same payload fits there.
        assertNotNull(PngRecipe.with(png(), "x".repeat(70_000)))
    }

    /**
     * Reaching the picture data is a definite answer, not a walk that ran out —
     * which is what lets a reader work from the head of a large file.
     */
    @Test
    fun `a complete header reports no truncation`() {
        assertFalse(JpegRecipe.scan(jpeg(app1())).truncated)
        assertFalse(PngRecipe.scan(png()).truncated)
    }

    @Test
    fun `a header cut mid-segment reports truncation`() {
        val cut = jpeg(app1()).copyOfRange(0, 5)
        assertTrue(JpegRecipe.scan(cut).truncated)
    }
}
