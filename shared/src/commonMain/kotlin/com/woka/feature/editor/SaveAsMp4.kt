package com.woka.feature.editor

import com.woka.feature.editor.core.EditSaveResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.io.File

/**
 * The formats nothing else on a Windows machine opens (VF-104, `video-formats.md`): the
 * ones Woka plays only because it carries its own ffmpeg. Everything else in the video
 * list already plays in Films & TV or VLC, and offering an MP4 of it would be clutter.
 */
val MP4_RESCUE_EXTENSIONS = setOf("rm", "rmvb", "amv", "mtv", "dav", "bik", "thp", "rpl", "nsv", "xesc")

/** Whether the grid offers "Save as MP4" for [file]. */
fun offersMp4Copy(file: File): Boolean = file.extension.lowercase() in MP4_RESCUE_EXTENSIONS

/** The conversion itself — the render's copy-or-encode, in `jvmMain`. Never throws. */
internal expect suspend fun convertVideoToMp4(path: String, progress: (Float) -> Unit): EditSaveResult

/**
 * Starts the conversion and reports it through `EditorRenderStatus` — the render's banner,
 * its bar and its cancel, because this *is* a render with an empty document. Refused, with
 * the banner saying why, while another render runs (one at a time, `render.md` §4).
 */
fun saveAsMp4(path: String, onSaved: (String) -> Unit) {
    if (EditorRenderStatus.running) {
        EditorRenderStatus.failed("A render is already running. Wait for it, or cancel it first.")
        return
    }
    val job = EditorRenderStatus.scope.launch(start = CoroutineStart.LAZY) {
        val result = convertVideoToMp4(path) { fraction ->
            EditorRenderStatus.scope.launch { EditorRenderStatus.progress(fraction) }
        }
        when (result) {
            is EditSaveResult.Saved -> {
                EditorRenderStatus.rendered(File(result.path).name)
                onSaved(result.path)
            }
            is EditSaveResult.Failed -> EditorRenderStatus.failed(result.reason)
            EditSaveResult.NothingToDo -> EditorRenderStatus.clear()
        }
    }
    EditorRenderStatus.started(File(path).name, job)
    job.start()
}
