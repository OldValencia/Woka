package com.woka.feature.editor.engine.video.render

import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.tools.trim.wholeSource
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MP3
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What an `mp4` carries that every player plays — the only streams worth copying as they are. */
private val COPYABLE_VIDEO = setOf(AV_CODEC_ID_H264, AV_CODEC_ID_HEVC)
private val COPYABLE_AUDIO = setOf(AV_CODEC_ID_AAC, AV_CODEC_ID_MP3)

/**
 * **"Save as MP4"** for a film in a format nothing else opens (VF-104): `rm`, `dav`,
 * `bik`, `thp`… — a copy beside it that plays anywhere, the original untouched.
 *
 * Built on the render, not beside it, by the block's own condition: Woka has one encode
 * path and a second is not wanted. So it is a render of the whole file with no document —
 * **streams copied when `mp4` can hold them** (the H.264 inside a `.dav` comes out bit for
 * bit) and re-encoded with openh264 and AAC when it cannot.
 *
 * "Can hold" is asked of the codec, not of the muxer: the `mp4` muxer accepts tags — MJPEG,
 * MPEG-4 Part 2 — that write a valid file most players still refuse, and a file that only
 * ffmpeg plays is not what this entry promises.
 */
internal fun convertToMp4(source: File, alive: () -> Boolean, progress: (Float) -> Unit): EditSaveResult {
    val target = mp4Beside(source)
    val scratch = scratchBeside(target)
    return try {
        val probe = FFmpegFrameGrabber(source).let { grabber ->
            try {
                grabber.start()
                Mp4Probe(grabber.lengthInTime / 1000L, copiesIntoMp4(grabber))
            } finally {
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
            }
        }
        val clips = wholeSource(probe.durationMs)
        if (probe.copies) copyWhole(source, scratch, probe.durationMs, alive, progress)
        else encodeClips(source, scratch, "mp4", clips, { null }, alive, progress)
        if (!alive()) {
            scratch.delete()
            return EditSaveResult.NothingToDo
        }
        Files.move(scratch.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        EditSaveResult.Saved(target.path)
    } catch (failure: Exception) {
        scratch.delete()
        EditSaveResult.Failed("Could not save ${source.name} as MP4 (${failure.message}). The original is untouched.")
    }
}

private class Mp4Probe(val durationMs: Long, val copies: Boolean)

private fun copiesIntoMp4(grabber: FFmpegFrameGrabber): Boolean =
    grabber.videoCodec in COPYABLE_VIDEO &&
        (grabber.audioChannels == 0 || grabber.audioCodec in COPYABLE_AUDIO)

private fun copyWhole(source: File, target: File, durationMs: Long, alive: () -> Boolean, progress: (Float) -> Unit) {
    val grabber = FFmpegFrameGrabber(source)
    try {
        grabber.start()
        val recorder = FFmpegFrameRecorder(target, grabber.imageWidth, grabber.imageHeight, grabber.audioChannels)
        recorder.format = "mp4"
        recorder.start(grabber.formatContext)
        try {
            copySpan(grabber, PacketSink(recorder), 0L, Long.MAX_VALUE / 4, alive) { atUs ->
                progress(atUs / 1000f / durationMs.coerceAtLeast(1L))
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

/** `film.mp4`, then `film 2.mp4` — the source's own name, since nothing about it was edited. */
private fun mp4Beside(source: File): File {
    val dir = source.absoluteFile.parentFile
    var counter = 1
    var candidate = File(dir, "${source.nameWithoutExtension}.mp4")
    while (candidate.exists()) {
        counter++
        candidate = File(dir, "${source.nameWithoutExtension} $counter.mp4")
    }
    return candidate
}
