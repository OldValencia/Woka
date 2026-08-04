package com.woka.system

/** Used where the OS has no integration yet, and while a backend is being built. */
class NoopSystemMediaControls : SystemMediaControls {
    override var onCommand: (MediaCommand) -> Unit = {}
    override fun enable() {}
    override fun setSession(info: MediaSessionInfo) {}
    override fun setPlayback(state: MediaPlaybackState) {}
    override fun disable() {}
    override fun dispose() {}
}
