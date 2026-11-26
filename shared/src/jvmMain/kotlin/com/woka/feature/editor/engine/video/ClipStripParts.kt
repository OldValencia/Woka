package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.split
import kotlin.math.roundToInt

const val PLAY_HEAD_TAG = "editor-play-head"

/**
 * The play head (`.timeline .playhead`, RD-301): a 2 dp line in the room's orange down the ruler
 * and every lane, a 10 dp dot near its top, and the scissors riding it — **always**, in every
 * tab, as the prototype draws them. ❄ left the play head for the clip menu's `Hold this frame`.
 *
 * The position is read in `offset { }` lambdas — the layout phase — so the play head moves sixty
 * times a second without recomposing the tracks it sits over. The scissors' enablement is a
 * derived state: it changes at a join or on play, not every frame.
 */
@Composable
internal fun BoxScope.PlayHead(state: StripState, clips: List<TrimClip>, geometry: StripGeometry) {
    val player = state.player
    val walk = state.walk
    val x = { geometry.xOf(walk.timelineMs(clips, player.currentPositionMs)) }
    Box(
        Modifier
            .offset { IntOffset((x() - TimelineTokens.playheadWidth.toPx() / 2f).roundToInt(), 0) }
            .width(TimelineTokens.playheadWidth)
            .fillMaxHeight()
            .background(Theme.room.accent)
            .testTag(PLAY_HEAD_TAG)
    )
    Box(
        Modifier
            .offset {
                IntOffset((x() - TimelineTokens.playheadDot.toPx() / 2f).roundToInt(), TimelineTokens.playheadDotTop.roundToPx())
            }
            .size(TimelineTokens.playheadDot)
            .background(Theme.room.accent, CircleShape)
    )
    // Paused, and inside a clip rather than on a join or a still (`splittable` in the prototype).
    val cuts by remember(clips, player, walk) {
        derivedStateOf {
            !walk.isPlaying(player) && clips.split(walk.timelineMs(clips, player.currentPositionMs), "") != clips
        }
    }
    Scissors(x, cuts) { state.splitAtPlayHead() }
}
