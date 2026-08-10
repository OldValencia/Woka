package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long everything must hold still before the chrome goes away. */
private const val CHROME_IDLE_MS = 2500L

/**
 * Whether the fullscreen window's controls are up, and when they were last asked
 * for.
 *
 * Split out of [MediaViewerFullscreenWindow] per the §2 size mandate: the state,
 * the idle clock and the timer that joins them are one thing, and the window had
 * them spread across fifteen lines in the middle of its own wiring.
 */
@Stable
internal class ChromeVisibility {
    var isVisible by mutableStateOf(true)
    var lastInteraction by mutableStateOf(System.currentTimeMillis())

    /** Pointer moved: bring the chrome back and restart the idle clock. */
    fun poke() {
        lastInteraction = System.currentTimeMillis()
        isVisible = true
    }
}

/**
 * Drives a [ChromeVisibility] from the idle clock, the transport and the popovers.
 *
 * A paused video keeps its controls: nothing is being watched, so nothing is being
 * covered. So does an open popover — [hover] is read here rather than in the window
 * because a pointer parked on a control emits no Move events, and without it the
 * chrome would vanish out from under the menu the user is reading.
 */
@Composable
internal fun rememberChromeVisibility(
    videoState: VideoPlayerState?,
    hover: PopoverHoverState
): ChromeVisibility {
    val chrome = remember { ChromeVisibility() }
    LaunchedEffect(chrome.lastInteraction, videoState?.isPlaying, hover.rawTarget) {
        if (videoState?.isPlaying == false || hover.rawTarget != HoverTarget.None) {
            chrome.isVisible = true
        } else {
            delay(CHROME_IDLE_MS.milliseconds)
            chrome.isVisible = false
        }
    }
    return chrome
}
