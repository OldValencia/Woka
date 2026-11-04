package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlin.math.abs
import kotlin.math.pow

/** One sideways wheel notch pans this far. */
private const val PAN_PX = 60f

/**
 * What moves the strip's zoom from outside `StripZoom` (ME-218): the wheel over the strip and
 * over the band, and the play head pulling the window along while it plays. Split from it
 * when the overview's scrollbar pushed that file past the size mandate.
 */

/**
 * The wheel over the strip: up and down zoom around the pointer, sideways — or with Shift,
 * which Windows turns into sideways — pans. Consumed, so the editor's canvas does not zoom
 * the picture as well.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.stripZoomWheel(zoom: StripZoom, layout: StripLayout) =
    onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.first()
        val delta = change.scrollDelta
        if (abs(delta.x) > abs(delta.y)) zoom.pan(delta.x * PAN_PX, layout)
        else zoom.zoomAround(change.position.x, ZOOM_NOTCH.pow(-delta.y), layout)
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

/** The wheel over the band outside the strip — what the strip did not consume. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.bandZoomWheel(zoom: StripZoom) = onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.first()
    if (change.isConsumed) return@onPointerEvent
    zoom.wheelAtPlayHead(change.scrollDelta.y)
    change.consume()
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

