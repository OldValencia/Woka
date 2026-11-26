package com.woka.feature.editor.engine.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.core.ui.theme.Theme
import java.awt.Cursor
import kotlin.math.roundToInt

const val TIMELINE_RESIZER_TAG = "editor-timeline-resizer"

/**
 * How tall the timeline is (RD-301): the height kept in `settings.json`, or the drag's while one
 * is in flight, held to 200 … the window less 380 (`timeline.js` `setHeight`).
 *
 * **Written once, on release** — not per pointer event, which would rewrite the settings file
 * sixty times a second for a value nobody reads until the next sitting.
 */
@Stable
internal class TimelineHeight(private val write: (Int) -> Unit) {

    private var dragged by mutableStateOf<Dp?>(null)
    internal var stored: Dp = 0.dp
    internal var limit: Dp = Dp.Infinity

    val value: Dp get() = (dragged ?: stored).coerceIn(TimelineTokens.minHeight, maxOf(TimelineTokens.minHeight, limit))

    fun drag(by: Dp) {
        dragged = (value + by).coerceIn(TimelineTokens.minHeight, maxOf(TimelineTokens.minHeight, limit))
    }

    fun release() {
        val kept = dragged ?: return
        stored = kept
        dragged = null
        write(kept.value.roundToInt())
    }
}

@Composable
internal fun rememberTimelineHeight(): TimelineHeight {
    val repository = remember { AppDependencies.settingsRepository }
    val settings by repository.settings.collectAsState()
    val height = remember(repository) { TimelineHeight(repository::setEditorTimelineHeight) }
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Plain fields, read by `value` in this same composition: nothing observes them change.
    height.stored = settings.editorTimelineHeight.dp
    height.limit = if (window > 0.dp) window - TimelineTokens.windowReserve else Dp.Infinity
    return height
}

/**
 * The top edge's grip (`.timeline-resizer`): an 8 dp band the pointer catches, a 34 dp line in
 * it that widens to 56 and turns the room's orange under the pointer. Up makes the timeline taller.
 */
@Composable
internal fun TimelineResizer(height: TimelineHeight, modifier: Modifier = Modifier) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val width by animateDpAsState(if (hovered) TimelineTokens.gripHoverWidth else TimelineTokens.gripWidth)
    val colour by animateColorAsState(if (hovered) Theme.room.accent else Theme.room.faint.copy(alpha = 0.6f))
    val current by rememberUpdatedState(height)
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(TimelineTokens.resizerHeight)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { current.release() },
                    onDragCancel = { current.release() }
                ) { change, dy ->
                    change.consume()
                    current.drag(with(density) { (-dy).toDp() })
                }
            }
            .testTag(TIMELINE_RESIZER_TAG),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(width, TimelineTokens.gripHeight)
                .background(colour, RoundedCornerShape(TimelineTokens.gripHeight))
        )
    }
}
