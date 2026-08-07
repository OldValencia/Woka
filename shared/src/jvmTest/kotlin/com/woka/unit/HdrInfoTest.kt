package com.woka.unit

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.engine.probe.readHdrInfo
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_UNSPECIFIED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HdrInfoTest {

    @Test
    fun `plain BT709 is left alone`() {
        val info = readHdrInfo(AVCOL_TRC_BT709, AVCOL_PRI_BT709)
        assertEquals(TransferKind.SDR, info.transfer)
        assertFalse(info.isWideGamut)
        assertFalse(info.needsToneMap)
    }

    @Test
    fun `PQ over BT2020 is tone mapped and wide`() {
        val info = readHdrInfo(AVCOL_TRC_SMPTE2084, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.PQ, info.transfer)
        assertTrue(info.isWideGamut)
        assertTrue(info.needsToneMap)
    }

    @Test
    fun `HLG is recognised as HDR in its own right`() {
        val info = readHdrInfo(AVCOL_TRC_ARIB_STD_B67, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.HLG, info.transfer)
        assertTrue(info.needsToneMap)
    }

    @Test
    fun `an HDR transfer over BT709 primaries still needs tone mapping`() {
        val info = readHdrInfo(AVCOL_TRC_SMPTE2084, AVCOL_PRI_BT709)
        assertEquals(TransferKind.PQ, info.transfer)
        assertFalse(info.isWideGamut)
        assertTrue(info.needsToneMap)
    }

    @Test
    fun `a wide gamut alone is enough to convert`() {
        val info = readHdrInfo(AVCOL_TRC_BT709, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.SDR, info.transfer)
        assertTrue(info.isWideGamut)
        assertTrue(info.needsToneMap)
    }

    @Test
    fun `unspecified metadata is read as SDR rather than throwing`() {
        val info = readHdrInfo(AVCOL_TRC_UNSPECIFIED, AVCOL_PRI_UNSPECIFIED)
        assertEquals(TransferKind.SDR, info.transfer)
        assertFalse(info.needsToneMap)
    }

    @Test
    fun `a value no enum covers falls back to SDR`() {
        val info = readHdrInfo(-1, 9999)
        assertEquals(TransferKind.SDR, info.transfer)
        assertFalse(info.isWideGamut)
        assertFalse(info.needsToneMap)
    }
}
