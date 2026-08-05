package com.woka.core.system

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent

// Windows virtual key codes for the hardware media keys. AWT defines no VK_
// constants for them, so the raw platform codes are what we match on.
private const val VK_MEDIA_NEXT_TRACK = 176
private const val VK_MEDIA_PREV_TRACK = 177
private const val VK_MEDIA_STOP = 178
private const val VK_MEDIA_PLAY_PAUSE = 179

private const val REPEAT_GUARD_MS = 250L

private val RAW_CODE = Regex("rawCode=(\\d+)")

/**
 * Media keys while one of Woka's windows has focus (§4).
 *
 * **Measured, not assumed** (Step 13 Phase 0): on Windows AWT *does* deliver these
 * keys — `keyCode` and `extendedKeyCode` are both 0, and the platform code sits in
 * `rawCode` (179 Play/Pause, 176 Next, 177 Previous). That is why the match is on
 * the raw code rather than `Key.MediaPlayPause`, which never arrives.
 *
 * `rawCode` is a package-private field of [KeyEvent] with no accessor, so it is
 * read back out of `paramString()`. Reflection would need
 * `--add-opens java.desktop/java.awt.event=ALL-UNNAMED` and would break for anyone
 * launching the jar without it; the printed form needs no flags at all.
 *
 * A Toolkit-wide listener rather than per-window wiring, because the fullscreen
 * player is a second `Window` and is covered by the same hook.
 *
 * The scope is what a focus-bound listener gives by construction: keys reach Woka
 * only while one of its windows is focused, so nothing is taken from other
 * players. Keys with Woka in the background would have come from SMTC, which the
 * OS routes on our behalf — that half was built and then dropped when it refused
 * to register a button handler, so this is the whole of the Windows story now.
 */
class AwtMediaKeyControls : SystemMediaControls {
    override var onCommand: (MediaCommand) -> Unit = {}

    private var installed = false
    private var lastCommand: MediaCommand? = null
    private var lastAt = 0L

    private val listener = AWTEventListener { event ->
        if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
            commandFor(rawCodeOf(event))?.let(::emit)
        }
    }

    override fun enable() {
        if (installed) return
        installed = true
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
    }

    override fun disable() {
        if (!installed) return
        installed = false
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }

    /** No OS panel on this path — Phase 2 is what gives those a home. */
    override fun setSession(info: MediaSessionInfo) {}

    override fun setPlayback(state: MediaPlaybackState) {}

    override fun dispose() = disable()

    /**
     * One physical press can surface as more than one KEY_PRESSED, and a
     * play/pause delivered twice cancels itself out. Media keys have no use for
     * key repeat, so a short same-command guard costs nothing and removes the
     * whole class of problem.
     */
    private fun emit(command: MediaCommand) {
        val now = System.currentTimeMillis()
        if (command == lastCommand && now - lastAt < REPEAT_GUARD_MS) return
        lastCommand = command
        lastAt = now
        onCommand(command)
    }
}

private fun rawCodeOf(event: KeyEvent): Int =
    RAW_CODE.find(event.paramString())?.groupValues?.get(1)?.toIntOrNull() ?: 0

private fun commandFor(rawCode: Int): MediaCommand? = when (rawCode) {
    VK_MEDIA_PLAY_PAUSE -> MediaCommand.PlayPause
    VK_MEDIA_NEXT_TRACK -> MediaCommand.Next
    VK_MEDIA_PREV_TRACK -> MediaCommand.Previous
    VK_MEDIA_STOP -> MediaCommand.Stop
    else -> null
}
