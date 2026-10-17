package com.woka.app.shell.sidebar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Which rows of the sidebar's tree are open, and what each open row holds.
 *
 * **Owned by `Sidebar`, through `remember`, for the life of the window.** It has no
 * scope of its own: every verb suspends and runs in the sidebar's effect or click
 * scope, so nothing here outlives the composition that asked for it.
 *
 * [children] is a cache that is never invalidated in this ticket. A folder created
 * while the app runs shows after a restart; SIDE-007 re-lists open rows on the disk
 * watcher's signal. Collapsing keeps the cache, so re-opening a row costs nothing.
 */
class SidebarTreeState {
    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _children = MutableStateFlow<Map<String, List<SidebarNode>>>(emptyMap())
    val children: StateFlow<Map<String, List<SidebarNode>>> = _children.asStateFlow()

    /** Opens [path], listing it off the main thread the first time. */
    suspend fun expand(path: String) {
        if (path !in _children.value) {
            val listed = withContext(Dispatchers.IO) { listSubfolders(path) }
            _children.update { it + (path to listed) }
        }
        _expanded.update { it + path }
    }

    fun collapse(path: String) {
        _expanded.update { it - path }
    }

    suspend fun toggle(path: String) {
        if (path in _expanded.value) collapse(path) else expand(path)
    }

    /**
     * Opens the way down to [current], so the folder on screen is always a visible,
     * highlighted row. Only opens: a row the user closed elsewhere stays closed.
     */
    suspend fun reveal(current: String?, roots: List<String>) {
        if (current == null) return
        val root = roots.firstOrNull { isWithin(it, current) } ?: return
        ancestorsWithin(root, current).forEach { expand(it) }
    }
}
