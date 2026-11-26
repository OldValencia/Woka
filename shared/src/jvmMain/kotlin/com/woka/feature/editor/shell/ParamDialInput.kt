package com.woka.feature.editor.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import com.woka.feature.editor.core.FloatParam
import kotlin.math.abs
import kotlin.math.roundToInt

/** Within this of zero a drag lands on zero (`dialValue` in `instruments.js`, 0.9°). */
private const val ZERO_SNAP = 0.9f

/** Shift's step, as the prototype's (`instruments.js`: 5° with Shift, 0.5° without). */
private const val SHIFT_STEP = 5f

/**
 * The dial's pointer and keys, split from `ParamDial` along its input/drawing seam (RD-205).
 * [level] is a function because a gesture outlives the composition it started in: the value a
 * drag starts from must be the one on screen now, not the one when the detector was built.
 */

/**
 * A sideways drag moves the scale with the pointer — right turns the value down — 8 dp to the
 * degree, in tenths. One preview per change and one settle on release, so the whole drag is
 * one undo step; a drag that changed nothing settles nothing.
 */
internal suspend fun PointerInputScope.dialDrag(
    level: () -> ScrubLevel,
    focus: FocusRequester,
    onDragging: (Boolean) -> Unit
) = awaitEachGesture {
    // From where the press landed, not from where the slop was crossed: the scale follows
    // the pointer exactly, as the prototype's `start - (x - startX) / 8` does.
    // `detectHorizontalDragGestures` measures from the slop and lagged by ~2°.
    val down = awaitFirstDown(requireUnconsumed = false)
    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        ?: return@awaitEachGesture
    focus.requestFocus()
    val start = level().value
    var written = start
    var moved = false
    fun follow(x: Float) {
        val current = level()
        val next = dragged(current.spec, start - (x - down.position.x) / DIAL_DEGREE.toPx())
        if (next == written) return
        written = next
        moved = true
        current.write(next, settled = false)
    }
    onDragging(true)
    follow(drag.position.x)
    horizontalDrag(drag.id) { change ->
        change.consume()
        follow(change.position.x)
    }
    onDragging(false)
    // Settled whenever anything was previewed, even a drag that came back to where it began:
    // a preview left unsettled is work in flight the next edit would fold in.
    if (moved) level().settle()
}

/** ← / → by the spec's step, Shift for five degrees, Home to zero — each one settled. */
internal fun dialKey(event: KeyEvent, level: ScrubLevel): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val spec = level.spec
    val step = if (event.isShiftPressed) SHIFT_STEP else spec.step ?: 1f
    val next = when (event.key) {
        Key.DirectionRight -> level.value + step
        Key.DirectionLeft -> level.value - step
        Key.MoveHome -> spec.detent
        else -> return false
    }
    level.write(tenths(spec, next), settled = true)
    return true
}

/** [raw] in range and in tenths of a degree, landing on zero when near it. */
internal fun dragged(spec: FloatParam, raw: Float): Float {
    val value = tenths(spec, raw)
    return if (abs(value - spec.detent) < ZERO_SNAP) spec.detent else value
}

private fun tenths(spec: FloatParam, raw: Float): Float =
    (raw.coerceIn(spec.range) * 10f).roundToInt() / 10f
