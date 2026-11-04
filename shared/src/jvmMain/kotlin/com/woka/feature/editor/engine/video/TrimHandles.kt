package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.math.roundToInt

/** `.handle`: 13 px in the mockup, 10 here because the reach, not the paint, is the target. */
private val HANDLE = 10.dp

/** `.film .dim`: what is outside the handles is still in the file, so it dims, not vanishes. */
private val DIMMED = Color.Black.copy(alpha = 0.55f)

/**
 * With Trim open: what was trimmed off either end, drawn as film and dimmed, and the
 * handles — the timeline's outer ends and both edges of the selected clip (ME-210;
 * mockup state 2; reported 2026-11-02).
 *
 * The trimmed stretches are laid out by `StripGeometry` from [trimmedEnds], so an outer
 * handle stays where it was let go. While [drag] moves an edge, the layout is the one it
 * began on, the handle follows the pointer, and the stretch it has taken is shaded.
 */
@Composable
internal fun TrimmedEnds(
    geometry: StripGeometry,
    clips: List<TrimClip>,
    cells: FilmstripCells?,
    durationMs: Long,
    selectedId: String?,
    drag: StripDrag.Edge?
) {
    if (clips.isEmpty()) return
    val first = clips.first()
    val last = clips.last()
    if (geometry.lead.width > 0f) Trimmed(TrimClip("lead", 0L, first.fromMs), geometry.lead, cells, geometry.viewPx)
    if (geometry.tail.width > 0f) Trimmed(TrimClip("tail", last.toMs, durationMs), geometry.tail, cells, geometry.viewPx)
    handlesOf(clips, selectedId).forEach { handle ->
        val moved = if (drag?.handle == handle) drag.deltaMs * geometry.pxPerMs else 0f
        val edge = handle.x(geometry)
        // Inward is positive for a start and negative for an end; only inward is cut away.
        if (handle.leading && moved > 0f) Shade(edge, moved)
        if (!handle.leading && moved < 0f) Shade(edge + moved, -moved)
        Handle(edge + moved, handle.leading)
    }
}


@Composable
private fun Shade(leftPx: Float, widthPx: Float) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(leftPx.roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .background(DIMMED)
    )
}

@Composable
private fun Trimmed(range: TrimClip, span: ClipSpan, cells: FilmstripCells?, viewPx: Float) {
    val density = LocalDensity.current
    val shown = span.visibleIn(viewPx) ?: return
    val modifier = Modifier
        .offset { IntOffset(shown.left.roundToInt(), 0) }
        .width(with(density) { shown.width.toDp() })
        .fillMaxHeight()
    ClipTile(range, cells, false, false, false, modifier = modifier, fullWidthPx = span.width, shiftPx = shown.left - span.left)
    Box(modifier.clip(RoundedCornerShape(CLIP_RADIUS)).background(DIMMED))
}

@Composable
private fun Handle(edgePx: Float, leading: Boolean) {
    Box(
        modifier = Modifier
            .offset { IntOffset((if (leading) edgePx else edgePx - HANDLE.toPx()).roundToInt(), 0) }
            .width(HANDLE)
            .fillMaxHeight()
            .background(Theme.colors.accentEditor)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 4.dp, vertical = 18.dp)
            .background(Theme.colors.onAccent.copy(alpha = 0.55f))
    )
}
