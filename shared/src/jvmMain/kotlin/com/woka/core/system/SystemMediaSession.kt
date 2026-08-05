package com.woka.core.system

import java.awt.EventQueue

/**
 * Process-wide owner of the OS media session (§4).
 *
 * It has to be process-wide, not per-composition: going fullscreen closes
 * `MediaViewerScreen` and opens a separate `Window` over the *same*
 * `VideoPlayerState` (see `PipManager`), so a session held by either composition
 * would die on the handover.
 *
 * Ownership is a **token**. [attach] issues one and makes it current; [detach]
 * does nothing unless the token still is. That makes the handover safe whichever
 * order the two compositions happen to run in — the outgoing screen's detach
 * carries a stale token and is ignored.
 */
object SystemMediaSession {
    private val controls: SystemMediaControls by lazy { createSystemMediaControls() }

    private var currentToken = 0L
    private var nextToken = 1L
    private var handler: (MediaCommand) -> Unit = {}

    @Synchronized
    fun attach(onCommand: (MediaCommand) -> Unit): Long {
        handler = onCommand
        controls.onCommand = ::dispatch
        val token = nextToken++
        currentToken = token
        controls.enable()
        return token
    }

    @Synchronized
    fun setSession(token: Long, info: MediaSessionInfo) {
        if (token != currentToken) return
        controls.setSession(info)
    }

    @Synchronized
    fun setPlayback(token: Long, state: MediaPlaybackState) {
        if (token != currentToken) return
        controls.setPlayback(state)
    }

    @Synchronized
    fun detach(token: Long) {
        if (token != currentToken) return
        currentToken = 0L
        handler = {}
        controls.disable()
    }

    /**
     * Commands arrive on an OS thread — a WinRT thread-pool thread or an ObjC
     * runloop — and every action they trigger writes Compose state. Hop to the
     * EDT first; the snapshot system tolerates off-thread writes but the player's
     * transport was never written to expect them.
     */
    private fun dispatch(command: MediaCommand) {
        val target = handler
        EventQueue.invokeLater { target(command) }
    }
}
