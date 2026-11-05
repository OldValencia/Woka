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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.join
import com.woka.feature.editor.tools.trim.withSpeed
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlin.math.roundToInt

/** `.play`: a 2 px white line, and `.play::before`'s 10 px knob. */
internal val PLAY_HEAD = 2.dp
private val PLAY_KNOB = 10.dp

/**
 * The play head, and in the trim state the two actions that ride it: **✂** splits there
 * (ME-212) and **❄** freezes the frame there (ME-217). Both act on the play head and not on
 * the selection — which is why Freeze moved here from the clip bar (the author, 2026-11-04) —
 * and each names itself on hover.
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
    onFreeze: () -> Unit = {},
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
    // On a freeze neither applies — one frame has nothing to cut and is frozen already — so
    // both stay in place but grey and deaf (the author, 2026-11-04).
    val onFreezeClip = clips.getOrNull(walk.index)?.isFreeze == true
    PlayHeadAction(x, geometry.viewPx, slot = 0, Icons.Static.Trim, "Split here", "editor-strip-split", !onFreezeClip, onSplit)
    PlayHeadAction(x, geometry.viewPx, slot = 1, Icons.Static.Freeze, "Freeze this frame", "editor-strip-freeze", !onFreezeClip, onFreeze)
}

/**
 * The selected [clip]'s bar over [anchorX]: its structure while `Clips` is open, and its look
 * — `Copy`, `Paste ▾` — whenever there is another clip to take it to (ME-267).
 */
@Composable
internal fun SelectedClipBar(state: StripState, clips: List<TrimClip>, clip: TrimClip, edits: EditorTimelineScope, anchorX: Float) {
    ClipBar(
        cutting = edits.editing,
        canDelete = clips.size > 1,
        anchorX = anchorX,
        onDelete = { state.deleteSelected(clip.id) },
        onDuplicate = { state.duplicateSelected(clip.id) },
        onJoin = { state.joinSelected(clip.id) }.takeIf { clips.join(clip.id) != clips },
        speed = clip.speed,
        // A freeze has no speed (ME-217).
        onSpeed = { speed: Float -> state.apply(clips.withSpeed(clip.id, speed)) }.takeIf { !clip.isFreeze },
        look = state.lookActions(clip, edits).takeIf { clips.size > 1 }
    )
}
