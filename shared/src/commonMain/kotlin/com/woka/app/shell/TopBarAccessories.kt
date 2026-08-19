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
     */
    var leadingAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var centerAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var rightAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var backActionOverride by mutableStateOf<(() -> Unit)?>(null)
}

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
