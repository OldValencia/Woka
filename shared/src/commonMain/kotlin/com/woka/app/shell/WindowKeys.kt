package com.woka.app.shell

import androidx.compose.ui.input.key.KeyEvent

/**
 * The window's own key hook: `main.kt` hands every key event the window receives to
 * [handle], and `MainShell` installs the [handler] that answers it.
 *
 * **Why the window and not a node** (2026-10-17). Ctrl+F was first caught by an
 * `onPreviewKeyEvent` on the shell's root box, and did nothing from the running app:
 * a node sees key events only while focus sits somewhere beneath it, and a click on
 * the grid or the sidebar can leave focus nowhere. The window receives every key
 * regardless of focus, which is what an app-wide shortcut needs. Screen shortcuts
 * stay on `GlobalShortcutState`, where they belong to whatever is focused.
 */
object WindowKeys {
    var handler: ((KeyEvent) -> Boolean)? = null

    fun handle(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}
