package com.woka.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class Screen {
    /**
     * The first run: nothing is linked, so there is no folder to show. Replaced Home
     * in SIDE-002 — the window always shows a folder once one exists, and the folders
     * themselves live in the sidebar.
     */
    data object Welcome : Screen()

    /**
     * [offerPin] marks a folder the app was *launched* into rather than navigated
     * to, so Folder View can offer to link it — see `startupStack`.
     */
    data class Folder(
        val path: String,
        val highlightFile: String? = null,
        val openViewerForId: String? = null,
        val nonce: Long = 0L,
        val offerPin: Boolean = false
    ) : Screen()
}

/**
 * The navigation history: [stack] is Back's side, [forward] is what Back has popped
 * and Forward can replay — a browser's two lists, cleared the way a browser clears
 * them (SIDE-002). Forward was added with the sidebar: a click there is an ordinary
 * navigation, and without Forward a mistaken Back lost the place for good.
 */
class Router {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Welcome))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _forward = MutableStateFlow<List<Screen>>(emptyList())
    val forward: StateFlow<List<Screen>> = _forward.asStateFlow()

    /**
     * Replaces the whole history with [screens]. Called with the launch route before
     * the first composition, and when the folder on screen stops existing (unlink).
     * An empty list is ignored so a bad argument cannot leave the app with no screen.
     */
    fun start(screens: List<Screen>) {
        if (screens.isEmpty()) return
        _stack.value = screens
        _forward.value = emptyList()
    }

    /**
     * Pushes [screen], and disarms the screen it is pushed on top of.
     *
     * See [withoutArrivalCommand]: without this, returning to that entry re-runs a
     * command that was carried out long ago. A new branch of history makes the old
     * Forward list meaningless, so it is cleared.
     */
    fun navigateTo(screen: Screen) {
        _stack.update { current -> current.disarmTop() + screen }
        _forward.value = emptyList()
    }

    fun replaceTop(screen: Screen) {
        _stack.update { if (it.isNotEmpty()) it.dropLast(1) + screen else listOf(screen) }
    }

    fun pop() {
        val current = _stack.value
        if (current.size < 2) return
        _forward.update { it + current.last().withoutArrivalCommand() }
        _stack.value = current.dropLast(1)
    }

    /** Replays the screen the last Back left, if there is one. */
    fun goForward() {
        val next = _forward.value.lastOrNull() ?: return
        _forward.update { it.dropLast(1) }
        _stack.update { current -> current.disarmTop() + next }
    }
}

private fun List<Screen>.disarmTop(): List<Screen> =
    if (isEmpty()) this else dropLast(1) + last().withoutArrivalCommand()

/**
 * The same folder, with nothing left to do on arrival.
 *
 * [Screen.Folder.openViewerForId] is a *command* — "open the viewer on this file
 * once you are shown" — and [Screen.Folder.nonce] exists only to make that command
 * fire again when the same folder is re-entered. Both describe a moment, not a
 * screen, and leaving them in a history entry means history replays them: return to
 * the main window from fullscreen (which writes the pair), walk into another folder,
 * press Back, and the video you finished with opens on top of the folder you asked
 * for. The same holds for a file opened from the command line.
 *
 * Disarmed on the way out rather than when the viewer closes: rewriting the entry
 * that is on screen would swap `ScreenHost`'s `AnimatedContent` key under a live
 * folder, rebuilding its view model and losing the scroll position the viewer just
 * handed back.
 */
private fun Screen.withoutArrivalCommand(): Screen = when {
    this !is Screen.Folder -> this
    openViewerForId == null && nonce == 0L -> this
    else -> copy(openViewerForId = null, nonce = 0L)
}
