package com.woka.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import com.woka.core.data.ViewLayout
import com.woka.core.ui.components.zoomedIn
import com.woka.core.ui.components.zoomedOut

/**
 * The two gestures Home's empty space answers to: Ctrl+wheel for density, and
 * right-click for the background menu. Both are what `FolderGrid` already does on
 * the other screen; they live in their own file here only because `HomeScreen` has
 * no room for them under the §2 size mandate.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.homeGridInput(
    layout: ViewLayout,
    onLayoutChange: (ViewLayout) -> Unit,
    onBackgroundMenu: (IntOffset) -> Unit
): Modifier = this
    .onPointerEvent(PointerEventType.Press) { event ->
        if (event.button != PointerButton.Secondary) return@onPointerEvent
        val change = event.changes.first()
        // A card that opened its own menu has already consumed the press. The Main
        // pass runs child-first, so by the time it arrives here the question is
        // only whether the click landed on nothing.
        if (change.isConsumed) return@onPointerEvent
        onBackgroundMenu(IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2))
        change.consume()
    }
    // Initial pass, so the grid does not also scroll while the wheel is zooming.
    .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
        if (!event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        val next = when {
            delta < 0 -> layout.zoomedIn()
            delta > 0 -> layout.zoomedOut()
            else -> layout
        }
        if (next != layout) onLayoutChange(next)
        event.changes.forEach { it.consume() }
    }
