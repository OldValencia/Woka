package com.woka

import java.awt.Desktop

/**
 * macOS hands a double-clicked file to the running app as an `APP_OPEN_FILE` event
 * rather than as a launch argument; [open] receives its absolute path.
 */
internal fun registerMacOpenFileHandler(open: (String) -> Unit) {
    try {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else return
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event ->
                event.files.firstOrNull()?.let { open(it.absolutePath) }
            }
        }
    } catch (_: Throwable) {
        // Not supported on this desktop environment
    }
}
