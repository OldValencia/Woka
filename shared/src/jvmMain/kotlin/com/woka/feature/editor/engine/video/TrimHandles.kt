package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import java.awt.Cursor
import kotlin.math.roundToInt

/** `.film .dim`: what an edge being dragged is taking away. */
private val DIMMED = Color.Black.copy(alpha = 0.55f)

/** `.clip:hover .clip-handle { opacity: .65 }`; selected, 1; otherwise hidden but still caught. */
private const val HOVER_HANDLE = 0.65f

/**
 * The handles (`.clip-handle`, RD-301) — the film's two outer ends and both edges of the selected
 * clip, **in every tab** — and, while an outer end is dragged, what was trimmed off either end.
 *
 * A selected clip's handles show; an outer end on a clip that is not selected shows under the
 * pointer and is caught either way. While [drag] moves an edge the handle follows the pointer and
 * the stretch it has taken is shaded. The trimmed stretches are laid out by `StripGeometry` from
 * `trimmedEnds`, so an outer handle stays where it was let go (reported 2026-11-02).
 */
@Composable
internal fun ClipHandles(
    geometry: StripGeometry,
    clips: List<TrimClip>,
    durationMs: Long,
    selectedId: String?,
    hovered: Int?,
    drag: StripDrag.Edge?
) {
    if (clips.isEmpty()) return
    if (geometry.lead.width > 0f && clips.first().fromMs > 0L) Trimmed(geometry.lead, geometry.viewPx)
    if (geometry.tail.width > 0f && clips.last().toMs < durationMs) Trimmed(geometry.tail, geometry.viewPx)
    handlesOf(clips, selectedId).forEach { handle ->
        val moved = if (drag?.handle == handle) drag.deltaMs * geometry.pxPerMs else 0f
        val edge = handle.x(geometry)
        // Inward is positive for a start and negative for an end; only inward is cut away.
        if (handle.leading && moved > 0f) Shade(edge, moved)
        if (!handle.leading && moved < 0f) Shade(edge + moved, -moved)
        val alpha = when {
            clips[handle.index].id == selectedId || drag?.handle == handle -> 1f
            handle.index == hovered -> HOVER_HANDLE
            else -> 0f
        }
        if (alpha > 0f) Handle(edge + moved, handle.leading, alpha)
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

/**
 * A trimmed-off stretch, beside the film during an outer-end drag: the clip's well **with no
 * frames** (the author, 2026-11-05) — it is not in the result, and pictures there read as if it
 * were — dimmed, as the prototype's `.trim-ghost`.
 */
@Composable
private fun Trimmed(span: ClipSpan, viewPx: Float) {
    val density = LocalDensity.current
    val shown = span.visibleIn(viewPx) ?: return
    Box(
        Modifier
            .offset { IntOffset(shown.left.roundToInt(), 0) }
            .width(with(density) { shown.width.toDp() })
            .fillMaxHeight()
            .alpha(0.5f)
            .background(Theme.room.well2, RoundedCornerShape(CLIP_RADIUS))
    )
}

@Composable
private fun Handle(edgePx: Float, leading: Boolean, alpha: Float) {
    val shape = if (leading) RoundedCornerShape(topStart = CLIP_RADIUS, bottomStart = CLIP_RADIUS)
    else RoundedCornerShape(topEnd = CLIP_RADIUS, bottomEnd = CLIP_RADIUS)
    Box(
        modifier = Modifier
            .offset { IntOffset((if (leading) edgePx else edgePx - TimelineTokens.handleWidth.toPx()).roundToInt(), 0) }
            .width(TimelineTokens.handleWidth)
            .fillMaxHeight()
            .alpha(alpha)
            .background(Theme.room.accent, shape)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(TimelineTokens.gripLineWidth, TimelineTokens.gripLineHeight)
                .background(Theme.room.onAccent.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
        )
    }
}
