package com.woka.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class Screen {
    data object Home : Screen()

    /**
     * [offerPin] marks a folder the app was *launched* into rather than navigated
     * to, so Folder View can offer to put it on Home — see `startupStack`.
     */
    data class Folder(
        val path: String,
        val highlightFile: String? = null,
        val openViewerForId: String? = null,
        val nonce: Long = 0L,
        val offerPin: Boolean = false
    ) : Screen()
}

class Router {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /**
     * Replaces the whole stack with the launch route. Called once, before the
     * first composition; an empty list is ignored so a bad argument cannot leave
     * the app with no screen at all.
     */
    fun start(screens: List<Screen>) {
        if (screens.isNotEmpty()) _stack.value = screens
    }

    /**
     * Pushes [screen], and disarms the screen it is pushed on top of.
     *
     * See [withoutArrivalCommand]: without this, returning to that entry re-runs a
     * command that was carried out long ago.
     */
    fun navigateTo(screen: Screen) {
        _stack.update { current ->
            if (current.isEmpty()) {
                listOf(screen)
            } else {
                current.dropLast(1) + current.last().withoutArrivalCommand() + screen
            }
        }
    }

    fun replaceTop(screen: Screen) {
        _stack.update { if (it.isNotEmpty()) it.dropLast(1) + screen else listOf(screen) }
    }

    fun pop() {
        _stack.update { if (it.size > 1) it.dropLast(1) else it }
    }

    fun popTo(screen: Screen) {
        _stack.update { current ->
            val index = current.indexOf(screen)
            if (index != -1) current.take(index + 1) else current
        }
    }
}

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
