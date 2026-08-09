package com.woka.feature.viewer.shell

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.video.HdrImagePainter
import com.woka.feature.viewer.video.engine.activeCue
import com.woka.feature.viewer.video.SubtitleBitmapLayer

/**
 * The drawn frame itself: picks the tone-mapping painter for HDR video, applies the
 * zoom/pan transform and hosts the context menu.
 *
 * [targetPoster] is the poster belonging to this copy of the enter/exit animation,
 * which during a file change is not the same as [media]'s — that is what the
 * outgoing copy is for.
 */
@Composable
fun MediaViewerFrame(
    media: ViewerMedia,
    targetPoster: ImageBitmap,
    renderScale: Float,
    offsetX: Float,
    offsetY: Float,
    subtitleBottomPadding: Dp,
    actions: ViewerActions
) {
    val video = media as? ViewerMedia.Video
    val frameToDraw = video?.player?.currentFrame ?: targetPoster
    // Read here, in the same composition as the bitmap above, so the pair the draw
    // scope reports is the pair it actually drew (§6).
    val drawnFrameId = video?.player?.currentFrameId ?: 0L
    // A decoded video frame answers for itself, per frame, because a track
    // switch changes it. Anything else is the still's own description — which
    // a photograph had no way of supplying at all until Phase 5.
    val color = if (frameToDraw === video?.player?.currentFrame) {
        video.player.currentFrameHdrInfo?.let { StillColorInfo(it.transfer, it.isWideGamut) }
    } else {
        (media as? ViewerMedia.Still)?.color
    } ?: StillColorInfo.Sdr

    val painter = if (color.needsColorManagement) {
        remember(frameToDraw, color) {
            HdrImagePainter(frameToDraw, color.transfer, color.isWideGamut)
        }
    } else {
        remember(frameToDraw) { BitmapPainter(frameToDraw) }
    }

    // Built once per action set. This composable recomposes on every presented
    // frame (it reads `currentFrame` above), and the list was rebuilt each time
    // (§10.6).
    val menuItems = remember(actions) {
        val fileActions = if (actions.showFileActions) {
            listOf(
                ContextMenuItem("Edit") { actions.onEdit() },
                ContextMenuItem("Copy") { actions.onCopy() },
                ContextMenuItem("Info") { actions.onInfo() }
            )
        } else {
            emptyList()
        }
        fileActions + actions.extra.map { ContextMenuItem(it.label, it.onClick) }
    }

    ContextMenuArea(items = { menuItems }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = renderScale,
                    scaleY = renderScale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            Image(
                painter = painter,
                contentDescription = "Image/Video Preview",
                contentScale = ContentScale.Fit,
                // Reports the frame this scene has finished with, which is what
                // lets the decoder free its 15MB the moment it is unreachable
                // (§6). The id is captured in composition alongside the bitmap it
                // belongs to, so the two can never be reported out of step.
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        video?.player?.frameRetention?.acknowledge(drawnFrameId)
                    }
            )
            // Bitmap subtitles carry their own placement, so they are drawn here —
            // inside the zoom/pan transform — rather than by the panel-anchored
            // text overlay (Step 10 / §2.2).
            video?.player?.activeCue?.image?.let { image ->
                SubtitleBitmapLayer(
                    image = image,
                    frameSize = IntSize(frameToDraw.width, frameToDraw.height),
                    bottomPadding = subtitleBottomPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
