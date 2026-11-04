package com.woka.feature.editor.engine.video.render

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.wholeSource
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * How far after a clip's start its first keyframe may sit and still be copied — half a
 * frame at 30 fps. Further than that and the copy would start on a frame the user cut.
 */
private const val KEYFRAME_SLACK_MS = 16L

/**
 * `Render video…` (ME-291, ME-292): **copy every packet when nothing touches a pixel and
 * every clip starts on a keyframe; encode everything otherwise** (`render.md` §3.2).
 *
 * Binary on purpose. A copied H.264 segment and an encoded one carry different parameter
 * sets, and splicing them into one track is the per-segment hybrid ME-295 needs a probe
 * for. A cut that is not on a keyframe demotes the whole render rather than moving the
 * cut, because the strip promised the cut lands where it was made (`timeline.md` §3.3).
 *
 * Written into a scratch file beside the target and moved over it last, so a cancelled,
 * failed or killed render leaves no film-shaped file behind. Never throws.
 */
internal fun renderVideo(
    source: File,
    document: EditDocument,
    /** Each clip's own look on top of [document], by layer key (ME-215). */
    looks: (Int) -> List<ToolOp>,
    registry: EditorToolRegistry,
    sourceSize: IntSize,
    alive: () -> Boolean,
    progress: (Float) -> Unit
): EditSaveResult {
    var scratch: File? = null
    return try {
        val probe = probeSource(source)
        val clips = TrimTool.clipsIn(document) ?: wholeSource(probe.durationMs)
        val recipes = clips.map { it.key }.distinct()
            .associateWith { key -> fold(EditDocument(document.ops + looks(key)), registry, sourceSize) }
        val pixelWork = recipes.values.any { !it.isIdentity(sourceSize) }
        val copies = !pixelWork && copiesInto(source.extension) &&
            clips.all { clip -> probe.keyframesMs.any { it in clip.fromMs..clip.fromMs + KEYFRAME_SLACK_MS } }
        val target = renderTargetBeside(source, renderExtension(source.extension, copies))
        scratch = scratchBeside(target)
        val format = containerFormat(target.extension, probe.format)
        if (copies) {
            copyClips(source, scratch, format, clips, alive, progress)
        } else {
            val pictures = if (pixelWork) recipes.mapValues { RecipePicture(sourceSize, it.value) } else emptyMap()
            encodeClips(source, scratch, format, clips, { clip -> pictures[clip.key] }, alive, progress)
        }
        if (!alive()) {
            scratch.delete()
            return EditSaveResult.NothingToDo
        }
        Files.move(scratch.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        EditSaveResult.Saved(target.path)
    } catch (failure: Exception) {
        scratch?.delete()
        EditSaveResult.Failed("The render failed (${failure.message}). The original is untouched.")
    }
}

/** Every clip's packets, rebased end to end, into one file of the source's own container. */
private fun copyClips(
    source: File,
    target: File,
    format: String,
    clips: List<TrimClip>,
    alive: () -> Boolean,
    progress: (Float) -> Unit
) = withGrabber(source) { grabber ->
    val recorder = FFmpegFrameRecorder(target, grabber.imageWidth, grabber.imageHeight, grabber.audioChannels)
    recorder.format = format
    recorder.start(grabber.formatContext)
    val sink = PacketSink(recorder)
    val totalMs = clips.sumOf { it.lengthMs }.coerceAtLeast(1L)
    var doneMs = 0L
    try {
        for (clip in clips) {
            copySpan(grabber, sink, clip.fromMs * 1000L, clip.toMs * 1000L, alive) { atUs ->
                progress((doneMs + (atUs / 1000L - clip.fromMs).coerceIn(0L, clip.lengthMs)).toFloat() / totalMs)
            }
            doneMs += clip.lengthMs
        }
    } finally {
        recorder.stop()
        recorder.release()
    }
}

private class SourceProbe(val durationMs: Long, val keyframesMs: List<Long>, val format: String)

private fun probeSource(source: File): SourceProbe = withGrabber(source) { grabber ->
    SourceProbe(grabber.lengthInTime / 1000L, keyframesMs(grabber), grabber.format.orEmpty())
}

/**
 * The muxer for [extension]. Named explicitly where it matters, because the grabber's own
 * format is a list — `mov,mp4,m4a,3gp,3g2,mj2` — and its first entry is not always the
 * file's (ME-290).
 */
private fun containerFormat(extension: String, probed: String): String = when (extension.lowercase()) {
    "mp4", "m4v" -> "mp4"
    "mov" -> "mov"
    "mkv" -> "matroska"
    "webm" -> "webm"
    "avi" -> "avi"
    else -> probed.substringBefore(',').ifEmpty { "mp4" }
}

private fun <T> withGrabber(file: File, block: (FFmpegFrameGrabber) -> T): T {
    val grabber = FFmpegFrameGrabber(file)
    try {
        grabber.start()
        return block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}
