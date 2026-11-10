package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.soundGain
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.math.roundToInt

const val SOUND_ROW_TAG = "editor-sound-row"

private val SOUND_ROW = 26.dp

/** The row and the gap above it — what the strip grows by when the row is shown. */
internal val SOUND_ROOM = SOUND_ROW + 4.dp

private val SEGMENT = RoundedCornerShape(5.dp)

/**
 * Whether the strip shows its sound row (ME-241, the author 2026-11-10) — for as long as Woka
 * runs, so reopening the editor finds it as it was left. The first of the rows under the strip:
 * the place later work on sound (music over the film, its own speed) is done, not the clips'.
 */
internal object SoundRows {
    var shown by mutableStateOf(false)
}

/**
 * The sound row along the strip's foot: one segment under each clip, on the strip's own geometry
 * so a zoom and a scroll move both together, each holding its clip's wave. A press puts the play
 * head there and drags nothing — moving clips is the strip's, above.
 */
@Composable
internal fun BoxScope.SoundRow(
    clips: List<TrimClip>,
    geometry: StripGeometry,
    widthPx: Float,
    peaks: AudioPeaks,
    /** The whole film's sound and each clip's own, by layer key — the wave is drawn through both. */
    whole: SoundMix?,
    ownOf: (Int) -> SoundMix?,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(geometry)
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(SOUND_ROW)
            .testTag(SOUND_ROW_TAG)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onSeek(current.timelineAt(down.position.x))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            }
    ) {
        clips.forEachIndexed { index, clip ->
            val span = geometry.spans[index]
            val shown = span.visibleIn(widthPx) ?: return@forEachIndexed
            val placed = Modifier
                .offset { IntOffset(shown.left.roundToInt(), 0) }
                .width(with(density) { shown.width.toDp() })
                .fillMaxSize()
                .clip(SEGMENT)
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Theme.viewer.onScrim.copy(alpha = 0.16f), SEGMENT)
            val start = clips.startOf(index)
            val total = clips.totalMs()
            val own = ownOf(clip.key)
            Box(placed) {
                ClipWave(clip, peaks, span.width, shown.left - span.left, Modifier.fillMaxSize()) { into ->
                    soundGain(whole, own, start + into, total, into, clip.lengthMs)
                }
            }
        }
    }
}
