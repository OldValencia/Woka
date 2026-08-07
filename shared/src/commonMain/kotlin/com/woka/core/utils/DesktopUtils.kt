package com.woka.core.utils

expect fun openDirectoryPicker(title: String, initialDirectory: String? = null): String?

/** Picks a single existing file, filtered to [extensions] (lower-case, no dot). */
expect fun openFilePicker(
    title: String,
    extensions: List<String>,
    initialDirectory: String? = null
): String?

/**
 * Puts [paths] on the system clipboard **as files**, so Explorer pastes real
 * copies rather than their names. Both the grid's context menu and the viewer's
 * go through here — the transferable used to live in the viewer alone, where the
 * folder feature (commonMain) could not reach it.
 */
expect fun copyFilesToClipboard(paths: List<String>)
