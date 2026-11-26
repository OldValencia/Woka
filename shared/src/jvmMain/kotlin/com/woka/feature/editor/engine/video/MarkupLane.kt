package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.math.abs
import kotlin.math.roundToInt

const val MARKUP_LANE_TAG = "editor-markup-lane"

/**
 * The Markup lane (`.text-row`, RD-301; the author, 2026-11-26): a bar per shape on the lanes'
 * geometry. **A drag gives a shape its window** — the body moves it, an end changes when it
 * appears or how long it holds — snapping to the film's ends, the play head and the joins with
 * a guide, Alt for none; one history step a drag, none if it came back. A click selects the
 * shape on the canvas and opens Markup; the clip scope stays as it is. It replaced `MarkedSpan`,
 * which showed the selected shape's window and nothing else.
 *
 * One pointer handler for the whole lane, in its fixed coordinates: a bar moves under a drag,
 * so a handler on the bar itself would read positions in a frame that slides with it.
 */
@Composable
internal fun MarkupLane(
    bars: List<MarkupBar>,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    document: EditDocument,
    geometry: StripGeometry,
    view: TimelineView,
    state: StripState
) {
    // The handlers are keyed on nothing and read this at event time: the scope is rebuilt every
    // composition, and a handler keyed on it restarted — and dropped the drag — at the first preview.
    val now by rememberUpdatedState(LaneNow(bars, geometry, document, clips, edits))
    var badge by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val layout = now.geometry
                    val clips = now.clips
                    val (bar, part) = barAt(now.bars, layout, down.position, this) ?: return@awaitEachGesture
                    val op = markupOpIn(now.document) ?: return@awaitEachGesture
                    down.consume()
                    val total = clips.totalMs()
                    val points = snapPoints(clips, state.walk.timelineMs(clips, state.player.currentPositionMs))
                    val reach = (TimelineTokens.snapReach.toPx() / layout.pxPerMs).toLong()
                    var last = bar.window
                    var moved = false
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val dx = change.position.x - down.position.x
                            if (!moved && abs(dx) < viewConfiguration.touchSlop) return@drag
                            moved = true
                            val free = currentEvent.keyboardModifiers.isAltPressed
                            val (next, guide) = moveWindow(bar.window, part, (dx / layout.pxPerMs).toLong(), total, points, if (free) -1L else reach)
                            view.guideMs = guide
                            badge = (if (part == BarPart.Body) startLabel(next.atMs) else lengthLabel(next.holdMs)) to change.position
                            if (next != last) now.edits.preview(withWindow(op, bar.id, next))
                            last = next
                        }
                    } finally {
                        view.guideMs = null
                        badge = null
                        if (moved && last != bar.window) now.edits.commit() else if (moved) now.edits.cancel()
                    }
                    if (!moved) {
                        now.edits.selectShape(bar.id)
                        now.edits.openTool(MarkupTool.manifest.id)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                        val change = event.changes.first()
                        val (bar, _) = barAt(now.bars, now.geometry, change.position, this) ?: continue
                        change.consume()
                        now.edits.selectShape(bar.id)
                        now.edits.openTool(MarkupTool.manifest.id)
                        view.menu.open(MenuTarget.Shape(bar.id))
                    }
                }
            }
            .testTag(MARKUP_LANE_TAG)
    ) {
        bars.forEach { bar ->
            val left = geometry.xOf(bar.window.atMs)
            val span = ClipSpan(left, geometry.xOf(bar.window.endMs) - left)
            val shown = span.visibleIn(geometry.viewPx) ?: return@forEach
            MarkupBarTile(bar, shown, span, selected = bar.id == edits.selectedShape)
        }
        badge?.let { (text, at) ->
            BasicText(
                text,
                Modifier
                    .offset { IntOffset(at.x.roundToInt() - 20.dp.roundToPx(), at.y.roundToInt() - 38.dp.roundToPx()) }
                    .background(Theme.room.tip, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = TimelineTokens.badge.copy(color = Theme.room.tipText)
            )
        }
    }
}

/** What the lane's handlers read at event time. */
private class LaneNow(
    val bars: List<MarkupBar>,
    val geometry: StripGeometry,
    val document: EditDocument,
    val clips: List<TrimClip>,
    val edits: EditorTimelineScope
)
