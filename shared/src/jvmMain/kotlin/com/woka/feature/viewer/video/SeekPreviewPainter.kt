package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.woka.core.data.StillColorInfo
import com.woka.core.data.applyTo
import com.woka.feature.viewer.image.rememberHdrModeAccess
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.toColour

/**
 * Paints a seek-bar preview the way the picture itself is painted.
 *
 * A preview is decoded straight off the stream, so on an HDR file its pixels are
 * PQ- or HLG-encoded exactly like a played frame. Drawn with a plain
 * [BitmapPainter] those values are read as if they were sRGB, which is what made
 * previews come out flat and washed out beside the video they were taken from. The
 * tone curve belongs here for the same reason it belongs in `MediaViewerFrame`.
 *
 * [VideoPlayerState.streamHdrInfo] rather than `currentFrameHdrInfo`: the stream's
 * own description is stable for the whole track, so reading it costs no
 * recomposition at the frame rate — and a preview is a frame of that track no
 * matter what happens to be on screen when it is drawn. So it takes the film's white
 * point (ME-301), never a scene's (ME-305), and the HDR mode like the picture (ME-304).
 */
@Composable
internal fun rememberPreviewPainter(
    videoState: VideoPlayerState?,
    bitmap: ImageBitmap?
): Painter? {
    if (bitmap == null) return null
    val mode = rememberHdrModeAccess().current()
    val stream = videoState?.streamHdrInfo?.toColour() ?: StillColorInfo.Sdr
    val hdr = mode.applyTo(stream).takeIf { it.needsColorManagement }
    return remember(bitmap, hdr) {
        if (hdr == null) BitmapPainter(bitmap)
        else HdrImagePainter(bitmap, hdr.transfer, hdr.isWideGamut, whitePoint = hdr.whitePoint ?: ASSUMED_WHITE_POINT)
    }
}
