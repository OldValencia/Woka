package com.woka.app.shell.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import com.woka.app.AppDependencies
import com.woka.app.collectField
import com.woka.app.shell.EditorPresence
import com.woka.core.data.WindowCorner
import kotlinx.coroutines.launch

/**
 * Now Playing while the sidebar is hidden (AP-006, `now-playing.html` `.bub`): the
 * slot's source in one corner of the window, dragged between corners and snapping
 * to the nearest, the corner kept in settings.
 *
 * Fills the window but takes no input outside the pill: the pill is laid out at the
 * anchor by a `layout` modifier, so its pointer handling covers its own bounds only.
 */
@Composable
fun NowPlayingBubble() {
    val nowPlaying = remember { AppDependencies.nowPlaying }
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val sidebarVisible by settingsRepo.collectField { it.sidebarVisible }
    val corner by settingsRepo.collectField { it.nowPlayingCorner }
    val source = nowPlaying.source
    // Kept through the fade-out: the slot is already empty while the bubble leaves.
    var shownSource by remember { mutableStateOf(source) }
    if (source != null) shownSource = source
    val eligible = source != null &&
        !EditorPresence.isOpen &&
        nowPlaying.shownInViewer != source.path
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() }) {
        val density = LocalDensity.current
        val container = IntSize(constraints.maxWidth, constraints.maxHeight)
        val bubble = with(density) { NowPlayingTokens.BubbleSize.roundToPx() }
        val edge = with(density) { NowPlayingTokens.BubbleEdge.roundToPx() }
        val top = with(density) { NowPlayingTokens.BubbleTop.roundToPx() }
        val placeOf = { at: WindowCorner -> cornerOffset(at, container, bubble, edge, top) }
        val anchor = remember { Animatable(placeOf(corner), IntOffset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val inset = with(density) { (NowPlayingTokens.BubbleSize - NowPlayingTokens.BubbleCover).toPx() / 2 }
        val cover = with(density) { NowPlayingTokens.BubbleCover.toPx() }
        val disc = { Rect(layerOrigin + anchor.value.toOffset() + Offset(inset, inset), Size(cover, cover)) }
        val flight = rememberCoverFlight(sidebarVisible, eligible, disc)
        val visible = eligible && !sidebarVisible && !flight.active
        LaunchedEffect(visible) { if (visible) flight.landed = false }

        LaunchedEffect(corner, container) {
            if (!dragging) anchor.snapTo(placeOf(corner))
        }
        if (dragging) {
            val corners = WindowCorner.entries
            val near = nearestCorner(centreOf(anchor.value, bubble), container)
            CornerTargets(corners.map(placeOf), corners.indexOf(near), bubble)
        }

        // The layer is the whole window, so the scale pivots on the anchor, not the window's middle.
        val centre = centreOf(anchor.value, bubble)
        val pivot = TransformOrigin(centre.x / container.width, centre.y / container.height)
        val fade = tween<Float>(NowPlayingTokens.FadeMs)
        AnimatedVisibility(
            visible = visible,
            // A cover that has just flown in is the bubble arriving; opening the sidebar
            // hands it to the flight back. Neither fades on top of the ghost (AP-008).
            enter = if (flight.landed) EnterTransition.None else fadeIn(fade) + scaleIn(fade, 0.85f, pivot),
            exit = if (sidebarVisible) ExitTransition.None else fadeOut(fade) + scaleOut(fade, 0.85f, pivot)
        ) {
            val held = shownSource ?: return@AnimatedVisibility
            val toRight = corner.isLeft
            val pill = Modifier
                .layout { measurable, outer ->
                    val placeable = measurable.measure(Constraints())
                    layout(outer.maxWidth, outer.maxHeight) {
                        val at = anchor.value
                        val x = if (toRight) at.x else at.x + bubble - placeable.width
                        placeable.place(x, at.y)
                    }
                }
                .pointerInput(container) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val target = nearestCorner(centreOf(anchor.value, bubble), container)
                            scope.launch {
                                anchor.animateTo(placeOf(target), tween(NowPlayingTokens.SnapMs))
                                dragging = false
                                settingsRepo.setNowPlayingCorner(target)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                anchor.animateTo(placeOf(corner), tween(NowPlayingTokens.SnapMs))
                                dragging = false
                            }
                        }
                    ) { change, amount ->
                        change.consume()
                        scope.launch { anchor.snapTo(anchor.value + amount.round()) }
                    }
                }
            NowPlayingPill(held, toRight, pill)
        }
        shownSource?.let { FlightGhost(flight, it, layerOrigin) }
    }
}

private fun centreOf(anchor: IntOffset, bubble: Int): Offset =
    Offset(anchor.x + bubble / 2f, anchor.y + bubble / 2f)
