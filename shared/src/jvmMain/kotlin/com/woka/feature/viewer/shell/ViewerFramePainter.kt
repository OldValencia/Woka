package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
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
    val fromFrame = if (isMovingFrame(media, drawn)) {
        val video = media as ViewerMedia.Video
        video.player.currentFrameHdrInfo?.let { StillColorInfo(it.transfer, it.isWideGamut) }
    } else {
        (media as? ViewerMedia.Still)?.color
    }
    return fromFrame ?: StillColorInfo.Sdr
}

/**
 * Whether [drawn] is a frame the player is about to replace, rather than a still.
 *
 * Its own function because two answers now hang off the question — which colour
 * description applies, and whether the white point can be measured — and the file's
 * whole point is that this branch is written once (ME-120).
 */
fun isMovingFrame(media: ViewerMedia, drawn: ImageBitmap): Boolean =
    drawn === (media as? ViewerMedia.Video)?.player?.currentFrame

/**
 * Tone-mapping painter for anything that cannot go straight to an sRGB display.
 *
 * [smooth] filters the upscale. It is off for the picture — a frame is drawn at
 * about its own size — and on for the ambient fill, which magnifies a 64-pixel
 * sample across a whole panel and shows every unfiltered texel as a block.
 *
 * **[moving] decides it, not [color]** (ME-177). A still is tone mapped where it is
 * decoded — `displayable` — and arrives here as ordinary sRGB, so mapping it again would
 * push it through the curve twice. Only a video frame reaches the shader now: its bitmap
 * is replaced at the frame rate, which is what put it out of `displayable`'s reach in the
 * first place.
 *
 * That is also why the white point is the constant here with no branch left. ME-120
 * measures a picture's own peak, and a frame that will be gone in 40 ms is the one thing
 * that cannot be measured — a running peak would make the curve move while the picture
 * plays.
 */
@Composable
fun rememberFramePainter(
    bitmap: ImageBitmap,
    color: StillColorInfo,
    smooth: Boolean = false,
    moving: Boolean = false
): Painter =
    if (moving && color.needsColorManagement) {
        remember(bitmap, color, smooth) {
            HdrImagePainter(
                bitmap = bitmap,
                transfer = color.transfer,
                isWideGamut = color.isWideGamut,
                smooth = smooth,
                whitePoint = ASSUMED_WHITE_POINT
            )
        }
    } else {
        remember(bitmap, smooth) {
            BitmapPainter(
                image = bitmap,
                filterQuality = if (smooth) FilterQuality.High else FilterQuality.Low
            )
        }
    }
