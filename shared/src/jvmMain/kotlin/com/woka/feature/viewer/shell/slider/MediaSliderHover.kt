package com.woka.feature.viewer.shell.slider

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A bar is a control, so the pointer says so — both bars, from one line.
 *
 * Deliberately not folded into [sliderHover]: that reports *where* the pointer is,
 * and a caller that wanted the position without claiming to be clickable would have
 * no way to ask.
 */
internal fun Modifier.sliderCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Hover tracking for the seek bar: the pointer's x over the track, or null once
 * it leaves.
 *
 * One `pointerInput(Unit)` loop rather than three `onPointerEvent` modifiers:
 * every Move recomposes [MediaSlider], and an event-keyed node would be torn
 * down and rebuilt on each of those recompositions.
 */
internal fun Modifier.sliderHover(onHover: (Float?) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter,
                    PointerEventType.Move -> onHover(event.changes.firstOrNull()?.position?.x)
                    PointerEventType.Exit -> onHover(null)
                    else -> {}
                }
            }
        }
    }
