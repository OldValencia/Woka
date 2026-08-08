package com.woka.core.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** What came of an export: how many pictures were written, and how many would not. */
data class FrameExportResult(val exported: Int, val failed: Int)

/**
 * Writes every picture inside [source] into [destination] as PNG.
 *
 * A frame set is the point — a HEIC burst, a Live Photo's still, the pages of a
 * TIFF, the frames of a GIF — but a single-frame file is not an error: it
 * exports one PNG, which is the same thing said about a file with one picture
 * in it.
 *
 * A Live Photo's motion clip is **not** exported. It is a video, the strip only
 * lists it because it is beside the photograph, and "export frames to PNG" has
 * nothing to say about it.
 *
 * [onProgress] is called with (done, total) before each picture, so a caller can
 * say something during the twelve seconds a 96-picture container takes.
 */
expect suspend fun exportFramesToPng(
    source: File,
    destination: File,
    onProgress: (done: Int, total: Int) -> Unit
): FrameExportResult

/**
 * The one place an export in flight is visible from.
 *
 * A global for the same reason `PipManager` is one: an export outlives the menu
 * that started it and the screen the user was on, and both the grid and the
 * viewer have to be able to say so. The alternative was the same banner state
 * duplicated into `FolderOverlayState` and `MediaViewerState`, kept in step by
 * hand.
 */
@Stable
object FrameExportStatus {
    /** Null when nothing is running and nothing has just finished. */
    var message by mutableStateOf<String?>(null)
        private set

    fun progress(name: String, done: Int, total: Int) {
        message = "Exporting $name — $done of $total"
    }

    fun finished(result: FrameExportResult) {
        message = when {
            result.exported == 0 -> "Nothing could be exported"
            result.failed > 0 -> "Exported ${result.exported}, ${result.failed} failed"
            result.exported == 1 -> "Exported 1 frame"
            else -> "Exported ${result.exported} frames"
        }
    }

    fun failed(reason: String) {
        message = "Export failed: $reason"
    }

    fun clear() {
        message = null
    }
}
