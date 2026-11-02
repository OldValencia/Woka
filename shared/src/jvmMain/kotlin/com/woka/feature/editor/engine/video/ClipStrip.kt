package com.woka.feature.editor.engine.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.resetOnRightClick
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlin.math.roundToInt

/** `.film`'s two heights in `video-editor.html`: at rest, and while Trim is open. */
private val FILM_RESTING = 28.dp
private val FILM_EDITING = 60.dp

/** `.clip + .clip { margin-left: 3px }` — the air at every join. */
internal val JOIN_GAP = 3.dp

/** How far from an outer edge a press still takes the handle rather than the clip. */
private val HANDLE_REACH = 12.dp

internal const val CLIP_STRIP_TAG = "editor-clip-strip"

/**
 * The film row: every clip in timeline order, the play head, and — with Trim open — the
 * outer handles, the ✂ on the play head and the selected clip's actions (ME-210…ME-214).
 *
 * **What is laid out is what the document says**, and with Trim open the source trimmed off
 * either end is laid out beside it, dimmed (`StripGeometry`, [TrimmedEnds]): it is still in
 * the file, and since kept plus trimmed is the whole source, an outer handle lands where
 * it was let go. An edge drag holds the layout it began on until release.
 */
@Composable
internal fun ClipStrip(
    player: VideoPlayerState,
    walk: ClipWalk,
    clips: List<TrimClip>,
    cells: FilmstripCells?,
    edits: EditorTimelineScope,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val state = remember(player, walk) { StripState(player, walk) }
    SideEffect { state.refresh(clips, edits, durationMs) }
    val height by animateDpAsState(if (edits.editing) FILM_EDITING else FILM_RESTING)
    val drag = state.drag
    // An edge drag keeps the layout it began on, so the strip does not re-flow under the
    // handle; everything else lays out the document as it stands.
    val laidOut = (drag as? StripDrag.Edge)?.from ?: clips
    val (lead, tail) = trimmedEnds(laidOut, durationMs, edits.editing)
    val selectedId = edits.selectedId?.takeIf { id -> clips.any { it.id == id } }

    BoxWithConstraints(modifier.height(height).testTag(CLIP_STRIP_TAG)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val gapPx = with(density) { JOIN_GAP.toPx() }
        val geometry = StripGeometry(laidOut, widthPx, gapPx, lead, tail)
        val gestures = Modifier.stripGestures(
            state = state,
            editing = edits.editing,
            widthPx = widthPx,
            gapPx = gapPx,
            handlePx = with(density) { HANDLE_REACH.toPx() }
        )
        Box(gestures.resetOnRightClick { state.resetCut() }.fillMaxSize()) {
            laidOut.forEachIndexed { index, clip ->
                val span = geometry.spans[index]
                ClipTile(
                    clip = clip,
                    cells = cells,
                    showRange = edits.editing && span.width > with(density) { 96.dp.toPx() },
                    selected = edits.editing && clip.id == selectedId,
                    carried = (drag as? StripDrag.Reorder)?.id == clip.id,
                    modifier = Modifier
                        .offset { IntOffset(span.left.roundToInt(), 0) }
                        .width(with(density) { span.width.toDp() })
                        .fillMaxHeight()
                )
                if (edits.editing && clip.id == selectedId && drag == null) {
                    ClipBar(
                        canDelete = clips.size > 1,
                        anchorX = span.left + span.width / 2f,
                        onDelete = { state.deleteSelected(selectedId) },
                        onDuplicate = { state.duplicateSelected(selectedId) }
                    )
                }
            }
            if (edits.editing) TrimmedEnds(geometry, laidOut, cells, durationMs, selectedId, drag as? StripDrag.Edge)
            PlayHead(player, walk, laidOut, geometry, splitting = edits.editing && drag == null) {
                state.splitAtPlayHead()
            }
        }
    }
}
