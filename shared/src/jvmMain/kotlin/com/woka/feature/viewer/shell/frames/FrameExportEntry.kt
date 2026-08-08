package com.woka.feature.viewer.shell.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.core.data.MediaItem
import com.woka.core.utils.FrameExportStatus
import com.woka.core.utils.exportFramesToPng
import com.woka.core.utils.openDirectoryPicker
import com.woka.feature.viewer.shell.ViewerMenuEntry
import kotlinx.coroutines.launch
import java.io.File

/**
 * The viewer's "Export Frames…" right-click entry, or nothing.
 *
 * Nothing for a file with one picture in it: the entry exists to get a *set* of
 * pictures out, and on an ordinary photograph it would be a "convert to PNG"
 * wearing the wrong name. [strip] has already counted them, so this costs no
 * second look at the file.
 *
 * The picker is opened from the click rather than from a coroutine because it
 * is a blocking native dialog — the export itself is what goes to the
 * background, and it reports through [FrameExportStatus] so it survives the
 * viewer being closed on top of it.
 */
@Composable
internal fun rememberFrameExportEntry(
    activeItem: MediaItem?,
    strip: FrameStripModel
): List<ViewerMenuEntry> {
    val scope = rememberCoroutineScope()
    return remember(activeItem, strip, scope) {
        if (activeItem == null || strip.isSingle) return@remember emptyList()
        listOf(
            ViewerMenuEntry("Export Frames...") {
                val source = activeItem.file
                val destination = openDirectoryPicker("Export frames to...", source.parent)
                    ?: return@ViewerMenuEntry
                scope.launch {
                    FrameExportStatus.progress(source.name, 0, strip.frames.size)
                    runCatching {
                        exportFramesToPng(source, File(destination)) { done, total ->
                            FrameExportStatus.progress(source.name, done, total)
                        }
                    }.fold(
                        onSuccess = { FrameExportStatus.finished(it) },
                        onFailure = { FrameExportStatus.failed(it.message ?: "unknown error") }
                    )
                }
            }
        )
    }
}
