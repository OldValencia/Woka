package com.woka.feature.viewer.video.engine.probe

import com.woka.core.data.TransferKind
import com.woka.core.data.needsToneMap
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084

data class HdrInfo(
    val transfer: TransferKind,
    val isWideGamut: Boolean
) {
    val needsToneMap: Boolean
        get() = needsToneMap(transfer, isWideGamut)
}

internal fun readHdrInfo(colorTrc: Int, colorPrimaries: Int): HdrInfo {
    val transfer = when (colorTrc) {
        AVCOL_TRC_SMPTE2084 -> TransferKind.PQ
        AVCOL_TRC_ARIB_STD_B67 -> TransferKind.HLG
        else -> TransferKind.SDR
    }
    val wide = colorPrimaries == AVCOL_PRI_BT2020
    return HdrInfo(transfer, wide)
}
