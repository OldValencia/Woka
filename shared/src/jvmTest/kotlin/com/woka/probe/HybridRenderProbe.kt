package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.av_log_get_level
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import java.io.File
import kotlin.test.Test

/**
 * TEMPORARY (ME-295): copy / changed / copy, with independent segment decodes as the oracle.
 * A muxer accepting packets is insufficient: every decoded frame must match that oracle.
 * Source fixtures use system FFmpeg; the changed H.264 segment uses bundled JavaCV,
 * exactly the renderer's encoder. System concat is a comparison, not an app dependency.
 * Artifacts stay in `shared/build/me295` for independent inspection and can be discarded.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "*HybridRenderProbe" --info
 */
class HybridRenderProbe {
    @Test
    fun `measure copied and encoded segments in one track`() {
        val root = File("build/me295").absoluteFile.also { it.mkdirs() }
        val level = av_log_get_level()
        av_log_set_level(AV_LOG_ERROR)
        try {
            val rows = mutableListOf<String>()
            for (kind in listOf("openh264", "x264-baseline", "x264-bframes", "hevc10")) {
                runCatching { measure(kind, root, rows) }
                    .onFailure { rows += "$kind FAILED ${it.message}" }
            }
            File(root, "results.txt").writeText(rows.joinToString("\n"))
            rows.forEach { println("ME295 $it") }
            println("ME295 artifacts=$root")
        } finally {
            av_log_set_level(level)
        }
    }

    private fun measure(kind: String, root: File, rows: MutableList<String>) {
        val dir = File(root, kind).also { it.mkdirs() }
        val source = hybridSource(dir, kind)
        hybridCommand(dir, "split", "-i", source.path, "-map", "0:v:0", "-an", "-c", "copy",
            // ME-295: allow the fixture's three B-frames of timestamp lead at a segment boundary.
            "-f", "segment", "-segment_time", "2", "-segment_time_delta", "0.1",
            "-reset_timestamps", "1", File(dir, "part%d.mp4").path)
        val parts = (0..2).map { File(dir, "part$it.mp4") }
        parts.forEach {
            val back = readBack(it)
            rows += "$kind fixture ${it.name}: $back"
            check(back.frames == 60) { "Invalid fixture ${it.name}: $back" }
        }
        val middle = File(dir, "changed.mp4")
        val began = System.nanoTime()
        hybridEncode(parts[1], middle)
        rows += "$kind encodeMiddleMs=${(System.nanoTime() - began) / 1_000_000} ${readBack(middle)}"
        for ((label, segments) in listOf("control" to parts, "hybrid" to listOf(parts[0], middle, parts[2]))) {
            trial(dir, kind, "$label-direct", segments, "mp4", true, rows)
            for (container in listOf("mp4", "mkv")) {
                trial(dir, kind, "$label-concat", segments, container, false, rows)
            }
        }
        if (kind == "hevc10") {
            val hevc = File(dir, "changed-hevc.mp4")
            hybridCommand(dir, "encode-hevc", "-i", parts[1].path, "-vf", "negate", "-an",
                "-c:v", "libx265", "-preset", "ultrafast", "-x265-params", "pools=2:frame-threads=2:log-level=error",
                "-pix_fmt", "yuv420p10le", hevc.path)
            for (container in listOf("mp4", "mkv")) {
                trial(dir, kind, "hybrid-hevc-concat", listOf(parts[0], hevc, parts[2]), container, false, rows)
            }
            val inBand = listOf(parts[0], hevc, parts[2]).mapIndexed { index, part ->
                File(dir, "inband$index.ts").also { ts ->
                    hybridCommand(dir, "inband$index", "-i", part.path, "-map", "0:v:0", "-c", "copy",
                        "-bsf:v", "hevc_mp4toannexb", "-f", "mpegts", ts.path)
                }
            }
            for (container in listOf("mp4", "mkv")) {
                trial(dir, kind, "hybrid-hevc-inband", inBand, container, false, rows)
            }
        }
    }

    private fun trial(
        dir: File,
        kind: String,
        label: String,
        segments: List<File>,
        container: String,
        direct: Boolean,
        rows: MutableList<String>
    ) {
        val out = File(dir, "$label.$container")
        val began = System.nanoTime()
        runCatching {
            if (direct) hybridPackets(segments, out) else {
                val list = File(dir, "$label-$container.txt")
                list.writeText(segments.joinToString("\n") { "file '${it.name}'" })
                hybridCommand(dir, "$label-$container", "-f", "concat", "-safe", "0", "-i", list.path,
                    "-map", "0:v:0", "-an", "-c", "copy", out.path)
            }
            val ms = (System.nanoTime() - began) / 1_000_000
            rows += "$kind $label.$container muxMs=$ms ${hybridCheck(segments, out)}"
        }.onFailure { rows += "$kind $label.$container FAILED ${it.message}" }
    }
}
