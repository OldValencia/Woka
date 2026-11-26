package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.image.soundGain
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.math.roundToInt

/** `.clip + .clip`: the air at every join. */
internal val JOIN_GAP = 3.dp

/** How far from an edge a press still takes the handle rather than the clip. */
private val HANDLE_REACH = 12.dp

internal const val CLIP_STRIP_TAG = "editor-clip-strip"

/** The tabs a click on a clip leaves for Clip (RD-301): the whole film's Transform, and Markup. */
private val LEAVES_FOR_CLIP = setOf(ToolDomain.Geometry, ToolDomain.Annotate)

/**
 * The clip lane (`.video-row`, RD-301): every clip in timeline order on the lanes' geometry,
 * each with its stills and its wave, and the handles of the selected clip and the film's ends.
 *
 * **What is laid out is what the document says**; while an outer end is dragged the source
 * trimmed off either end stands beside it, dimmed (`trimmedEnds`). An edge drag re-flows the
 * lane live around the edge (ME-218).
 */
@Composable
internal fun ClipStrip(
    state: StripState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    geometry: StripGeometry,
    view: TimelineView,
    cells: FilmstripCells?,
    peaks: AudioPeaks?
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(geometry)
    // Read at event time: the scope is rebuilt every composition, the handlers are not.
    val scope by rememberUpdatedState(edits)
    val drag = state.drag
    val selectedId = edits.selectedId?.takeIf { id -> clips.any { it.id == id } }
    val hovered = view.hoverX?.let { geometry.indexAt(it) }
    val whole = SoundTool.mixIn(edits.sharedOps)
    val total = clips.totalMs()
    val waveTop = with(density) { (TimelineTokens.videoLaneHeight - TimelineTokens.waveHeight).toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.room.lane, TimelineTokens.laneShape)
            .stripGestures(state, { current }, with(density) { HANDLE_REACH.toPx() }, waveTop) { clip, onWave ->
                scope.pick(clip.id, clip.key)
                if (onWave) scope.openTool(SoundTool.manifest.id) else scope.openTool(TrimTool.manifest.id, LEAVES_FOR_CLIP)
            }
            .pointerInput(state) {
                // A right press selects the clip under it and opens its menu (the prototype's `contextmenu`).
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                        val change = event.changes.first()
                        val clip = current.indexAt(change.position.x)?.let { state.clips.getOrNull(it) } ?: continue
                        change.consume()
                        scope.pick(clip.id, clip.key)
                        view.menu.open(MenuTarget.Clip(clip.id))
                    }
                }
            }
            .testTag(CLIP_STRIP_TAG)
    ) {
        clips.forEachIndexed { index, clip ->
            val span = geometry.spans[index]
            // Only the part on screen is composed (ME-218): a zoomed clip outgrows a layout.
            val shown = span.visibleIn(geometry.viewPx) ?: return@forEachIndexed
            val own = SoundTool.mixIn(edits.ownOps(clip.key))
            val start = clips.startOf(index)
            ClipTile(
                clip = clip,
                cells = cells,
                peaks = peaks,
                look = ClipTileLook(
                    selected = clip.id == selectedId,
                    hovered = index == hovered && drag == null,
                    carried = (drag as? StripDrag.Reorder)?.id == clip.id,
                    ownLook = edits.hasOwnLook(clip.key),
                    muted = whole?.mute == true || own?.mute == true
                ),
                modifier = Modifier
                    .offset { IntOffset(shown.left.roundToInt(), 0) }
                    .width(with(density) { shown.width.toDp() })
                    .fillMaxHeight(),
                gainAt = { into -> soundGain(whole?.copy(mute = false), own?.copy(mute = false), start + into, total, into, clip.lengthMs) },
                fullWidthPx = span.width,
                shiftPx = shown.left - span.left
            )
        }
        ClipHandles(geometry, clips, state.durationMs, selectedId, hovered, drag as? StripDrag.Edge)
    }
}
