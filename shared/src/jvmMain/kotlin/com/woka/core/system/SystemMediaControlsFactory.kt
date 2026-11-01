package com.woka.core.system

import com.woka.core.system.windows.WindowsSmtcControls

private val osName: String = System.getProperty("os.name").orEmpty().lowercase()

internal val isWindows: Boolean get() = osName.startsWith("windows")
internal val isMac: Boolean get() = osName.startsWith("mac")

/**
 * Picks the backend for this OS (§4). One switch, so adding a platform never
 * touches the viewer.
 *
 * Windows takes System Media Transport Controls on Woka's own window (AP-007): the
 * media keys reach Woka focused or not, and the system's media panel shows what plays.
 * The first attempt hung SMTC on a message-only window, where `add_ButtonPressed`
 * refused with `CO_E_NOT_SUPPORTED`, and shipped a focus-bound AWT key hook instead;
 * SMTC also handles the focused case, so the hook went.
 *
 * macOS has no equivalent shortcut: its media keys are `NSSystemDefined` events,
 * not key events, so AWT never sees them and `MPRemoteCommandCenter` is the only
 * route.
 */
internal fun createSystemMediaControls(): SystemMediaControls = when {
    isWindows -> WindowsSmtcControls()
    else -> NoopSystemMediaControls()
}
