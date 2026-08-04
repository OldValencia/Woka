package com.woka.ui.viewer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.woka.ui.viewer.player.VideoPlayerState

/**
 * The drawn frame itself: picks the tone-mapping painter for HDR video, applies the
 * zoom/pan transform and hosts the context menu. Split out of [MediaViewerContent]
 * per the §2 file-size mandate.
 */
@Composable
fun MediaViewerFrame(
    targetBitmap: ImageBitmap,
    videoState: VideoPlayerState?,
    renderScale: Float,
    offsetX: Float,
    offsetY: Float,
    subtitleBottomPadding: Dp,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit
) {
    val frameToDraw = if (videoState != null) videoState.currentFrame ?: targetBitmap else targetBitmap
    val hdrInfo = if (videoState != null && frameToDraw === videoState.currentFrame) {
        videoState.currentFrameHdrInfo
    } else {
        null
    }

    val painter = if (hdrInfo?.needsToneMap == true) {
        remember(frameToDraw) { HdrImagePainter(frameToDraw) }
    } else {
        remember(frameToDraw) { BitmapPainter(frameToDraw) }
    }

    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Edit") { onEdit() },
                ContextMenuItem("Copy") { onCopy() },
                ContextMenuItem("Info") { onInfo() }
            )
        }
    ) {
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
                modifier = Modifier.fillMaxSize()
            )
            // Bitmap subtitles carry their own placement, so they are drawn here —
            // inside the zoom/pan transform — rather than by the panel-anchored
            // text overlay (Step 10 / §2.2).
            videoState?.activeCue?.image?.let { image ->
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
