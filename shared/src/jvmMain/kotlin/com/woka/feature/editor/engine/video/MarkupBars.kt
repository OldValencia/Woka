package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.label
import com.woka.feature.editor.tools.markup.MarkupWindowsParam

/**
 * The Markup lane's model (RD-301): one bar per shape, over its window — **or across the whole
 * film when it has none**, which is what an untimed shape is (a watermark, ME-263) — packed into
 * rows so two shapes on screen at once do not cover each other. Pure, so the packing and the
 * moves are asserted without a window (`MarkupLaneRowsTest`).
 *
 * **It reads and writes the op the canvas edits** — the document's last `markup` op, the one in
 * flight first. Markup is the whole film's since RD-302, so a bar's move lands in the shared
 * layer whatever clip is selected, as a drag on the canvas does.
 */
@Immutable
internal data class MarkupBar(val id: String, val label: String, val window: TimeWindow, val row: Int)

/** Which part of a bar a press took: its start, its end, or the whole of it. */
internal enum class BarPart { Start, End, Body }

/** The shortest window a drag leaves: a tenth of a second, a clip's own floor (`MIN_CLIP_MS`). */
private const val MIN_HOLD_MS = 100L

internal fun markupOpIn(document: EditDocument): ToolOp? =
    document.pending?.takeIf { it.toolId == MarkupTool.manifest.id }
        ?: document.opsOf(MarkupTool.manifest.id).lastOrNull()

internal fun markupBarsIn(document: EditDocument, totalMs: Long): List<MarkupBar> {
    val params = markupOpIn(document)?.params ?: return emptyList()
    val windows = params[MarkupWindowsParam]
    val shapes = params[MarkupTool.Shapes]
    val spans = shapes.map { windows[it.id] ?: TimeWindow(0L, totalMs) }
    val rows = laneRows(spans)
    return shapes.mapIndexed { index, shape -> MarkupBar(shape.id, shape.label, spans[index], rows[index]) }
}

/** Each window in the first row whose last window has ended by its start, earliest start first. */
internal fun laneRows(windows: List<TimeWindow>): List<Int> {
    val ends = mutableListOf<Long>()
    val rows = IntArray(windows.size)
    windows.indices.sortedBy { windows[it].atMs }.forEach { index ->
        val window = windows[index]
        val row = ends.indexOfFirst { it <= window.atMs }.takeIf { it >= 0 } ?: ends.size.also { ends.add(0L) }
        ends[row] = window.endMs
        rows[index] = row
    }
    return rows.toList()
}

internal fun markupLaneHeight(bars: List<MarkupBar>): Dp {
    val rows = (bars.maxOfOrNull { it.row } ?: 0) + 1
    return TimelineTokens.markupBarHeight * rows + TimelineTokens.markupRowGap * (rows - 1)
}

/**
 * [window] with [part] moved [deltaMs] inside a film [totalMs] long, snapped to [points] within
 * [reachMs] (negative: Alt, nothing snaps) — and the point it snapped to, for the guide.
 */
internal fun moveWindow(
    window: TimeWindow,
    part: BarPart,
    deltaMs: Long,
    totalMs: Long,
    points: List<Long>,
    reachMs: Long
): Pair<TimeWindow, Long?> = when (part) {
    BarPart.Body -> {
        val last = (totalMs - window.holdMs).coerceAtLeast(0L)
        val moved = snapBody((window.atMs + deltaMs).coerceIn(0L, last), window.holdMs, points, reachMs)
        window.copy(atMs = moved.atMs.coerceIn(0L, last)) to moved.snapped
    }
    BarPart.Start -> {
        val end = window.endMs
        val latest = (end - MIN_HOLD_MS).coerceAtLeast(0L)
        val hit = snapTo((window.atMs + deltaMs).coerceIn(0L, latest), points, reachMs)?.takeIf { it in 0L..latest }
        val at = hit ?: (window.atMs + deltaMs).coerceIn(0L, latest)
        window.copy(atMs = at, holdMs = end - at) to hit
    }
    BarPart.End -> {
        val earliest = (window.atMs + MIN_HOLD_MS).coerceAtMost(totalMs)
        val raw = (window.endMs + deltaMs).coerceIn(earliest, totalMs.coerceAtLeast(earliest))
        val hit = snapTo(raw, points, reachMs)?.takeIf { it in earliest..totalMs }
        window.copy(holdMs = (hit ?: raw) - window.atMs) to hit
    }
}

/** [op] with shape [id] on screen for [window] — the write `Appears at` and `Holds for` make. */
internal fun withWindow(op: ToolOp, id: String, window: TimeWindow): ToolOp =
    ToolOp(op.toolId, op.params.with(MarkupWindowsParam, op.params[MarkupWindowsParam] + (id to window)))

/** [op] without shape [id] and its window — the menu's `Remove`. */
internal fun withoutShape(op: ToolOp, id: String): ToolOp = ToolOp(
    op.toolId,
    op.params
        .with(MarkupTool.Shapes, op.params[MarkupTool.Shapes].filterNot { it.id == id })
        .with(MarkupWindowsParam, op.params[MarkupWindowsParam] - id)
)
