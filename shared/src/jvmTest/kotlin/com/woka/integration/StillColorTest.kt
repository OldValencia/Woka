package com.woka.integration

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.readStillColor
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.HDR_HLG_AVIF
import com.woka.fixtures.HDR_PQ_AVIF
import com.woka.fixtures.HDR_PQ_PNG
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A still's colour description — the thing a photograph had no way of supplying
 * until Phase 5, which is why a PQ still used to render as SDR.
 */
class StillColorTest {

    private fun colorOf(name: String) = readStillColor(SampleMedia.file(name))

    @Test
    fun `a PQ AVIF is read as PQ over wide primaries`() {
        val color = colorOf(HDR_PQ_AVIF)
        assertEquals(TransferKind.PQ, color.transfer)
        assertTrue(color.isWideGamut)
        assertTrue(color.needsColorManagement)
    }

    /**
     * HLG must be told apart from PQ, not merely from SDR. They were the same
     * answer until Phase 5, and the shader decoded both with PQ's curve.
     */
    @Test
    fun `an HLG AVIF is read as HLG and not as PQ`() {
        val color = colorOf(HDR_HLG_AVIF)
        assertEquals(TransferKind.HLG, color.transfer)
        assertTrue(color.isWideGamut)
    }

    /**
     * **The reason this does not follow the pixel path.** skia decodes PNG and
     * describes this file's colour as "gamma close to sRGB"; the `cICP` chunk
     * says PQ, and FFmpeg reads it. Asking the decoder that draws the pixels
     * would have left the one HDR still on the skia path unmanaged.
     */
    @Test
    fun `a PQ PNG is read as PQ even though skia decodes it`() {
        val color = colorOf(HDR_PQ_PNG)
        assertEquals(TransferKind.PQ, color.transfer)
        assertTrue(color.isWideGamut)
    }

    @Test
    fun `ordinary photographs are SDR and ask for nothing`() {
        listOf(STILL_PNG, STILL_JPG, STILL_AVIF, BURST_HEIC).forEach { name ->
            val color = colorOf(name)
            assertEquals(TransferKind.SDR, color.transfer, name)
            assertFalse(color.needsColorManagement, name)
        }
    }

    /** A file that cannot be opened is SDR, not an exception on the draw path. */
    @Test
    fun `an unreadable file falls back to SDR`() {
        val broken = File.createTempFile("woka-color", ".heic")
        broken.writeBytes(ByteArray(2048) { (it % 251).toByte() })
        assertEquals(TransferKind.SDR, readStillColor(broken).transfer)
        broken.delete()
    }
}
