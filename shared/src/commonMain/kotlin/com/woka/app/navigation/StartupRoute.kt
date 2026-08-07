package com.woka.app.navigation

import com.woka.core.data.isMedia
import java.io.File

/**
 * The launch route (roadmap § Architecture & CLI Routing): what `Woka.exe [path]`
 * opens on.
 *
 * - no argument, or a path that is not there → Home
 * - a directory → its Folder View
 * - a media file → the Viewer, over the parent's Folder View
 * - any other file → the parent's Folder View
 *
 * [Screen.Home] stays under the launch screen so Back and the dock's Home tab work
 * from a launch exactly as they do from a click. The launched folder carries
 * `offerPin`, which is what makes closing the viewer offer to put it on Home.
 */
fun startupStack(args: List<String>): List<Screen> {
    val target = launchTarget(args) ?: return listOf(Screen.Home)

    if (target.isDirectory) {
        return listOf(Screen.Home, Screen.Folder(target.absolutePath, offerPin = true))
    }

    val parent = target.parentFile ?: return listOf(Screen.Home)
    return listOf(
        Screen.Home,
        Screen.Folder(
            path = parent.absolutePath,
            openViewerForId = if (target.isMedia) target.absolutePath else null,
            offerPin = true
        )
    )
}

/**
 * Windows hands an unquoted path with spaces over as several arguments, so a first
 * argument that is not on disk is retried as the whole line joined back together.
 */
private fun launchTarget(args: List<String>): File? {
    val first = args.firstOrNull()?.trim().orEmpty()
    if (first.isEmpty()) return null

    val direct = File(first)
    if (direct.exists()) return direct

    if (args.size < 2) return null
    val joined = File(args.joinToString(" ").trim())
    return if (joined.exists()) joined else null
}
