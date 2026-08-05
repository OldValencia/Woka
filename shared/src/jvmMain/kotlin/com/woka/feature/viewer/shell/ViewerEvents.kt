package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A one-shot UI signal: "this happened again, show the HUD".
 *
 * Replaces four `Long` fields that were raised by assigning
 * `System.currentTimeMillis()` and observed through `remember(trigger)`. The clock
 * is too coarse for that: two events inside one tick produced the *same* value, so
 * `remember` never re-keyed and the second HUD silently never appeared —
 * reproducible with a fast double skip, or two zoom clicks in a row. A counter that
 * only ever goes up removes the failure mode rather than narrowing it.
 *
 * [seq] starts at 0, which means "never raised" and is the only value that shows
 * nothing.
 */
@Stable
class ViewerEvent {
    var seq by mutableIntStateOf(0)
        private set

    fun raise() {
        seq++
    }
}

/**
 * True for [durationMs] after each [event] raise, false otherwise — the shared
 * "flash something briefly" timer behind [TransientOverlay] and the volume popover.
 *
 * The volume popover used to do this with a second state field
 * (`volumeTriggerTimer: Int`, used as a boolean) driven by a `LaunchedEffect`
 * living in an entirely different file from its only reader.
 */
@Composable
fun rememberEventFlag(event: ViewerEvent, durationMs: Long): Boolean {
    val seq = event.seq
    var active by remember(seq) { mutableStateOf(seq > 0) }

    LaunchedEffect(seq) {
        if (seq == 0) return@LaunchedEffect
        active = true
        delay(durationMs.milliseconds)
        active = false
    }

    return active
}
