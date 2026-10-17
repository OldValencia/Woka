package com.woka.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent

class TopBarAccessoriesState {
    /**
     * Beside the Back button, on the left — where a screen puts the control that
     * *undoes* rather than the one that proceeds (ME-107). The editor's Revert is the
     * first tenant: red, destructive, and deliberately at the end the hand leaves by.
     *
     * Two disciplines share these slots and both are correct for who uses them.
     * A **screen** claims one by key ([setLeading]): `AnimatedContent` keeps the
     * outgoing screen composed for the length of the slide, so its `onDispose` runs
     * *after* the incoming screen has claimed the slot, and without the key it would
     * null out its successor's accessory. An **overlay** — the viewer, the editor —
     * assigns directly and restores what it found, because it means to give the slot
     * back to whichever screen is still underneath it.
     *
     * Only [leadingAccessory] has a keyed claim, because only Home claims one. A
     * screen that wants another slot mirrors [setLeading] for it.
     */
    var leadingAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var centerAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)

    /**
     * Beside [centerAccessory], never instead of it (ME-173).
     *
     * The editor wanted a control next to the viewer's pill and there was no way to add
     * one: taking the centre removes the viewer's zoom, eye and Edit, and *wrapping* the
     * pill in a new parent re-composes it, so its enter animation runs again on a control
     * that never left. A second slot leaves the accessory itself untouched — the same
     * lambda, in the same place — and `EditorTopBarUiTest` asserts that identity.
     */
    var centerTrailingAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var rightAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var backActionOverride by mutableStateOf<(() -> Unit)?>(null)

    private var leadingKey by mutableStateOf<String?>(null)

    fun setLeading(key: String, content: @Composable () -> Unit) {
        leadingKey = key
        leadingAccessory = content
    }

    fun clearLeading(key: String) {
        if (leadingKey == key) {
            leadingKey = null
            leadingAccessory = null
        }
    }

    /**
     * The folder's toolbar controls (SIDE-003) claim [rightAccessory] the way Home
     * claimed the leading slot, and for the same reason: the outgoing folder's
     * `onDispose` runs after the incoming one has claimed it.
     */
    private var rightKey by mutableStateOf<String?>(null)

    fun setRight(key: String, content: @Composable () -> Unit) {
        rightKey = key
        rightAccessory = content
    }

    fun clearRight(key: String) {
        if (rightKey == key) {
            rightKey = null
            rightAccessory = null
        }
    }
}

/**
 * Whether the toolbar is too narrow for every control (SIDE-003). Provided by
 * `GlobalTopBar` from its own width, so a screen's accessory can drop its widest
 * items at the 800 px minimum window instead of running under the path.
 */
val LocalToolbarCompact = compositionLocalOf { false }

val LocalTopBarAccessories = compositionLocalOf { TopBarAccessoriesState() }

/**
 * App-wide keyboard shortcut hook. A screen (e.g. the media viewer) registers a
 * handler here; the shell invokes it in the key-event *preview* (tunneling)
 * phase at the very root, so shortcuts fire before any focused button and win
 * regardless of what currently holds focus. Returning true consumes the event,
 * which is what stops Space/arrows from also activating a focused button.
 */
class GlobalShortcutState {
    var onPreviewKey by mutableStateOf<((KeyEvent) -> Boolean)?>(null)
}

val LocalGlobalShortcuts = compositionLocalOf { GlobalShortcutState() }
