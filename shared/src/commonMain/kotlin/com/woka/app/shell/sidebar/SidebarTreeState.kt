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
 * [children] is a cache. The open folder's row and its parent's are re-listed when
 * the disk changes under them ([relist], SIDE-007); other rows refresh when they are
 * next opened after a restart. Collapsing keeps the cache, so re-opening costs nothing.
 */
class SidebarTreeState {
    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _children = MutableStateFlow<Map<String, List<SidebarNode>>>(emptyMap())
    val children: StateFlow<Map<String, List<SidebarNode>>> = _children.asStateFlow()

    /** Bumped on every disk change seen, so rows not in the cache — the roots — recount. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

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

    /**
     * Re-reads a row already listed, because the disk under it changed (SIDE-007).
     * Matched by `samePath`, since the folder on screen and the cached row can spell
     * the same folder differently.
     */
    suspend fun relist(path: String) {
        _version.update { it + 1 }
        val key = _children.value.keys.firstOrNull { samePath(it, path) } ?: return
        val listed = withContext(Dispatchers.IO) { listSubfolders(key) }
        _children.update { it + (key to listed) }
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
