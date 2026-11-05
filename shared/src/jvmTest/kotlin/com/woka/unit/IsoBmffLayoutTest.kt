package com.woka.unit

import com.woka.feature.editor.recipe.isoBoxesEndCleanly
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 1.0.6 review: where an edit appended to an ISOBMFF file would actually land. */
class IsoBmffLayoutTest {

    private fun box(type: String, body: Int, declared: Int = 8 + body): ByteArray =
        ByteBuffer.allocate(8 + body).putInt(declared).put(type.toByteArray(Charsets.US_ASCII)).array()

    private fun fileOf(vararg parts: ByteArray): File =
        File.createTempFile("layout", ".mp4").apply {
            deleteOnExit()
            writeBytes(parts.fold(ByteArray(0)) { all, part -> all + part })
        }

    private val ftyp = box("ftyp", 8)
    private val moov = box("moov", 32)

    @Test
    fun `an ordinary film, padded or not, takes an append`() {
        assertTrue(fileOf(ftyp, moov, box("mdat", 64)).isoBoxesEndCleanly())
        assertTrue(fileOf(ftyp, moov, box("mdat", 64), ByteArray(16)).isoBoxesEndCleanly(), "zero padding refused")
    }

    @Test
    fun `a box that runs to or past the end would swallow it`() {
        assertFalse(fileOf(ftyp, moov, box("mdat", 64, declared = 0)).isoBoxesEndCleanly(), "mdat of size 0")
        assertFalse(fileOf(ftyp, moov, box("mdat", 64, declared = 500)).isoBoxesEndCleanly(), "a cut-off mdat")
    }

    @Test
    fun `a fragmented film's closing index stays last`() {
        assertFalse(fileOf(ftyp, moov, box("moof", 16), box("mdat", 32), box("mfra", 16)).isoBoxesEndCleanly())
    }
}
