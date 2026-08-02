package com.woka.ui.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.togglePlayPause
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pointer gestures for the media area:
 * - `Ctrl + Scroll` zooms by ±1% in Zoom Mode; in Preset Mode it is disabled and
 *   fires [onScalingDisabledAttempt] instead (never auto-switches modes).
 * - `Ctrl + hold Right Mouse Button + drag` pans the frame when it overflows.
 * - Tap toggles play/pause (video); double-tap toggles fullscreen (video) or
 *   Fit ↔ 100% (image).
 */
@Composable
fun Modifier.mediaViewerGestures(
    sizeMode: SizeMode,
    offsetX: Float,
    offsetY: Float,
    containerSize: IntSize,
    nativeSize: IntSize,
    videoState: VideoPlayerState?,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onScalingDisabledAttempt: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit
): Modifier {
    val curMode by rememberUpdatedState(sizeMode)
    val curOffsetX by rememberUpdatedState(offsetX)
    val curOffsetY by rememberUpdatedState(offsetY)
    val curContainer by rememberUpdatedState(containerSize)
    val curNative by rememberUpdatedState(nativeSize)

    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var panning = false
                var lastPos = Offset.Zero
                var lastScrollNanos = 0L
                var scrollAccel = 1
                while (true) {
                    // Initial pass so Ctrl+RMB pan is intercepted before the child
                    // ContextMenuArea; non-Ctrl events are left unconsumed and still
                    // reach the context menu / tap detector on the Main pass.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.first()
                    val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed

                    if (event.type == PointerEventType.Scroll && isCtrl) {
                        val delta = event.changes.first().scrollDelta.y
                        when (val mode = curMode) {
                            is SizeMode.Zoom -> {
                                // Acceleration: a slow single tick stays ±1 for fine control,
                                // but scrolling fast (events < 140ms apart) ramps the step up so
                                // you can cross the range quickly. Magnitude also counts (trackpads).
                                val now = System.nanoTime()
                                val fast = now - lastScrollNanos < 140_000_000L
                                lastScrollNanos = now
                                scrollAccel = if (fast) (scrollAccel + 2).coerceAtMost(24) else 1
                                val magnitude = abs(delta).roundToInt().coerceAtLeast(1)
                                val dir = if (delta > 0) -1 else 1
                                val step = magnitude * scrollAccel * dir
                                onSizeModeChange(SizeMode.Zoom(SizeMode.clampPercent(mode.percent + step)))
                            }
                            is SizeMode.Preset -> onScalingDisabledAttempt()
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    val canPan = contentOverflows(curMode, curContainer, curNative)
                    if (isCtrl && event.buttons.isSecondaryPressed && canPan) {
                        if (!panning) {
                            panning = true
                            lastPos = change.position
                        } else {
                            val d = change.position - lastPos
                            lastPos = change.position
                            onOffsetChange(curOffsetX + d.x, curOffsetY + d.y)
                        }
                        change.consume()
                    } else {
                        panning = false
                    }
                }
            }
        }
        .pointerInput(videoState) {
            detectTapGestures(
                onDoubleTap = { tap ->
                    if (videoState != null) {
                        onToggleFullscreen()
                    } else {
                        val mode = curMode
                        // Double-tap only ever zooms (never drops back to presets). From a
                        // preset it enters Zoom at 200%; in Zoom it doubles (capped at 1000%),
                        // and a tap at 1000% resets to 100%.
                        val next = when (mode) {
                            is SizeMode.Preset -> SizeMode.Zoom(200)
                            is SizeMode.Zoom ->
                                if (mode.percent >= SizeMode.MAX_PERCENT) SizeMode.Zoom(SizeMode.DEFAULT_PERCENT)
                                else SizeMode.Zoom((mode.percent * 2).coerceAtMost(SizeMode.MAX_PERCENT))
                        }
                        val nextPercent = (next as SizeMode.Zoom).percent
                        if (nextPercent <= SizeMode.DEFAULT_PERCENT) {
                            onOffsetChange(0f, 0f)
                        } else {
                            // Keep the clicked content point under the cursor while zooming in.
                            val container = curContainer
                            val s0 = computeGraphicsScale(mode, container, curNative)
                            val s1 = computeGraphicsScale(next, container, curNative)
                            val ratio = if (s0 != 0f) s1 / s0 else 1f
                            val ctrX = container.width / 2f
                            val ctrY = container.height / 2f
                            val t1x = (tap.x - ctrX) * (1f - ratio) + ratio * curOffsetX
                            val t1y = (tap.y - ctrY) * (1f - ratio) + ratio * curOffsetY
                            onOffsetChange(t1x, t1y)
                        }
                        onSizeModeChange(next)
                    }
                },
                onTap = {
                    if (videoState != null) {
                        videoState.togglePlayPause()
                        onPlayPause()
                    }
                }
            )
        }
}
