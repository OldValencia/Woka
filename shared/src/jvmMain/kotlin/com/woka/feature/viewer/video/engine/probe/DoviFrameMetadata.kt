package com.woka.feature.viewer.video.engine.probe

import com.woka.core.system.BundledNatives
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_METADATA
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_RGB
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import kotlin.math.pow

internal data class DoviFramePeaks(val sourceWhite: Float, val scenePeak: Float?)

/** Only our reconstructed RGB/PQ frames carry this combination with parsed Dolby metadata. */
internal fun AVFrame.convertedDoviPeaks(): DoviFramePeaks? =
    if (colorspace() == AVCOL_SPC_RGB && color_trc() == AVCOL_TRC_SMPTE2084) DoviFrameMetadata.peaks(this) else null

/** DV5-102: copied scalars from this frame's parsed RPU, in units of 100-nit SDR white. */
internal object DoviFrameMetadata {
    private val read by lazy {
        val symbol = BundledNatives.load("woka_dovi").find("woka_dovi_metadata").orElseThrow()
        Linker.nativeLinker().downcallHandle(symbol,
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG))
    }

    fun peaks(frame: AVFrame?): DoviFramePeaks? {
        if (frame == null) return null
        val side = av_frame_get_side_data(frame, AV_FRAME_DATA_DOVI_METADATA) ?: return null
        return Arena.ofConfined().use { arena ->
            val values = arena.allocate(JAVA_DOUBLE, 704)
            val address = MemorySegment.ofAddress(side.data().address())
            val status = read.invokeExact(address, side.size(), avutil_version(), values, 704L) as Int
            if (status != 0) throw UnsupportedVideoException("Dolby Vision Profile 5: invalid frame metadata ($status).")
            val source = pqWhite(values.getAtIndex(JAVA_DOUBLE, 5))
            val scene = if (values.getAtIndex(JAVA_DOUBLE, 6) == 1.0) pqWhite(values.getAtIndex(JAVA_DOUBLE, 8)) else null
            DoviFramePeaks(source, scene)
        }
    }

    private fun pqWhite(code: Double): Float {
        val p = (code / 4095.0).coerceIn(0.0, 1.0).pow(1.0 / 78.84375)
        val linear = ((p - 0.8359375).coerceAtLeast(0.0) / (18.8515625 - 18.6875 * p)).pow(1.0 / 0.1593017578125)
        return (linear * 100).toFloat().coerceAtLeast(1f)
    }
}
