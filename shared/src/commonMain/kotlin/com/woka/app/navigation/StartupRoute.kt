package com.woka.app.navigation

import com.woka.app.AppDependencies
import com.woka.app.shell.sidebar.isWithin
import com.woka.core.data.PinnedSession
import com.woka.core.data.isMedia
import java.io.File

/**
 * The launch route (roadmap § Architecture & CLI Routing): what `Woka.exe [path]`
 * opens on.
 *
 * - a directory → its Folder View
 * - a media file → the Viewer, over the parent's Folder View
 * - any other file → the parent's Folder View
 * - no argument, or a path that is not there → where the user was ([last]), then
 *   the first linked folder, then Welcome
 *
 * **Nothing sits under the launch screen any more** (SIDE-002). Home used to, so
 * Back from a launched folder reached it; with Home gone the sidebar is that way
 * out, and Back starts empty as it does in any freshly opened window. A launched
 * folder carries `offerPin`, which is what makes it offer to link itself.
 *
 * [last] is honoured only inside a linked root: a folder opened from Explorer and
 * never linked, or one whose root was unlinked since, is not where the library
 * reopens.
 */
fun startupStack(
    args: List<String>,
    last: PinnedSession? = null,
    linked: List<String> = emptyList()
): List<Screen> {
    val target = launchTarget(args) ?: return listOf(restoredScreen(last, linked))

    if (target.isDirectory) {
        return listOf(Screen.Folder(target.absolutePath, offerPin = true))
    }

    val parent = target.parentFile ?: return listOf(restoredScreen(last, linked))
    return listOf(
        Screen.Folder(
            path = parent.absolutePath,
            openViewerForId = if (target.isMedia) target.absolutePath else null,
            offerPin = true
        )
    )
}

private fun restoredScreen(last: PinnedSession?, linked: List<String>): Screen {
    val roots = linked.filter { File(it).isDirectory }
    val lastPath = last?.path
    if (lastPath != null && File(lastPath).isDirectory && roots.any { isWithin(it, lastPath) }) {
        return Screen.Folder(lastPath)
    }
    return roots.firstOrNull()?.let { Screen.Folder(it) } ?: Screen.Welcome
}

private fun sanitizeArg(arg: String): String {
    var s = arg.trim().removeSurrounding("\"")
    while (s.endsWith("%*") || s.endsWith("%1")) {
        s = s.removeSuffix("%*").removeSuffix("%1").trim().removeSurrounding("\"")
    }
    return s
}

/**
 * Windows hands an unquoted path with spaces over as several arguments, so a first
 * argument that is not on disk is retried as the whole line joined back together.
 * Shell file association placeholders (%*, %1) and surrounding quotes are stripped.
 */
private fun launchTarget(args: List<String>): File? {
    val cleanArgs = args
        .map { sanitizeArg(it) }
        .filter { it.isNotEmpty() }

    val first = cleanArgs.firstOrNull().orEmpty()
    if (first.isEmpty()) return null

    val direct = File(first)
    if (direct.exists()) return direct

    if (cleanArgs.size < 2) return null
    val joined = File(cleanArgs.joinToString(" ").trim().removeSurrounding("\""))
    return if (joined.exists()) joined else null
}

/**
 * Routes an external path (from CLI arguments or IPC forwarding) into the running
 * application's router, generating a fresh nonce so the target folder or viewer
 * command is re-executed even if already selected.
 */
fun openExternalPath(path: String) {
    val screens = startupStack(listOf(path))
    val target = screens.firstOrNull() ?: return
    val screen = when (target) {
        is Screen.Folder -> target.copy(nonce = System.currentTimeMillis())
        else -> target
    }
    AppDependencies.router.navigateTo(screen)
}
