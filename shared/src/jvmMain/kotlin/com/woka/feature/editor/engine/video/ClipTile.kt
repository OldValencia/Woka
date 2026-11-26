package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.frames.EditedDot
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip

/** `.timeline .clip { border-radius: 8px }`. */
internal val CLIP_RADIUS = 8.dp

/** `.clip.selected`: a 2 px ring in the room's orange; `:hover`, a 1 px one in white at 20 %. */
private val SELECTED_RING = 2.dp
private val HOVER_RING = Color.White.copy(alpha = 0.2f)

/** `.clip.dragging`: the carried clip rides 5 dp above its neighbours. */
private val LIFT = 5.dp

/** `.clip-wave.muted { opacity: .35 }`. */
private const val MUTED_WAVE = 0.35f

/**
 * One clip on the lane (`.timeline .clip`, RD-301): its stretch of the filmstrip over the top,
 * its sound in an 18 dp strip along the foot, its name in the corner — and the states the
 * prototype gives it: hovered, selected, carried.
 *
 * **The wave is always there** (left to judgement by the author, RD-301): the speaker that
 * showed a sound row under the strip (ME-241) went, since the peaks are measured either way and
 * a clip's sound — muted, faded, silent for a still — is now read off the clip itself.
 *
 * **The stills are drawn, not composed.** A tile per still would be a dozen composables per
 * clip recomposing as each one lands; drawn here, a still that arrives repaints this clip and
 * nothing else, because `stillAt` reads the snapshot map inside the draw scope.
 */
@Composable
internal fun ClipTile(
    clip: TrimClip,
    cells: FilmstripCells?,
    peaks: AudioPeaks?,
    look: ClipTileLook,
    modifier: Modifier = Modifier,
    /** The edit's gain a millisecond into this clip, its mute left out — a muted wave dims instead. */
    gainAt: (intoClipMs: Long) -> Float = { 1f },
    /**
     * The whole clip's width when this tile draws only the part on screen, and how far into
     * it that part starts (ME-218) — so a zoomed tile shows the stills of its own stretch.
     */
    fullWidthPx: Float = 0f,
    shiftPx: Float = 0f
) {
    val shape = RoundedCornerShape(CLIP_RADIUS)
    val ring = when {
        look.selected -> Modifier.border(SELECTED_RING, Theme.room.accent, shape)
        look.hovered -> Modifier.border(1.dp, HOVER_RING, shape)
        else -> Modifier
    }
    Box(
        modifier = modifier
            .graphicsLayer { translationY = if (look.carried) -LIFT.toPx() else 0f }
            .clip(shape)
            .background(Theme.room.well)
    ) {
        // A slot still decoding shimmers (2026-11-04); the draw says whether any is left.
        val loading = remember { mutableStateOf(true) }
        val phase = rememberShimmer(loading.value)
        Canvas(Modifier.fillMaxSize().padding(bottom = TimelineTokens.waveHeight)) {
            val missing = drawStills(clip, cells, phase.value, fullWidthPx.takeIf { it > 0f } ?: size.width, shiftPx)
            if (missing != loading.value) loading.value = missing
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(TimelineTokens.waveHeight)
                .background(Theme.room.soundBg)
                .alpha(if (look.muted) MUTED_WAVE else 1f)
        ) {
            if (peaks != null) {
                ClipWave(
                    clip, peaks, fullWidthPx.takeIf { it > 0f } ?: 0f, shiftPx,
                    Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp), Theme.room.sound, gainAt
                )
            }
        }
        BasicText(
            text = clipName(clip),
            modifier = Modifier.padding(start = 6.dp, top = 5.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            style = TimelineTokens.clipName.copy(color = Color.White),
            maxLines = 1
        )
        // ME-157's mark for a clip with a look of its own (ME-215): the frame strip's dot (RD-204).
        if (look.ownLook) EditedDot(Modifier.align(Alignment.TopEnd))
        Box(Modifier.matchParentSize().then(ring))
    }
}

/** How a [ClipTile] is drawn besides its clip: the five states the lane knows about it. */
@Immutable
internal data class ClipTileLook(
    val selected: Boolean = false,
    val hovered: Boolean = false,
    val carried: Boolean = false,
    /** The clip carries a look of its own — ME-157's dot, on a film's clip (ME-215). */
    val ownLook: Boolean = false,
    val muted: Boolean = false
)
