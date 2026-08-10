package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.video.HdrImagePainter

/**
 * How the viewer decides what a picture must be drawn *through*.
 *
 * Lifted out of [MediaViewerFrame] because the ambient fill needs the identical
 * decision on its own downscaled copy of the frame — an HDR video whose bars were
 * filled from untone-mapped pixels would put blown-out colour beside a correctly
 * mapped picture. Two copies of this branch is how those two drift apart.
 */

/**
 * The colour description that answers for [drawn].
 *
 * A decoded video frame answers for itself, per frame, because a track switch
 * changes it. Anything else is the still's own description.
 */
fun frameColorInfo(media: ViewerMedia, drawn: ImageBitmap): StillColorInfo {
    val video = media as? ViewerMedia.Video
    val fromFrame = if (drawn === video?.player?.currentFrame) {
        video.player.currentFrameHdrInfo?.let { StillColorInfo(it.transfer, it.isWideGamut) }
    } else {
        (media as? ViewerMedia.Still)?.color
    }
    return fromFrame ?: StillColorInfo.Sdr
}

/**
 * Tone-mapping painter for anything that cannot go straight to an sRGB display.
 *
 * [smooth] filters the upscale. It is off for the picture — a frame is drawn at
 * about its own size — and on for the ambient fill, which magnifies a 64-pixel
 * sample across a whole panel and shows every unfiltered texel as a block.
 */
@Composable
fun rememberFramePainter(
    bitmap: ImageBitmap,
    color: StillColorInfo,
    smooth: Boolean = false
): Painter =
    if (color.needsColorManagement) {
        remember(bitmap, color, smooth) {
            HdrImagePainter(bitmap, color.transfer, color.isWideGamut, smooth)
        }
    } else {
        remember(bitmap, smooth) {
            BitmapPainter(
                image = bitmap,
                filterQuality = if (smooth) FilterQuality.High else FilterQuality.Low
            )
        }
    }
