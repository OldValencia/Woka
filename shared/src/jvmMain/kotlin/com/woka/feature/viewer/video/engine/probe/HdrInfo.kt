package com.woka.feature.viewer.video.engine.probe

import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.core.data.needsColorManagement
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084

data class HdrInfo(
    val transfer: TransferKind,
    val isWideGamut: Boolean,
    /**
     * The film's declared peak in units of SDR white (ME-301) — **null until a decoded frame
     * was read**, because the codec parameters alone never carry it (`HdrPeak.kt`).
     */
    val whitePoint: Float? = null,
    /**
     * This frame's scene peak from HDR10+ (ME-305). Never part of `streamHdrInfo`, which
     * stays the stable thing a memoised fold keys on (ME-205).
     */
    val scenePeak: Float? = null
) {
    val needsColorManagement: Boolean
        get() = needsColorManagement(transfer, isWideGamut)
}

/** The painters' vocabulary: the scene's peak where there is one, else the film's. */
internal fun HdrInfo.toColour(): StillColorInfo =
    StillColorInfo(transfer, isWideGamut, scenePeak ?: whitePoint)

internal fun readHdrInfo(colorTrc: Int, colorPrimaries: Int): HdrInfo {
    val transfer = when (colorTrc) {
        AVCOL_TRC_SMPTE2084 -> TransferKind.PQ
        AVCOL_TRC_ARIB_STD_B67 -> TransferKind.HLG
        else -> TransferKind.SDR
    }
    val wide = colorPrimaries == AVCOL_PRI_BT2020
    return HdrInfo(transfer, wide)
}
