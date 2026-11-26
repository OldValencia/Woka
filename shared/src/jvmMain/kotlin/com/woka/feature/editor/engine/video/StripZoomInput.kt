package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlin.math.abs
import kotlin.math.pow

/** One sideways wheel notch pans this far. */
private const val PAN_PX = 60f

/**
 * What moves the strip's zoom from outside `StripZoom` (ME-218): the wheel over the tracks, and
 * the play head pulling the window along while it plays. Split from it when the overview's
 * scrollbar pushed that file past the size mandate.
 */

/**
 * The wheel over the tracks (RD-301, the prototype's tooltips): **with Ctrl** up and down zoom
 * around the pointer; sideways — or with Shift, which Windows turns into sideways — pans. A
 * plain wheel is left unconsumed for the lanes' own vertical scroll, which a tall Markup lane
 * needs now the timeline has the height the author gives it. [startPx] is the label column the
 * lanes start after.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.stripZoomWheel(zoom: StripZoom, layout: StripLayout, startPx: Float) =
    onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.first()
        val delta = change.scrollDelta
        when {
            abs(delta.x) > abs(delta.y) -> zoom.pan(delta.x * PAN_PX, layout)
            event.keyboardModifiers.isCtrlPressed -> zoom.zoomAround(change.position.x - startPx, ZOOM_NOTCH.pow(-delta.y), layout)
            else -> return@onPointerEvent
        }
        change.consume()
    }

/** While it plays, the zoomed strip scrolls to keep the play head in view (ME-218). */
@Composable
internal fun FollowPlayHead(
    player: VideoPlayerState,
    walk: ClipWalk,
    clips: List<TrimClip>,
    zoom: StripZoom,
    layout: StripLayout
) {
    if (!zoom.zoomed) return
    val current by rememberUpdatedState(clips)
    val currentLayout by rememberUpdatedState(layout)
    LaunchedEffect(player, walk, zoom) {
        snapshotFlow { walk.timelineMs(current, player.currentPositionMs) to walk.isPlaying(player) }
            .collect { (ms, playing) ->
                if (playing) zoom.follow(currentLayout(zoom.scale, zoom.scrollPx).xOf(ms), currentLayout)
            }
    }
}

/**
 * An edge drag's layout (ME-218): the live list at the scale of the press, scrolled so the
 * dragged edge sits under the pointer — the neighbours visibly move instead of the strip
 * holding still and jumping on release. The scroll it borrowed is handed to the zoom when
 * the drag ends, so the view stays where the hand left it.
 */
internal class EdgeCarry {

    private var shift = 0f

    fun layout(edge: StripDrag.Edge?, layout: StripLayout, zoom: StripZoom): StripGeometry {
        if (edge == null) return layout(zoom.scale, zoom.scrollPx)
        val scale = zoom.scale ?: edge.scale.takeIf { it > 0f }
        val unshifted = layout(scale, zoom.scrollPx)
        shift = edge.x - edge.handle.x(unshifted)
        return layout(scale, zoom.scrollPx - shift)
    }

    fun release(edge: StripDrag.Edge?, zoom: StripZoom, layout: StripLayout) {
        if (edge != null || shift == 0f) return
        zoom.pan(-shift, layout)
        shift = 0f
    }
}

