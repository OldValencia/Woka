package com.woka.feature.viewer.shell.ambient

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.woka.feature.viewer.shell.rememberFramePainter
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/** How far the glow is turned down. It is a surround, not a second picture. */
private const val AMBIENT_ALPHA = 0.42f

/** A little more colour than the picture, so the bars read as light rather than as a copy. */
private const val AMBIENT_SATURATION = 1.15f

/**
 * Blur radius, measured in *sampled cells* rather than in pixels.
 *
 * A fixed pixel radius was the bug: 64px is about one cell of a 64-wide sample
 * stretched across a 3440px panel, which leaves the grid standing and reads as a
 * badly compressed picture rather than as light. Two and a half cells dissolves it,
 * and expressing it this way keeps it true on any panel.
 */
private const val AMBIENT_BLUR_CELLS = 2.5f

/** Sane bounds for the derived radius, for a container of an unexpected size. */
private const val AMBIENT_BLUR_MIN = 24f
private const val AMBIENT_BLUR_MAX = 400f

/** Pushes the crop's own edges past the container, under the blur. */
private const val AMBIENT_OVERSCAN = 1.10f

/**
 * The ambilight: the video's own colour, spread behind it so a fullscreen 16:9 file
 * on a wider panel sits in light rather than in two black slabs.
 *
 * Composed only where there are side bars to fill — see `MediaViewerContent`. The
 * picture itself is drawn over this, so everything here is what the picture does
 * not cover.
 */
@Composable
internal fun AmbientFillLayer(player: VideoPlayerState) {
    val sampler = remember(player) { AmbientSampler() }

    LaunchedEffect(player) {
        while (isActive) {
            player.currentFrame?.let { sampler.blend(it, player.currentFrameHdrInfo) }
            delay(AMBIENT_SAMPLE_MS.milliseconds)
        }
    }

    val bitmap = sampler.image
    val fade by animateFloatAsState(
        targetValue = if (bitmap != null) AMBIENT_ALPHA else 0f,
        animationSpec = tween(400)
    )
    if (bitmap == null) return

    val saturation = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(AMBIENT_SATURATION) })
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Constraints are device pixels, so the radius follows the panel rather
        // than being right on one screen and wrong on the next.
        val blur = (constraints.maxWidth.toFloat() / AMBIENT_W * AMBIENT_BLUR_CELLS)
            .coerceIn(AMBIENT_BLUR_MIN, AMBIENT_BLUR_MAX)
        Image(
            // The same painter choice the picture gets, so an HDR video's bars are
            // tone mapped by the same shader rather than clipping to blown-out
            // colour — filtered, because this one is magnified sixty times.
            // `moving`: the sampler blends a fresh frame every 200 ms, and this is a
            // 64-pixel smear of the picture rather than the picture (ME-120).
            painter = rememberFramePainter(bitmap, sampler.color, smooth = true, moving = true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = saturation,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = AMBIENT_OVERSCAN,
                    scaleY = AMBIENT_OVERSCAN,
                    alpha = fade,
                    // Clamp, not Decal: Decal fades the blur to transparent, and at
                    // this radius that is a dark band all round the picture.
                    renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                )
        )
    }
}
