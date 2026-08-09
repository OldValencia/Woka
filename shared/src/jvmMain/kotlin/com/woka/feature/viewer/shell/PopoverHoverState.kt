package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Which piece of hover-driven chrome the pointer is engaging.
 *
 * [Seeker] has no popover of its own to open — it is here because this enum is also
 * what holds the chrome open, and a pointer resting on the seek bar reading a
 * preview is the clearest case there is of the UI being in use. Without it the bar
 * hid itself out from under the cursor after the auto-hide delay, since a
 * *stationary* pointer emits no Move events to keep the timer alive.
 */
enum class HoverTarget { None, Volume, Subtitle, Settings, Seeker }

/**
 * Single source of truth for the Volume, Subtitle and Settings hover popovers.
 *
 * Replaces the previous pair of mutually-resetting raw booleans: each one's
 * effect cleared the other's state, so crossing the icon boundary (where Compose
 * emits Enter/Exit on the overlays as they appear/disappear) made the two effects
 * fight and the popover flickered open/closed forever.
 *
 * [rawTarget] is what the pointer is over right now; [visibleTarget] is what is
 * actually shown, held open by a short grace delay so moving between an icon and
 * its own popover doesn't flicker it closed.
 */
@Stable
class PopoverHoverState {
    var rawTarget by mutableStateOf(HoverTarget.None)
        private set
    var visibleTarget by mutableStateOf(HoverTarget.None)

    /**
     * Enter/Exit hook for an icon or a popover body. Exit only clears when [target]
     * still owns the pointer, so a stale Exit arriving *after* the next Enter can't
     * reopen the ping-pong.
     */
    fun setHovered(target: HoverTarget, hovered: Boolean) {
        rawTarget = when {
            hovered -> target
            rawTarget == target -> HoverTarget.None
            else -> rawTarget
        }
    }
}

/** Drives [PopoverHoverState.visibleTarget] from the raw target with a grace delay. */
@Composable
fun PopoverHoverEffect(state: PopoverHoverState) {
    LaunchedEffect(state.rawTarget) {
        if (state.rawTarget != HoverTarget.None) {
            state.visibleTarget = state.rawTarget
        } else {
            delay(300.milliseconds)
            state.visibleTarget = HoverTarget.None
        }
    }
}
