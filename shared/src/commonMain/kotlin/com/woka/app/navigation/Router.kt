package com.woka.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
     *
     * [openViewerForId] is a command — "open the viewer on this file once you are
     * shown" — and [nonce] makes it fire again when the same folder is re-entered.
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
 * Which screen the window shows. **One screen, no history** (SIDE-003).
 *
 * It was a back stack, then a back stack with Forward for a day (SIDE-002). Both went
 * when the sidebar became the navigation: the tree, the path and Backspace say where
 * to go, and history only said where you had been. A folder keeps its scroll for the
 * run instead (`FolderScrollMemory`), which is what Back was mostly used to recover.
 *
 * [stack] stays a list of exactly one entry rather than becoming a single value: the
 * launch route and `PipManager` speak in it, and a list that cannot grow costs those
 * call sites nothing. The disarming of arrival commands that history needed went
 * with it — nothing can replay an entry that no longer exists.
 */
class Router {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Welcome))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /**
     * The folder the app was last launched into ([Screen.Folder.offerPin]): from
     * Explorer, the command line or a dropped file. Up and Backspace treat it as a root
     * while it is not linked, so its sub-folders can be left upward but never past it
     * (author, 2026-11-20 — before, they had no way up at all).
     */
    private val _launchRoot = MutableStateFlow<String?>(null)
    val launchRoot: StateFlow<String?> = _launchRoot.asStateFlow()

    /**
     * Shows the last of [screens]. Called with the launch route before the first
     * composition, and when the folder on screen stops existing (unlink). An empty
     * list is ignored so a bad argument cannot leave the app with no screen.
     */
    fun start(screens: List<Screen>) {
        val screen = screens.lastOrNull() ?: return
        show(screen)
    }

    fun navigateTo(screen: Screen) {
        show(screen)
    }

    /** The roots Up stops at: the linked folders, then the [launchRoot]. */
    fun upRoots(linked: List<String>): List<String> = linked + listOfNotNull(_launchRoot.value)

    private fun show(screen: Screen) {
        if (screen is Screen.Folder && screen.offerPin) _launchRoot.value = screen.path
        _stack.value = listOf(screen)
    }

    /** The same as [navigateTo]; kept as a name for the callers that mean "this, again". */
    fun replaceTop(screen: Screen) {
        show(screen)
    }
}
