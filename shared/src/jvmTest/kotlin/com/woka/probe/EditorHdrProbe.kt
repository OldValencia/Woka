package com.woka.probe

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.readStillColor
import com.woka.fixtures.HDR_PQ_AVIF
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * The question §6.1's `To HDR` is gated on: can the **bundled** ffmpeg write a
 * PQ or HLG still that Woka reads back with the right transfer function?
 *
 * Skia is not a candidate and is not tested — it describes a valid PQ `cICP` PNG
 * as "gamma close to sRGB" (measured 2026-08-07, recorded in `StillColorInfo`),
 * so HDR output goes through ffmpeg or it does not ship.
 *
 * **The second half of the question is not machine-answerable.** Whether Windows
 * Photos opens the result as HDR has to be looked at; this probe writes the files
 * and prints where they are.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.EditorHdrProbe" -i
 */
class EditorHdrProbe {

    private val encoders = listOf(
        Target("PQ AVIF", "libaom-av1", "avif", "smpte2084"),
        Target("HLG AVIF", "libaom-av1", "avif", "arib-std-b67"),
        Target("PQ HEIF", "libx265", "heif", "smpte2084"),
        Target("HLG HEIF", "libx265", "heif", "arib-std-b67")
    )

    private data class Target(
        val label: String,
        val encoder: String,
        val extension: String,
        val transfer: String
    )

    @Test
    fun `what the bundled ffmpeg can write, and what we read back`() {
        // Deliberately **not** a temp root: half of this probe's question is
        // "does Windows Photos open it as HDR", which someone has to look at.
        // A directory that deletes itself on the way out answers nothing.
        val root = File("build/hdr-probe").apply { mkdirs() }
        val binary = runCatching { Loader.load(ffmpeg::class.java) }.getOrNull()
        if (binary == null) {
            println("Editor HDR probe — the bundled ffmpeg could not be unpacked; nothing measured")
            return
        }

        val source = SampleMedia.file(HDR_PQ_AVIF)
        // The known-good PQ file goes in beside the written ones, so the check is
        // "do these look like that one" rather than "does this look HDR to me".
        source.copyTo(File(root, "reference_$HDR_PQ_AVIF"), overwrite = true)
        val rows = encoders.map { target -> row(binary, root, source, target) }

        println("Editor HDR probe — writing HDR stills with the bundled ffmpeg")
        println(renderTable(listOf("target", "encoder", "written", "read back as"), rows))
        println()
        println("Files are in `${root.absolutePath}`.")
        println("**By hand:** open each in Windows Photos and record whether it displays as HDR.")
        println("`To HDR` ships only if a row both writes and reads back with the right transfer,")
        println("*and* the system viewer agrees. Otherwise the tool is `Keep` + `To SDR` only.")
    }

    private fun row(binary: String, root: File, source: File, target: Target): List<String> {
        val out = File(root, "hdr_${target.transfer}.${target.extension}")
        val args = listOf(
            binary, "-y", "-hide_banner", "-loglevel", "error",
            "-i", source.absolutePath,
            "-frames:v", "1",
            "-c:v", target.encoder,
            "-pix_fmt", "yuv420p10le",
            "-color_trc", target.transfer,
            "-color_primaries", "bt2020",
            "-colorspace", "bt2020nc",
            out.absolutePath
        )
        val outcome = run(args)
        if (!out.isFile || out.length() == 0L) {
            return listOf(target.label, "`${target.encoder}`", "**✗** ${outcome.take(60)}", "—")
        }
        val colour = readStillColor(out)
        val readBack = when (colour.transfer) {
            TransferKind.SDR -> "**SDR** — the transfer did not survive"
            else -> "${colour.transfer}" + if (colour.isWideGamut) " + wide" else ""
        }
        return listOf(target.label, "`${target.encoder}`", "${out.length() / 1024}KB", readBack)
    }

    /** Merged output, drained before waiting: ffmpeg fills a pipe and deadlocks. */
    private fun run(args: List<String>): String {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return "timed out"
        }
        return if (process.exitValue() == 0) "ok" else text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
    }
}
