package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import kotlin.math.roundToInt

const val TIMELINE_SKIM_TAG = "editor-timeline-skim"

/**
 * What the tracks share besides the geometry (RD-301): where the pointer is, the moment it
 * skims, the snap guide of a drag in flight, and the menu. [geometry] is the lanes' as last
 * drawn — a plain field, read by pointer handlers at event time, never by a composition.
 */
@Stable
internal class TimelineView {
    var hoverX by mutableStateOf<Float?>(null)
    var skimMs by mutableStateOf<Long?>(null)
    var guideMs by mutableStateOf<Long?>(null)
    val menu = TimelineMenuState()
    var geometry: StripGeometry? = null
}

/**
 * Follows the pointer over the tracks without taking a single event from the lanes under it —
 * the `Initial` pass, nothing consumed. **Skimming moves no decoder** (the author, 2026-11-26):
 * a seek per hover would cost a heavy HEVC's pre-roll (~3.5 s measured, ME-236) in CPU and RAM,
 * so the moment under the pointer is shown as the nearest filmstrip still in a badge
 * ([SkimBadge]) and the canvas keeps the play head's frame. Off while playing, dragging or pressed.
 */
internal fun Modifier.trackPointer(view: TimelineView, state: StripState, labelPx: Float) = pointerInput(view, state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) view.menu.at = change.position
            val geometry = view.geometry
            val x = change.position.x - labelPx
            val over = geometry != null && event.type != PointerEventType.Exit &&
                !event.buttons.areAnyPressed && x in 0f..geometry.viewPx
            view.hoverX = if (over) x else null
            val skims = over && state.drag == null && !state.walk.isPlaying(state.player)
            view.skimMs = if (skims) geometry?.timelineAt(x) else null
        }
    }
}

/** `.skim`: a hairline in the room's text at 35 % under the pointer. */
@Composable
internal fun BoxScope.SkimLine(view: TimelineView, state: StripState) {
    val x = view.hoverX ?: return
    if (view.skimMs == null || state.walk.isPlaying(state.player)) return
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), 0) }
            .width(1.dp)
            .fillMaxHeight()
            .background(Theme.room.text.copy(alpha = 0.35f))
            .testTag(TIMELINE_SKIM_TAG)
    )
}

/** `.snap-guide`: the room's text with a soft glow, where a drag has just snapped. */
@Composable
internal fun BoxScope.SnapGuide(view: TimelineView) {
    val ms = view.guideMs ?: return
    val x = view.geometry?.xOf(ms) ?: return
    Box(
        Modifier.offset { IntOffset((x - 3.dp.toPx()).roundToInt(), 0) }.width(7.dp).fillMaxHeight()
            .background(Theme.room.text.copy(alpha = 0.12f))
    )
    Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(1.dp).fillMaxHeight().background(Theme.room.text))
}
