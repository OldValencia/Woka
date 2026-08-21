package com.woka.unit

import com.woka.feature.editor.recipe.GifRecipe
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PAYLOAD = "{\"ops\":[]}"

/** Signature (6) + logical screen descriptor (7). */
private const val HEADER = 13

/** `gctBits = 2` means `3 * 2^3` bytes of Global Color Table. */
private const val GCT_BYTES = 24

/**
 * The block surgery on its own, against files built here (ME-138).
 *
 * Two things are asserted on every path and they are the two the format punishes: a
 * payload is carried in sub-blocks of at most 255 bytes and has to come back
 * concatenated, and a block belonging to somebody else must survive both a write and a
 * clear exactly where it was. `NETSCAPE2.0` is that somebody in every real GIF, and it
 * is what tells a decoder how often to loop.
 */
class GifRecipeTest {

    private fun le16(value: Int) =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    /** [gctBits] null writes no Global Color Table at all, which is legal. */
    private fun gif(
        version: String = "GIF89a",
        gctBits: Int? = 2,
        vararg blocks: ByteArray
    ): ByteArray {
        val packed = if (gctBits == null) 0 else 0x80 or gctBits
        val gct = if (gctBits == null) ByteArray(0) else ByteArray(3 shl (gctBits + 1)) { 0x7F }
        val descriptor = le16(320) + le16(240) + byteArrayOf(packed.toByte(), 0, 0)
        val body = blocks.fold(ByteArray(0)) { all, part -> all + part }
        return version.toByteArray(Charsets.US_ASCII) + descriptor + gct + body +
            byteArrayOf(0x3B)
    }

    /** The looping extension every animated GIF carries, ahead of everything else. */
    private fun netscape(): ByteArray =
        byteArrayOf(0x21, 0xFF.toByte(), 0x0B) +
            "NETSCAPE2.0".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00)

    /** Descriptor, LZW minimum code size, and an empty sub-block chain. */
    private fun image(): ByteArray =
        byteArrayOf(0x2C) + le16(0) + le16(0) + le16(320) + le16(240) +
            byteArrayOf(0, 0x02, 0x00)

    private fun ByteArray.identityAt(at: Int) = String(this, at, 11, Charsets.US_ASCII)

    private fun ByteArray.countOf(identity: String): Int =
        (0..size - 11).count { identityAt(it) == identity }

    @Test
    fun `a recipe written into a gif reads back out of it`() {
        val written = assertNotNull(GifRecipe.with(gif(blocks = arrayOf(image())), PAYLOAD))

        assertEquals(PAYLOAD, GifRecipe.scan(written).payload)
        assertEquals(1, written.countOf("WokaEditRec"), "the block was written twice")
    }

    /**
     * A sub-block's length is one byte, so anything past 255 is written in runs. This is
     * the framing bug that would otherwise appear as a truncated recipe months later.
     */
    @Test
    fun `a payload longer than one sub-block survives being split and rejoined`() {
        val long = "x".repeat(700)
        val written = assertNotNull(GifRecipe.with(gif(blocks = arrayOf(image())), long))

        assertEquals(long, GifRecipe.scan(written).payload)
    }

    /**
     * **Ours goes after the leading application extensions, not in front of them.**
     * Decoders exist that honour the loop count only when its block sits first.
     */
    @Test
    fun `another application extension keeps its place through a write and a clear`() {
        val source = gif(blocks = arrayOf(netscape(), image()))
        val written = assertNotNull(GifRecipe.with(source, PAYLOAD))

        assertEquals(
            "NETSCAPE2.0",
            written.identityAt(HEADER + GCT_BYTES + 3),
            "our block displaced the looping extension"
        )
        assertEquals(1, written.countOf("NETSCAPE2.0"), "the looping extension was duplicated")
        assertContentEquals(source, GifRecipe.without(written), "a clear did not restore the file")
    }

    @Test
    fun `a file with no global colour table is written and read like any other`() {
        val source = gif(gctBits = null, blocks = arrayOf(image()))
        val written = assertNotNull(GifRecipe.with(source, PAYLOAD))

        assertEquals(PAYLOAD, GifRecipe.scan(written).payload)
        assertContentEquals(source, GifRecipe.without(written))
    }

    /** The Application Extension does not exist in `GIF87a`; the version is not promoted. */
    @Test
    fun `a GIF87a is refused rather than upgraded`() {
        assertNull(GifRecipe.with(gif(version = "GIF87a", blocks = arrayOf(image())), PAYLOAD))
    }

    @Test
    fun `a file that is not a gif at all refuses everything`() {
        val notAGif = "RIFF....WEBP".toByteArray(Charsets.US_ASCII)

        assertNull(GifRecipe.with(notAGif, PAYLOAD))
        assertNull(GifRecipe.without(notAGif))
        assertNull(GifRecipe.scan(notAGif).payload)
    }

    /** Nothing to remove is null, which is what lets `clear` skip the write entirely. */
    @Test
    fun `a file carrying no recipe reports none and is not rewritten`() {
        val source = gif(blocks = arrayOf(netscape(), image()))

        assertNull(GifRecipe.scan(source).payload)
        assertNull(GifRecipe.without(source))
    }

    /** A chain that runs off the end reads as "no recipe" rather than throwing. */
    @Test
    fun `a truncated sub-block chain is read as no recipe`() {
        val cut = assertNotNull(GifRecipe.with(gif(blocks = arrayOf(image())), "y".repeat(400)))
            .copyOfRange(0, HEADER + GCT_BYTES + 20)

        val scan = GifRecipe.scan(cut)
        assertNull(scan.payload)
        assertTrue(scan.truncated, "a chain that ran out did not say so")
    }
}
