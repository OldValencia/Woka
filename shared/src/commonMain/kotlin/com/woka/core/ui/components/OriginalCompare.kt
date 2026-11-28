package com.woka.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A press shorter than this is a click; longer, a hold (`initComparison`'s 200 ms, RD-502). */
private const val HOLD_MS = 200L

/** `#compare` and `#viewer-original`'s tip: the same words on the same control in both places. */
const val ORIGINAL_TOOLTIP = "Show the original. Click again to return, or press and hold. Shortcut: hold \\"

/**
 * Whether the picture is shown as shot rather than as edited — the viewer's and the
 * editor's `Original`, one control in two places (the prototype's `initComparison`, RD-502).
 *
 * **A click toggles, a hold peeks.** A hold — the button past [HOLD_MS], or `\` — shows the
 * original and on release returns to whatever was shown *before* it, so a hold over a
 * comparison already on leaves it on. The editor's button was hold-only until RD-502: a
 * comparison that ends the moment the pointer lifts cannot be looked at while a slider moves.
 *
 * One object, not a flag and its setter, because three parties read it on each side — the
 * button, the picture that swaps, and the label over it.
 */
@Stable
class OriginalCompare {

    var active by mutableStateOf(false)
        private set

    /** What [active] was when the current hold began; null while nothing is held. */
    private var beforeHold: Boolean? = null

    fun toggle() {
        active = !active
    }

    /** Off, and any hold forgotten: the file changed, the editor opened, Esc. */
    fun end() {
        active = false
        beforeHold = null
    }

    /** Esc's half: ends a comparison that is on, and answers whether there was one to end. */
    fun endIfActive(): Boolean {
        if (!active) return false
        end()
        return true
    }

    /**
     * A hold begins ([down]) or ends. A key repeats its down while held, so a second down
     * inside one hold is ignored rather than overwriting what the release restores.
     */
    fun hold(down: Boolean) {
        if (down) {
            if (beforeHold != null) return
            beforeHold = active
            active = true
        } else {
            beforeHold?.let { active = it }
            beforeHold = null
        }
    }
}

/**
 * `\` held: its press holds, its release lets go. Answers whether the event was `\`'s, so the
 * caller consumes both halves; the repeats of a held key are absorbed by [OriginalCompare.hold].
 * The key types `\` on the Russian layout too, so it needs no `layoutFreeKey`.
 */
fun OriginalCompare.holdOnKey(event: KeyEvent): Boolean {
    if (event.key != Key.Backslash) return false
    when (event.type) {
        KeyEventType.KeyDown -> hold(true)
        KeyEventType.KeyUp -> hold(false)
    }
    return true
}

/**
 * The button's gesture: a click toggles [compare], a press held past [HOLD_MS] holds it.
 * A cancel — the pointer dragged off — ends a hold as a release does and is never a click,
 * so nobody is left on an original they did not ask for.
 */
fun Modifier.originalPress(compare: OriginalCompare): Modifier = pointerInput(compare) {
    coroutineScope {
        detectTapGestures(
            onPress = {
                var held = false
                val timer = launch {
                    delay(HOLD_MS)
                    held = true
                    compare.hold(true)
                }
                val released = tryAwaitRelease()
                timer.cancel()
                when {
                    held -> compare.hold(false)
                    released -> compare.toggle()
                }
            }
        )
    }
}
