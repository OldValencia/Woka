package com.woka

import org.jetbrains.skiko.Library
import java.awt.GraphicsEnvironment
import kotlin.concurrent.thread

/**
 * Brings AWT's graphics environment and Skia's native library up on a thread of
 * their own, while `main` runs the single-instance check and Koin.
 *
 * Measured 2026-11-01 on the packaged app with `woka.jsa`, warm, 9 launches a side:
 * the first frame at **~525 ms against ~600 ms** (±5 ms). Everything before
 * `application {}` used to run in turn — single instance 45 ms, Koin 40 ms, then AWT
 * ~150 ms of its own, almost all of it Java2D's Direct3D pipeline. With this the two
 * overlap and `application {}` starts the moment AWT is ready. Purged-cache launches
 * ran 670–790 ms before it, the extra spread over every phase rather than one file.
 *
 * Tried and dropped, same rig: `-Dsun.java2d.d3d=false` (AWT in 40 ms, but Direct3D
 * then starts with the window — 595–620 ms in all); Skia on a second thread (it
 * loads in 16 ms); a warmed font collection (no change). OpenGL was slower, and
 * Software only faster because it never starts the GPU the video path needs.
 */
internal object StartupPrewarm {
    fun start() {
        thread(name = "Woka-Prewarm", isDaemon = true) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            Library.load()
        }
    }
}
