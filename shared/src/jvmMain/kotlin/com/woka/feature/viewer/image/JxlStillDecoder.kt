package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import java.io.File

/**
 * JPEG XL, through libjxl 0.12 bundled with the app and called over Java FFM.
 *
 * The bundled FFmpeg has no JPEG XL decoder, and the pure-Java decoders run
 * 1–2.5 s per megapixel (photo-formats decision log). Every picture is converted
 * to sRGB by libjxl's own CMS, so a wide-gamut or ICC-tagged file is drawn in the
 * colours the viewer assumes everywhere else. An animation is a frame set, with
 * each frame's duration from its header.
 */
internal object JxlStillDecoder : StillDecoder {

    override fun nativeSize(file: File, frameIndex: Int): IntSize = decodeJxl(file, JXL_DEC_BASIC_INFO) { session ->
        session.next()
        session.orientedSize()
    }

    override fun frameSet(file: File): ImageFrameSet =
        decodeJxl(file, JXL_DEC_BASIC_INFO or JXL_DEC_FRAME) { session ->
            session.next()
            if (!session.basicFlag(BASIC_HAVE_ANIMATION)) return@decodeJxl ImageFrameSet.Single
            val numerator = session.basicInt(BASIC_TPS_NUMERATOR).toLong()
            val denominator = session.basicInt(BASIC_TPS_DENOMINATOR).toLong()
            val durations = mutableListOf<Int?>()
            while (session.next() == JXL_DEC_FRAME) {
                val ticks = session.frameDurationTicks()
                durations += if (numerator > 0) (ticks * 1000 * denominator / numerator).toInt() else null
            }
            if (durations.size <= 1) return@decodeJxl ImageFrameSet.Single
            ImageFrameSet(
                durations.mapIndexed { index, duration ->
                    ImageFrame(
                        index = index,
                        kind = if (index == 0) FrameKind.Primary else FrameKind.Sequence,
                        durationMs = duration
                    )
                }
            )
        }

    override fun frame(file: File, frameIndex: Int): ImageBitmap =
        decodeJxl(file, JXL_DEC_BASIC_INFO or JXL_DEC_COLOR_ENCODING or JXL_DEC_FULL_IMAGE) { session ->
            session.next()
            if (frameIndex > 0) JxlNative.skipFrames.invokeWithArguments(session.decoder, frameIndex.toLong())
            check(session.next() == JXL_DEC_COLOR_ENCODING) { "libjxl skipped the colour event" }
            session.requestSrgb()
            session.drawInto()
        }
}
