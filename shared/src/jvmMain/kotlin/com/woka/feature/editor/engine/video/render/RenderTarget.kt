package com.woka.feature.editor.engine.video.render

import com.woka.feature.editor.core.EditorExport
import java.io.File

/**
 * Where a render is written: beside the source, under a name nothing else has
 * (`render.md` §3.1, §4).
 *
 * **Beside, never over.** A re-encode is lossy, so writing over the source would make a
 * second edit encode an already-encoded picture with the pristine original gone — the
 * one path where ME-132's reasoning against copies inverts.
 *
 * **`clip edited.mp4`, then `clip edited 2.mp4`** — `FolderActions.copyNameBeside`'s
 * shape with the word that says what happened to it. A copy never silently replaces
 * another file, which is the rule already in the tree.
 */
internal fun renderTargetBeside(source: File, extension: String): File {
    val dir = source.absoluteFile.parentFile
    val stem = source.nameWithoutExtension
    fun named(counter: Int) =
        File(dir, if (counter == 1) "$stem edited.$extension" else "$stem edited $counter.$extension")

    var counter = 1
    var candidate = named(counter)
    while (candidate.exists()) {
        counter++
        candidate = named(counter)
    }
    return candidate
}

/**
 * The scratch file a render writes into before it is the target: same folder, so the
 * final rename is a move on one volume, and a name the grid does not list.
 *
 * **Deleted on JVM exit unless it was renamed first** — a render still running when the
 * window closes is killed with the process (`EditorRenderStatus`), and this is what keeps
 * that from leaving a half-written film beside the user's own.
 */
internal fun scratchBeside(target: File): File =
    File(target.parentFile, ".${target.name}.woka-render").also { it.deleteOnExit() }

/**
 * The container a render writes for a source with [extension].
 *
 * **The source's own**, for a copy: every packet is already legal there. For an encode the
 * stream becomes H.264 and AAC, which `mp4`, `mov`, `m4v` and Matroska carry and `webm`,
 * `avi`, `flv` and the long tail of `video-formats.md` do not reliably — those come out as
 * `mp4`, which is also VF-104's answer for them.
 */
internal fun renderExtension(extension: String, copies: Boolean): String {
    val ext = extension.lowercase()
    if (copies) return ext
    return if (ext in ENCODE_KEEPS) ext else "mp4"
}

private val ENCODE_KEEPS = setOf("mp4", "mov", "m4v", "mkv")

/** The one entry the video engine adds to `Done ▾` — the mockup's words (state 9). */
internal val RenderVideo = EditorExport(
    id = "render-video",
    title = "Render video…",
    summary = "A new file beside this one. Plays edited anywhere."
)

/**
 * Resize on a film sizes the render and nothing else (ME-262, the author's choice
 * 2026-11-02): the player fits the picture to the window whatever its pixel count, so a
 * smaller frame would only look softer. The inspector says so rather than leave a slider
 * that appears to do nothing.
 */
internal fun renderOnlyNote(toolId: String): String? =
    if (toolId == "resize") "Applies when you render the video" else null
