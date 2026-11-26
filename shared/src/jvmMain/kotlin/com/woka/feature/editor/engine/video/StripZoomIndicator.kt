package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * The whole film as a hairline under the lanes (RD-301; over the strip before), the stretch on
 * screen lit — where in the film the zoomed lanes are, at a glance. **It is a scrollbar too**
 * (the author, 2026-11-04: zooming in was fine, moving sideways was not): a press centres the
 * window there, a drag moves it. Its grip is taller than its line so it can be caught. Drawn
 * only while zoomed.
 */
@Composable
internal fun BoxScope.ZoomOverview(zoom: StripZoom, geometry: StripGeometry) {
    if (!zoom.zoomed || geometry.contentPx <= 0f) return
    val density = LocalDensity.current
    val from = zoom.scrollPx / geometry.contentPx
    val shown = geometry.viewPx / geometry.contentPx
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(OVERVIEW_GRIP)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(zoom) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    zoom.centreOn(down.position.x)
                    horizontalDrag(down.id) { change ->
                        zoom.panOverview(change.position.x - change.previousPosition.x)
                        change.consume()
                    }
                }
            }
            .testTag("editor-strip-overview")
    ) {
        Box(Modifier.fillMaxWidth().height(OVERVIEW_LINE).background(Theme.room.line))
        Box(
            Modifier
                .offset { IntOffset((geometry.viewPx * from).roundToInt(), 0) }
                .width(with(density) { (geometry.viewPx * shown).toDp().coerceAtLeast(OVERVIEW_MIN) })
                .height(OVERVIEW_LINE)
                .background(Theme.room.faint)
        )
    }
}

/** The lit line, the area that catches the pointer, and the narrowest the lit part gets. */
private val OVERVIEW_LINE = 3.dp
internal val OVERVIEW_GRIP = 10.dp
private val OVERVIEW_MIN = 6.dp
