package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The blurred, over-saturated copy of the frame sitting behind the chrome.
 *
 * Its own composable purely so the `frame` read lands here: read in
 * [MediaViewerToolbar]'s scope it pulled the nav buttons, the video controls and
 * the carousel into every presented frame (§10.4).
 *
 * The blur itself still re-rasterises per frame — whether the backdrop should
 * track the video at all, or freeze on a poster, is a visible choice and stays
 * open in the plan.
 */
@Composable
internal fun ViewerBackdrop(media: ViewerMedia, backdrop: BackdropTransform) {
    val saturation = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }) }
    val video = media as? ViewerMedia.Video
    // This is the second scene-level reader of the decoded frame, so it reports
    // too (§6). A draw site that skipped this would eventually be drawing a bitmap
    // the decoder had already closed.
    val drawnFrameId = video?.player?.currentFrameId ?: 0L
    Image(
        bitmap = video?.frame ?: media.poster,
        contentDescription = null,
        colorFilter = saturation,
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                video?.player?.frameRetention?.acknowledge(drawnFrameId)
            }
            .graphicsLayer(
                scaleX = backdrop.scale * 1.05f,
                scaleY = backdrop.scale * 1.05f,
                translationX = backdrop.offsetX,
                translationY = backdrop.offsetY,
                renderEffect = BlurEffect(50f / backdrop.scale, 50f / backdrop.scale)
            )
    )
}
