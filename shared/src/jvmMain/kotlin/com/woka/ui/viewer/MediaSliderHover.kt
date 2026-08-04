package com.woka.ui.viewer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

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
