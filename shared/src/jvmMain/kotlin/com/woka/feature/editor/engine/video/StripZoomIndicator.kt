package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * That the strip is zoomed, said where it can be seen (the author, 2026-11-04: "show beside
 * the strip that a zoom has happened"). Two signs: this pill, which reads `8×` and puts the
 * whole film back on a click, and [ZoomOverview] along the strip's top edge.
 */
@Composable
internal fun ZoomPill(zoom: StripZoom) {
    if (!zoom.zoomed) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Theme.viewer.onScrim.copy(alpha = 0.14f))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { zoom.fit() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .testTag("editor-strip-zoom"),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            "⌕ ${zoomLabel(zoom.zoom)}",
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/**
 * The whole film as a hairline over the strip, the stretch on screen lit — where in the
 * film the zoomed strip is, at a glance. **It is a scrollbar too** (the author, 2026-11-04:
 * zooming in was fine, moving sideways was not): a press centres the window there, a drag
 * moves it. Its grip is taller than its line so it can be caught. Drawn only while zoomed.
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
        Box(Modifier.fillMaxWidth().height(OVERVIEW_LINE).background(Theme.viewer.onScrim.copy(alpha = 0.25f)))
        Box(
            Modifier
                .offset { IntOffset((geometry.viewPx * from).roundToInt(), 0) }
                .width(with(density) { (geometry.viewPx * shown).toDp().coerceAtLeast(OVERVIEW_MIN) })
                .height(OVERVIEW_LINE)
                .background(Theme.colors.accentEditor)
        )
    }
}

/** The lit line, the area that catches the pointer, and the narrowest the lit part gets. */
private val OVERVIEW_LINE = 3.dp
private val OVERVIEW_GRIP = 10.dp
private val OVERVIEW_MIN = 6.dp

/** `2×`, `8×`, `140×` — whole numbers past ten, one decimal under. */
private fun zoomLabel(zoom: Float): String =
    if (zoom >= 10f) "${zoom.roundToInt()}×" else "${(zoom * 10f).roundToInt() / 10f}×"
