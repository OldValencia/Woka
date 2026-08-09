package com.woka.feature.viewer.shell.slider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

/**
 * A still for the pill to show, and the shape to reserve for it.
 *
 * [aspect] is the media's own, so a 2.39:1 film is not letterboxed inside a 16:9
 * hole. It being non-null is what turns the pill into a frame window at all —
 * a caller with no frames to show passes nothing and gets the plain time pill.
 *
 * A [Painter] rather than a bitmap, because how a still should be *painted* is
 * knowledge the slider does not have and must not acquire: an HDR frame needs the
 * tone-mapping shader its video is drawn through, and handing this a bitmap is
 * exactly what made HDR previews render as washed-out sRGB.
 */
data class SliderPreview(val painter: Painter?, val aspect: Float?)

/**
 * The frame window at the top of the pill, flush to its edges so the two read as one
 * object rather than a panel with a picture glued inside it.
 *
 * The fade is on the box rather than on each bitmap, so it plays once — when the
 * first still of a session arrives — and never again. Later stills swap instantly,
 * which is what keeps a sweep crisp instead of mushy. Until the first one lands the
 * reserved space is a darker scrim, so it reads as part of the pill rather than as
 * something failing to load.
 */
@Composable
internal fun SliderPreviewWindow(painter: Painter?, aspect: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        AnimatedVisibility(visible = painter != null, enter = fadeIn(tween(180))) {
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
