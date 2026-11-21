package com.woka.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.video.engine.quietFfmpegLogs
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avformat.avformat_version
import java.io.File

/**
 * What a file opened from the command line needs, started while the window is built
 * (ST-001). Called after `initKoin`, so the HDR mode and saved edits a decode reads are
 * already installed.
 */
object MediaStartup {

    /** The viewer's first answers for a still: its size, its colour and its preview. */
    class Still(val path: String, val size: IntSize, val color: StillColorInfo, val preview: ImageBitmap?)

    @Volatile
    private var still: Still? = null

    /** A film or a track: the FFmpeg libraries its first frame or tag read will need. */
    fun prewarm() {
        quietFfmpegLogs()
        avformat_version()
    }

    /**
     * Runs the viewer's first decode steps for a still — the same [MediaThumbnails] calls,
     * earlier. Measured 2026-11-19: the preview was ready 2 ms after it was asked for, then
     * waited ~100 ms for the event thread to finish the first frame; taken from here it is
     * painted in that frame.
     */
    fun prepareStill(file: File) {
        val item = MediaItem.Image(file)
        still = runBlocking {
            Still(
                file.absolutePath,
                MediaThumbnails.sourceSize(item),
                MediaThumbnails.stillColor(item),
                MediaThumbnails.loadPreview(item)
            )
        }
    }

    /** The prepared answers for [item], once; null when not ready yet or for another file. */
    @Synchronized
    fun takeStill(item: MediaItem, frameIndex: Int): Still? {
        val ready = still?.takeIf { frameIndex == 0 && item is MediaItem.Image && it.path == item.file.absolutePath }
        if (ready != null) still = null
        return ready
    }
}
