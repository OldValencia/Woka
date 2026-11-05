package com.woka.feature.editor.engine.video.render

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.HdrView
import com.woka.core.data.applyTo
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.viewer.video.engine.probe.toColour
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
        val probe = probeSource(source, alive)
        if (!alive()) return EditSaveResult.NothingToDo
        if (probe.durationMs <= 0L) return EditSaveResult.Failed("${source.name} does not say how long it is. Nothing was written.")
        val clips = TrimTool.clipsIn(document) ?: wholeSource(probe.durationMs)
        val recipes = clips.map { it.key }.distinct()
            .associateWith { key -> fold(EditDocument(document.ops + looks(key)), registry, sourceSize) }
        // A PQ or HLG film is tone mapped by the canvas's own stage (`hdrStageFor`), so an
        // encode that would otherwise pass frames through draws every one of them — under
        // the HDR mode the canvas showed it in (ME-304), to the film's or scene's peak.
        val colour = HdrView.mode.applyTo(probe.hdr.toColour()).takeIf { it.needsColorManagement }
        // **The render is what the canvas showed** (the author, 2026-11-05): an HDR mode that
        // changes the picture — Off on a PQ film, On on an SDR one — rules the copy out, which
        // would hand back the source's own look instead.
        val modeMoves = colour != probe.hdr.toColour().takeIf { it.needsColorManagement }
        val pixelWork = modeMoves || recipes.values.any { !it.isIdentity(sourceSize) }
        // A gain, a mute or a fade changes every sample, which a packet copy cannot (ME-240).
        val sound = renderSoundOf(document, looks, clips)
        // A clip at another speed, or a freeze, is retimed — which a packet copy cannot do.
        val copies = !pixelWork && sound == null && copiesInto(source.extension) && clips.all { it.speed == 1f && !it.isFreeze } &&
            clips.all { clip -> probe.keyframesMs.any { it in clip.fromMs..clip.fromMs + KEYFRAME_SLACK_MS } }
        val target = renderTargetBeside(source, renderExtension(source.extension, copies))
        scratch = scratchBeside(target)
        val format = containerFormat(target.extension, probe.format)
        if (copies) {
            copyClips(source, scratch, format, clips, alive, progress)
        } else {
            val drawn = pixelWork || colour != null
            val buffers = HashMap<EvenSize, RenderBuffers>()
            val shared = { size: EvenSize -> buffers.getOrPut(size) { RenderBuffers(size) } }
            val pictures = if (drawn) recipes.mapValues { RecipePicture(sourceSize, it.value, colour, shared) } else emptyMap()
            try {
                encodeClips(source, scratch, format, clips, { clip -> pictures[clip.key] }, alive, progress, sound)
            } finally {
                buffers.values.forEach { it.close() }
            }
        }
        if (!alive()) {
            scratch.delete()
            return EditSaveResult.NothingToDo
        }
        // Named again if a file took the name while this ran: an atomic move replaces it on Windows.
        val landed = if (target.exists()) renderTargetBeside(source, target.extension) else target
        Files.move(scratch.toPath(), landed.toPath(), StandardCopyOption.ATOMIC_MOVE)
        EditSaveResult.Saved(landed.path)
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

/** The edit's sound for a render, or null when neither the film nor any clip has any. */
private fun renderSoundOf(document: EditDocument, looks: (Int) -> List<ToolOp>, clips: List<TrimClip>): RenderSound? {
    val whole = SoundTool.mixIn(document.ops)
    val own = clips.map { it.key }.distinct().associateWith { SoundTool.mixIn(looks(it)) }
    if (whole == null && own.values.all { it == null }) return null
    return RenderSound(whole, { clip -> own[clip.key] }, clips.sumOf { it.lengthMs })
}

