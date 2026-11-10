package com.woka.feature.editor.engine.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.resetOnRightClick
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.sound.SoundTool
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
 * it was let go. An edge drag re-flows the strip live around the edge (ME-218).
 */
@Composable
internal fun ClipStrip(
    player: VideoPlayerState,
    walk: ClipWalk,
    clips: List<TrimClip>,
    cells: FilmstripCells?,
    edits: EditorTimelineScope,
    durationMs: Long,
    zoom: StripZoom,
    modifier: Modifier = Modifier,
    /** The film's sound for the row under the strip (ME-241), shown while [SoundRows] says so. */
    peaks: AudioPeaks? = null
) {
    val state = remember(player, walk) { StripState(player, walk) }
    SideEffect { state.refresh(clips, edits, durationMs) }
    // Clips are picked in every tool once there are two (2026-11-05); cut only in Clips.
    val tabs = edits.editing || clips.size > 1
    val sound = if (SoundRows.shown && peaks != null) SOUND_ROOM else 0.dp
    val height by animateDpAsState((if (edits.editing) FILM_EDITING else FILM_RESTING + if (tabs) CLIP_TAB_HEIGHT else 0.dp) + sound)
    val drag = state.drag
    // The document as it stands, always — an edge drag re-flows it live (`edgeLayout`).
    val laidOut = clips
    val (lead, tail) = trimmedEnds(laidOut, durationMs, edits.editing)
    val selectedId = edits.selectedId?.takeIf { id -> clips.any { it.id == id } }
    // The tab that just lost the selection pulses once, so the author sees it go (ME-221).
    val pulse = rememberDeselectPulse(selectedId)

    BoxWithConstraints(modifier.height(height).testTag(CLIP_STRIP_TAG)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val gapPx = with(density) { JOIN_GAP.toPx() }
        val layout: StripLayout = { scale, scroll -> StripGeometry(laidOut, widthPx, gapPx, lead, tail, scale, scroll) }
        val carry = remember { EdgeCarry() }
        val geometry = carry.layout(drag as? StripDrag.Edge, layout, zoom)
        SideEffect {
            zoom.layoutNow = layout
            zoom.playHeadMsNow = { walk.timelineMs(laidOut, player.currentPositionMs) }
            carry.release(drag as? StripDrag.Edge, zoom, layout)
        }
        FollowPlayHead(player, walk, laidOut, zoom, layout)
        val gestures = Modifier.stripGestures(
            state = state,
            zoom = zoom,
            editing = edits.editing,
            widthPx = widthPx,
            gapPx = gapPx,
            handlePx = with(density) { HANDLE_REACH.toPx() }
        )
        Box(
            gestures.resetOnRightClick { state.resetCut() }
                .stripZoomWheel(zoom, layout)
                .clipToBounds()
                .fillMaxSize()
        ) {
            laidOut.forEachIndexed { index, clip ->
                val span = geometry.spans[index]
                // Only the part on screen is composed (ME-218): a zoomed clip outgrows a layout.
                val shown = span.visibleIn(widthPx) ?: return@forEachIndexed
                ClipTile(
                    clip = clip,
                    cells = cells,
                    showRange = edits.editing && span.width > with(density) { 96.dp.toPx() },
                    selected = tabs && clip.id == selectedId,
                    ownLook = edits.hasOwnLook(clip.key),
                    carried = (drag as? StripDrag.Reorder)?.id == clip.id,
                    modifier = Modifier
                        .offset { IntOffset(shown.left.roundToInt(), 0) }
                        .width(with(density) { shown.width.toDp() })
                        .fillMaxHeight()
                        .padding(bottom = (if (tabs) CLIP_TAB_HEIGHT else 0.dp) + sound),
                    fullWidthPx = span.width,
                    shiftPx = shown.left - span.left
                )
                // Over the part of the clip on screen, so a zoom does not carry it off.
                if (tabs && clip.id == selectedId && drag == null) SelectedClipBar(state, clips, clip, edits, shown.left + shown.width / 2f)
            }
            // Under the play head, which runs down through it.
            if (sound > 0.dp && peaks != null) SoundRow(laidOut, geometry, widthPx, peaks, SoundTool.mixIn(edits.sharedOps), { key -> SoundTool.mixIn(edits.ownOps(key)) }) { ms -> walk.seekTo(player, laidOut, ms) }
            if (edits.editing) TrimmedEnds(geometry, laidOut, durationMs, selectedId, null)
            edits.markedSpan?.let { MarkedSpan(geometry, laidOut, it) }
            PlayHead(player, walk, laidOut, geometry, edits.editing && drag == null, onFreeze = { state.freezeAtPlayHead() }) {
                state.splitAtPlayHead()
            }
            // The tabs sit under their clips, above the sound row.
            if (tabs) Box(Modifier.fillMaxSize().padding(bottom = sound)) {
                ClipTabs(laidOut, geometry, selectedId, pulse) { id -> laidOut.firstOrNull { it.id == id }?.let(state::pickTab) }
            }
            ZoomOverview(zoom, geometry)
        }
    }
}
