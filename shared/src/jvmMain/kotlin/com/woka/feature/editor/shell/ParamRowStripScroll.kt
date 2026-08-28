package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The two ways a preview strip is driven besides dragging it: a chevron at each end
 * and the mouse wheel (asked for 2026-08-28).
 *
 * Fourteen squares in one line are about three panels wide, so the line has to be
 * scrollable and — more to the point — has to *look* scrollable. A bare
 * `horizontalScroll` on desktop offers no affordance at all: there is no scrollbar,
 * and a wheel over it scrolls nothing because the wheel is vertical.
 */

/**
 * A chevron at one end of the strip.
 *
 * **Dimmed at the end of its travel rather than removed.** A button that vanishes
 * when it runs out of room takes its width with it, and the whole line shifts under
 * the cursor at the moment the user arrives at the edge.
 */
@Composable
internal fun StripScrollButton(back: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Theme.viewer.onScrim else Theme.viewer.onScrimBorder
    Box(
        modifier = Modifier
            .size(CHEVRON_BOX)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(if (back) "param-strip-back" else "param-strip-forward"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                if (back) Icons.Static.ChevronLeft else Icons.Static.ChevronRightMac
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(CHEVRON_GLYPH)
        )
    }
}

/**
 * Turns a vertical wheel into horizontal travel, by [step] pixels a notch.
 *
 * A desktop wheel reports on `scrollDelta.y`, and a `horizontalScroll` reads `x` — so
 * without this the wheel over the strip does nothing at all while the page behind it
 * scrolls instead. The event is consumed for that second reason as much as the first.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.wheelScrollsStrip(
    state: ScrollState,
    scope: CoroutineScope,
    step: Float
): Modifier = onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.firstOrNull() ?: return@onPointerEvent
    // Either axis: a trackpad's horizontal swipe already arrives on x, and adding
    // them means one expression serves both without asking which device it was.
    val notches = change.scrollDelta.y + change.scrollDelta.x
    if (notches == 0f) return@onPointerEvent
    scope.launch { state.scrollBy(notches * step) }
    change.consume()
}

private val CHEVRON_BOX = 20.dp
private val CHEVRON_GLYPH = 11.dp
