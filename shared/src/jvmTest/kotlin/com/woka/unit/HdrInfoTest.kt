package com.woka.unit

import com.woka.core.data.TransferKind
import com.woka.core.data.needsColorManagement
import com.woka.core.data.needsGamutMap
import com.woka.core.data.needsToneCurve
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
        assertFalse(info.needsColorManagement)
    }

    @Test
    fun `PQ over BT2020 is tone mapped and wide`() {
        val info = readHdrInfo(AVCOL_TRC_SMPTE2084, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.PQ, info.transfer)
        assertTrue(info.isWideGamut)
        assertTrue(info.needsColorManagement)
    }

    @Test
    fun `HLG is recognised as HDR in its own right`() {
        val info = readHdrInfo(AVCOL_TRC_ARIB_STD_B67, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.HLG, info.transfer)
        assertTrue(info.needsColorManagement)
    }

    @Test
    fun `an HDR transfer over BT709 primaries still needs tone mapping`() {
        val info = readHdrInfo(AVCOL_TRC_SMPTE2084, AVCOL_PRI_BT709)
        assertEquals(TransferKind.PQ, info.transfer)
        assertFalse(info.isWideGamut)
        assertTrue(info.needsColorManagement)
    }

    @Test
    fun `a wide gamut alone is enough to convert`() {
        val info = readHdrInfo(AVCOL_TRC_BT709, AVCOL_PRI_BT2020)
        assertEquals(TransferKind.SDR, info.transfer)
        assertTrue(info.isWideGamut)
        assertTrue(info.needsColorManagement)
    }

    @Test
    fun `unspecified metadata is read as SDR rather than throwing`() {
        val info = readHdrInfo(AVCOL_TRC_UNSPECIFIED, AVCOL_PRI_UNSPECIFIED)
        assertEquals(TransferKind.SDR, info.transfer)
        assertFalse(info.needsColorManagement)
    }

    @Test
    fun `a value no enum covers falls back to SDR`() {
        val info = readHdrInfo(-1, 9999)
        assertEquals(TransferKind.SDR, info.transfer)
        assertFalse(info.isWideGamut)
        assertFalse(info.needsColorManagement)
    }
}

/**
 * The distinction Phase 5 was written to settle: **selecting** a picture for
 * colour management and **tone mapping** it are two different questions, and
 * treating them as one sent wide-gamut SDR through a PQ tone curve.
 */
class ColorManagementRuleTest {

    @Test
    fun `only an HDR transfer earns a tone curve`() {
        assertTrue(needsToneCurve(TransferKind.PQ))
        assertTrue(needsToneCurve(TransferKind.HLG))
        assertFalse(needsToneCurve(TransferKind.SDR))
    }

    /**
     * The case that was wrong. BT.2020 primaries with an ordinary transfer are
     * not HDR: the picture needs its primaries converted and its luminance left
     * exactly alone.
     */
    @Test
    fun `wide-gamut SDR is converted but never tone mapped`() {
        assertTrue(needsColorManagement(TransferKind.SDR, isWideGamut = true))
        assertTrue(needsGamutMap(isWideGamut = true))
        assertFalse(needsToneCurve(TransferKind.SDR))
    }

    /** An HDR transfer over BT.709 primaries is the mirror case: curve, no matrix. */
    @Test
    fun `HDR over narrow primaries is tone mapped but not converted`() {
        assertTrue(needsToneCurve(TransferKind.PQ))
        assertFalse(needsGamutMap(isWideGamut = false))
        assertTrue(needsColorManagement(TransferKind.PQ, isWideGamut = false))
    }

    @Test
    fun `plain SDR asks for nothing at all`() {
        assertFalse(needsColorManagement(TransferKind.SDR, isWideGamut = false))
    }
}
