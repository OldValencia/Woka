package com.woka.app.shell.sidebar

import com.woka.core.data.isMedia
import java.io.File

/**
 * One folder as the sidebar's tree draws it.
 *
 * [mediaCount] is the media directly inside the folder, not below it. A recursive
 * count would make expanding one row cost a walk of the whole subtree, and the
 * sidebar expands rows on every navigation (`SidebarTreeState.reveal`).
 */
data class SidebarNode(
    val path: String,
    val name: String,
    val mediaCount: Int,
    /** Whether the row gets a chevron. Read from the same listing as the count. */
    val hasChildren: Boolean
)

/**
 * The visible sub-folders of [path], in the order a person reads them: by name,
 * case ignored. Files and hidden directories are left out, and so is a folder the
 * process may not list — `listFiles` answers null for it rather than throwing.
 */
fun listSubfolders(path: String): List<SidebarNode> =
    File(path).listFiles().orEmpty()
        .filter { it.isVisibleFolder() }
        .sortedBy { it.name.lowercase() }
        .map { nodeOf(it.absolutePath) }

/** One folder as a row: a single listing answers both the count and the chevron. */
fun nodeOf(path: String): SidebarNode {
    val folder = File(path)
    val entries = folder.listFiles().orEmpty()
    return SidebarNode(
        path = folder.absolutePath,
        name = folder.name.ifEmpty { folder.absolutePath },
        mediaCount = entries.count { it.isFile && it.isMedia },
        hasChildren = entries.any { it.isVisibleFolder() }
    )
}

private fun File.isVisibleFolder(): Boolean = isDirectory && !isHidden

/**
 * The directories that must be expanded for [path] to be visible under [root]:
 * from [root] down to [path]'s parent, both ends included. Empty when [path] is
 * [root] itself or lies outside it.
 *
 * Compared through `File.absoluteFile.normalize()`, never as raw strings: the same
 * folder reaches here as `D:\A` from the picker and `D:/A/` from a command line,
 * and a string prefix would also call `D:\Animals` a child of `D:\A`.
 */
fun ancestorsWithin(root: String, path: String): List<String> {
    val top = normalised(root)
    val chain = generateSequence(normalised(path).parentFile) { it.parentFile }.toList()
    val index = chain.indexOf(top)
    if (index < 0) return emptyList()
    return chain.take(index + 1).reversed().map { it.path }
}

/**
 * The second line of a pinned row: where the folder sits, so two pins both named
 * `Season 1` can be told apart. The parent's path relative to the root that holds
 * it, the root's own name first — `Movies › Series › Andor` for
 * `…/Movies/Series/Andor/Season 1`. Outside every root, the parent's name alone.
 */
fun pinnedParentLabel(path: String, roots: List<String>): String {
    val parent = normalised(path).parentFile ?: return ""
    val root = roots.map(::normalised)
        .firstOrNull { ancestorsWithin(it.path, path).isNotEmpty() }
        ?: return parent.name
    val below = parent.relativeTo(root).path
    val segments = listOf(root.name) + below.split(File.separatorChar).filter { it.isNotEmpty() }
    return segments.joinToString(" › ")
}

/** Whether two spellings name one folder — the row on screen and the path routed to. */
fun samePath(a: String, b: String?): Boolean = b != null && normalised(a) == normalised(b)

/** Whether [path] is [root] or anywhere beneath it. */
fun isWithin(root: String, path: String): Boolean =
    normalised(root) == normalised(path) || ancestorsWithin(root, path).isNotEmpty()

private fun normalised(path: String): File = File(path).absoluteFile.normalize()
