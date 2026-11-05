package com.woka.probe

import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.av_log_get_level
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

/**
 * Can JavaCV copy packets in-process, and which encoders does the bundled FFmpeg carry?
 * (ME-290, `video-editor/render/render.md` §3.2–§3.4.)
 *
 * **It reports, it does not assert** (`code-style/test.md`). The render's fast path —
 * cuts and reorders as a stream copy — exists only if `grabPacket` → `recordPacket`
 * works against `start(AVFormatContext)`; if it does not, §3.4's in-process decision is
 * reversed for that one path. The encoder half decides what the re-encode path writes.
 *
 * Five things per file: the whole file copied, the middle third copied with the source's
 * stamps and again rebased, the first and last thirds concatenated, and a keyframe scan.
 * Each output is opened again with a fresh grabber and decoded to the end.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.PacketCopyProbe" -i
 */
class PacketCopyProbe {

    /** File → the muxer the copy is written with: the source's own container. */
    private val fixtures = listOf(
        "rot90.mp4" to "mp4",
        NO_AUDIO to "matroska",
        CHAPTERS to "matroska",
        "cut.mkv" to "matroska",
        LIVE_PHOTO_MOTION to "mov"
    )

    @Test
    fun `copy packets and list the encoders`() = quietly {
        fixtures.forEach { (name, format) -> copyRows(SampleMedia.file(name), format) }
        generatedRows()
        encoderRows()
    }

    /**
     * Every committed video has exactly one keyframe, so the corpus rows can only cut at
     * zero. These three have one every half second: openh264 and Media Foundation H.264
     * in mp4, and openh264 in Matroska.
     */
    private fun generatedRows() {
        val dir = createTempDirectory("probe-gop").toFile()
        try {
            val openh264 = File(dir, "gop_openh264.mp4")
            val mf = File(dir, "gop_mf.mp4")
            val mkv = File(dir, "gop_openh264.mkv")
            runCatching { generateClip(openh264, "libopenh264", seconds = 3, gop = 15) }
                .onFailure { println("PROBE   generate openh264 FAILED ${it.brief()}") }
            runCatching { generateClip(mf, "h264_mf", seconds = 3, gop = 15) }
                .onFailure { println("PROBE   generate h264_mf FAILED ${it.brief()}") }
            // Written as Matroska directly: copying the mp4 across is refused by the muxer
            // ("Tag mp4a incompatible") unless `codec_tag` is cleared — a cross-container
            // concern the render, which keeps the source's container, does not have.
            runCatching { generateClip(mkv, "libopenh264", seconds = 3, gop = 15, format = "matroska") }
                .onFailure { println("PROBE   generate mkv FAILED ${it.brief()}") }
            listOf(openh264 to "mp4", mf to "mp4", mkv to "matroska")
                .filter { it.first.length() > 0 }
                .forEach { (file, format) -> copyRows(file, format) }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun copyRows(source: File, format: String) {
        println("PROBE ── ${source.name} → $format")
        val original = readBack(source)
        println("PROBE   source        $original")
        println("PROBE   keys          ${scanKeys(source)}")
        val third = original.durationMs * 1000 / 3
        row("full copy", source, format) { remuxAll(source, it, format) }
        row("range as-is", source, format) {
            remuxSpans(source, it, format, listOf(Span(third, third * 2)), rebase = false)
        }
        row("range rebased", source, format) {
            remuxSpans(source, it, format, listOf(Span(third, third * 2)), rebase = true)
        }
        row("concat A+B", source, format) {
            remuxSpans(source, it, format, listOf(Span(0, third), Span(third * 2, null)), rebase = true)
        }
    }

    /** One copy, then the output read back; the temp file goes either way. */
    private fun row(label: String, source: File, format: String, copy: (File) -> PacketSink) {
        val out = File.createTempFile("probe-copy", ".${source.extension}").also { it.delete() }
        try {
            val began = System.nanoTime()
            val sink = runCatching { copy(out) }
            val ms = (System.nanoTime() - began) / 1_000_000
            val written = sink.fold(
                { "wrote=${it.written} dropped=${it.dropped} in ${ms}ms bytes=${out.length()}" },
                { "copy FAILED ${it.brief()}" }
            )
            val back = if (out.length() > 0) readBack(out).toString() else "no output"
            println("PROBE   %-13s %s | %s".format(label, written, back))
        } finally {
            out.delete()
        }
    }

    private fun encoderRows() {
        println("PROBE ── encoders (avcodec_find_encoder_by_name)")
        ASKED_ENCODERS.forEach { println("PROBE   %-12s %s".format(it, if (encoderPresent(it)) "present" else "—")) }
        println("PROBE ── 1280×720 mp4, 30 frames + AAC, per H.264 encoder present")
        H264_ENCODERS.filter(::encoderPresent).forEach { codec ->
            println("PROBE   30f ${encodeTrial(codec, controlsFor(codec)[1])}")
            controlsFor(codec).forEach { println("PROBE   5s  ${encodeTrial(codec, it, frames = 150)}") }
        }
    }

    /** Errors only: the table must stay readable, and an encoder's refusal is still worth seeing. */
    private fun quietly(block: () -> Unit) {
        val level = av_log_get_level()
        av_log_set_level(AV_LOG_ERROR)
        try {
            block()
        } finally {
            av_log_set_level(level)
        }
    }
}
