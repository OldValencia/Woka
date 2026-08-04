package com.woka.system

private val osName: String = System.getProperty("os.name").orEmpty().lowercase()

internal val isWindows: Boolean get() = osName.startsWith("windows")
internal val isMac: Boolean get() = osName.startsWith("mac")

/**
 * Picks the backend for this OS (§4). One switch, so adding a platform never
 * touches the viewer.
 *
 * Windows takes the focus-bound AWT path. The OS Now Playing session was built
 * on top of it and then dropped: SMTC registered, showed the title and the
 * poster frame, and refused every attempt to register the `ButtonPressed`
 * handler with `CO_E_NOT_SUPPORTED` — a panel that displays but does not
 * respond is worse than no panel. The plan doc records what was tried.
 *
 * macOS has no equivalent shortcut: its media keys are `NSSystemDefined` events,
 * not key events, so AWT never sees them and `MPRemoteCommandCenter` is the only
 * route.
 */
internal fun createSystemMediaControls(): SystemMediaControls = when {
    isWindows -> AwtMediaKeyControls()
    else -> NoopSystemMediaControls()
}
