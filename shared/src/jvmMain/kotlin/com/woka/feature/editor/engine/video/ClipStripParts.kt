package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** `.play`: a 2 px white line, and `.play::before`'s 10 px knob. */
private val PLAY_HEAD = 2.dp
private val PLAY_KNOB = 10.dp

/** `.scissors`, shrunk from 24 to 22 so it sits inside the 60 dp film. */
private val SCISSORS = 22.dp
private val SCISSORS_GLYPH = 12.dp

/**
 * The play head, and in the trim state the **✂** that rides it (ME-212).
 *
 * The position is read in an `offset { }` lambda — the layout phase — so the play head
 * moves sixty times a second without recomposing the strip it sits in; the slider this
 * replaced made the same choice with its `value: () -> Float`.
 */
@Composable
internal fun BoxScope.PlayHead(
    player: VideoPlayerState,
    walk: ClipWalk,
    clips: List<TrimClip>,
    geometry: StripGeometry,
    splitting: Boolean,
    onSplit: () -> Unit
) {
    val x = { geometry.xOf(walk.timelineMs(clips, player.currentPositionMs)) }
    Box(
        Modifier
            .offset { IntOffset((x() - PLAY_HEAD.toPx() / 2f).roundToInt(), 0) }
            .width(PLAY_HEAD)
            .fillMaxHeight()
            .background(Color.White)
    )
    Box(
        Modifier
            .offset { IntOffset((x() - PLAY_KNOB.toPx() / 2f).roundToInt(), -PLAY_KNOB.roundToPx() / 2) }
            .size(PLAY_KNOB)
            .clip(CircleShape)
            .background(Color.White)
    )
    if (!splitting) return
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset((x() - SCISSORS.toPx() / 2f).roundToInt(), 0) }
            .size(SCISSORS)
            .clip(CircleShape)
            .background(Theme.colors.accentEditor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onSplit)
            .testTag("editor-strip-split"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.Trim),
            contentDescription = "Split at the play head",
            modifier = Modifier.size(SCISSORS_GLYPH),
            colorFilter = ColorFilter.tint(Theme.colors.onAccent)
        )
    }
}
