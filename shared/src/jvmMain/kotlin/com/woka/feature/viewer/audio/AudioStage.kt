package com.woka.feature.viewer.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.AudioMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/** `.art.paused`: the cover settles back and dims a touch (the author, 2026-11-01). */
private const val PAUSED_SCALE = 0.94f
private const val PAUSED_BRIGHTNESS = 0.88f

/**
 * The centre of the viewer for a track (AP-003, `audio-player.html`): the cover and
 * what the tags say. A click anywhere plays and pauses, as on a film — the viewer's
 * own tap does it, so nothing here listens. Codec and length are the Info panel's.
 */
@Composable
internal fun AudioStage(media: AudioMedia) {
    val tags by produceState<AudioTags?>(null, media.player.file) {
        value = withContext(Dispatchers.IO) { AudioTags.read(media.player.file) }
    }
    val paused = !media.player.isPlaying
    val scale by animateFloatAsState(if (paused) PAUSED_SCALE else 1f, tween(350))
    val brightness by animateFloatAsState(if (paused) PAUSED_BRIGHTNESS else 1f, tween(350))
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 150.dp), contentAlignment = Alignment.Center) {
        val side = min(300.dp, maxHeight * 0.46f)
        val shape = RoundedCornerShape(12.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(side)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(Theme.colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val cover = media.cover
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(dimmed(brightness))
                    )
                } else {
                    Image(
                        painter = painterResource(Icons.Static.MusicNote),
                        contentDescription = null,
                        modifier = Modifier.size(side * 0.34f),
                        colorFilter = ColorFilter.tint(Theme.colors.textTertiary)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val line = tags
                StageText(line?.title ?: media.player.file.nameWithoutExtension, 20f, FontWeight.SemiBold, false)
                StageText(line?.subtitle.orEmpty(), 13f, FontWeight.Normal, true)
            }
        }
    }
}

@Composable
private fun StageText(text: String, sizeSp: Float, weight: FontWeight, secondary: Boolean) {
    val color = if (secondary) Theme.viewer.onScrimSecondary else Theme.viewer.onScrim
    BasicText(
        text,
        modifier = Modifier.padding(horizontal = 24.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight, textAlign = TextAlign.Center)
    )
}

/** Every channel scaled by [factor]: brightness without a second layer over the cover. */
private fun dimmed(factor: Float): ColorMatrix = ColorMatrix(
    floatArrayOf(
        factor, 0f, 0f, 0f, 0f,
        0f, factor, 0f, 0f, 0f,
        0f, 0f, factor, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)
