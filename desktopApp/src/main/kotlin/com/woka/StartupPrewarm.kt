package com.woka

import androidx.compose.runtime.withFrameNanos
import org.jetbrains.skiko.Library
import com.woka.core.system.StartupTiming
import com.woka.core.system.windows.D3d12Prewarm
import com.woka.core.data.MediaItem
import com.woka.core.data.isMedia
import com.woka.core.data.mediaItemOrNull
import com.woka.feature.viewer.MediaStartup
import com.woka.core.utils.ClipboardCopies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
 * Tried and dropped, same rig: Skia on a second thread (it loads in 16 ms); a warmed
 * font collection (no change). OpenGL was slower, and Software only faster because it
 * never starts the GPU the video path needs. `-Dsun.java2d.d3d=false` alone was slower
 * too (AWT in 40 ms, the driver load moved into the window); with [D3d12Prewarm]
 * beside it, it is the fastest start measured (ST-001, 2026-11-19).
 */
internal object StartupPrewarm {
    /**
     * A direct file open starts its first media work beside window creation instead of
     * after it (ST-001): a still runs the viewer's first decode steps ([MediaStartup]),
     * a film or track loads FFmpeg. Called after `initKoin`, since a still's decode reads
     * the HDR mode and saved edits Koin installs. Only in the primary instance; a
     * folder-only launch pays nothing.
     */
    fun media(args: Array<String>) {
        if (!System.getProperty("woka.startup.mediaPrewarm", "true").toBoolean()) return
        val file = args.firstOrNull()?.let(::File) ?: return
        if (!file.isMedia) return
        thread(name = "Woka-Media-Prewarm", isDaemon = true) {
            if (!file.isFile) return@thread
            if (mediaItemOrNull(file) is MediaItem.Image) MediaStartup.prepareStill(file) else MediaStartup.prewarm()
        }
    }

    /**
     * Called once the window exists. Two frames later skiko has drawn with its own
     * device, so the prewarmed one has done its job and is let go.
     */
    suspend fun windowShown() {
        StartupTiming.mark("window-bound")
        repeat(2) { withFrameNanos { } }
        D3d12Prewarm.release()
        withContext(Dispatchers.IO) { ClipboardCopies.start() }
    }

    fun start() {
        // With Java2D's Direct3D off (`desktopApp/build.gradle.kts`), nothing else loads
        // the GPU driver before skiko needs it — see [D3d12Prewarm].
        if (System.getProperty("os.name").startsWith("Windows")) {
            thread(name = "Woka-D3D12-Prewarm", isDaemon = true) {
                D3d12Prewarm.run()
            }
        }
        thread(name = "Woka-Prewarm", isDaemon = true) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            StartupTiming.mark("awt")
            Library.load()
            StartupTiming.mark("skia")
        }
    }
}
