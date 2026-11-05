package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.probe.declaredWhitePoint
import com.woka.feature.viewer.video.engine.probe.scenePeak
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_CONTENT_LIGHT_LEVEL
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.test.Test

private const val BOYS = "The Boys Season 5/The.Boys.S05E01.Fifteen.Inches.of.Sheer.Dynamite.AMZN.DV.HDR.WEB-DL.2160p.by.AKTEP.mkv"
private const val AVATAR = "Avatar The Last Airbender Season 2/S02E01.mkv"

/**
 * ME-301 / ME-237 / ME-305: what the **bundled** FFmpeg tells the player about an HDR film's
 * peak. Needs the author's `D:/Movies`; prints nothing to measure without it.
 *
 * Measured 2026-11-05 on 17 PQ files:
 * - MaxCLL and the mastering display's peak are on every decoded frame (HEVC SEI — none of the
 *   containers carry them), identical at the start and after a seek, and at 0–40 min;
 * - MaxCLL matches the frames' own 8-bit peak (S02E02 227 / 253, Green Book 717 / 679,
 *   The Boys 913 / 1010); MDCV is 1000 on all 17;
 * - HDR10+ changes per scene (The Boys: 10 values in 25 s, one at 1060 > its MaxCLL), but on
 *   JavaCV's RGBA frame it stuck at the first value — its side data piled up instead, 19.5 KB a
 *   frame (ME-237). Dolby Vision L1 is not readable: no Bytedeco struct, and one value read by
 *   raw offset for three minutes.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.VideoHdrPeakProbe" -i
 */
class VideoHdrPeakProbe {

    private val root = File("D:/Movies")

    @Test
    fun `the declared white point of every film`() {
        val films = root.takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("mkv", "mp4", "mov") }
            .orEmpty()
        films.forEach { file ->
            onGrabber(FreshFrameGrabber(file)) { grabber ->
                val first = grabber.grabImage()?.opaque as? AVFrame ?: return@onGrabber
                if (av_frame_get_side_data(first, AV_FRAME_DATA_CONTENT_LIGHT_LEVEL) == null) return@onGrabber
                val atStart = first.declaredWhitePoint()
                grabber.setVideoTimestamp(600_000_000L)
                val later = (grabber.grabImage()?.opaque as? AVFrame)?.declaredWhitePoint()
                println("${file.name}: white point $atStart at the start, $later at 10 min")
            }
        }
    }

    /** One frame every 20 s: the brightest channel's 8-bit PQ code, carried to nits. */
    @Test
    fun `the frames' own peak against the declared one`() {
        listOf(AVATAR, BOYS).map { File(root, it) }.filter { it.isFile }.forEach { file ->
            onGrabber(FreshFrameGrabber(file)) { grabber ->
                val declared = (grabber.grabImage()?.opaque as? AVFrame)?.declaredWhitePoint()
                val seconds = minOf(grabber.lengthInTime / 1_000_000L, 1800L)
                val peaks = (0 until seconds step 20).mapNotNull { t ->
                    grabber.setVideoTimestamp(t * 1_000_000L)
                    grabber.grabImage()?.let { nits(brightestCode(it)) }
                }
                println("${file.name}: declared ${declared?.times(100)} nits, frames peak at ${peaks.maxOrNull()}")
            }
        }
    }

    /** ME-305's read, through ME-237's grabber: a run across cuts, without a seek. */
    @Test
    fun `HDR10+ scene peaks on the RGBA path`() {
        val file = File(root, BOYS).takeIf { it.isFile } ?: return
        onGrabber(FreshFrameGrabber(file)) { grabber ->
            grabber.setVideoTimestamp(298_000_000L)
            val peaks = (0 until 600).map { (grabber.grabImage()?.opaque as? AVFrame)?.scenePeak() }
            val bare = peaks.count { it == null }
            println("${file.name}: scene peaks from 4:58 — ${peaks.filterNotNull().distinct()}, $bare of 600 frames without one")
        }
    }

    /** ME-237: entries on the RGBA frame after 300 pictures, JavaCV's grabber and ours. */
    @Test
    fun `side data on the RGBA frame across a run`() {
        val file = File(root, BOYS).takeIf { it.isFile } ?: return
        listOf(FFmpegFrameGrabber(file), FreshFrameGrabber(file)).forEach { plain ->
            onGrabber(plain) { grabber ->
                val counts = (1..300).mapNotNull { (grabber.grabImage()?.opaque as? AVFrame)?.nb_side_data() }
                println("${grabber.javaClass.simpleName}: ${counts.first()} entries → ${counts.last()}")
            }
        }
    }

    private fun onGrabber(grabber: FFmpegFrameGrabber, block: (FFmpegFrameGrabber) -> Unit) {
        grabber.pixelFormat = AV_PIX_FMT_RGBA
        try {
            grabber.start()
            block(grabber)
        } catch (failure: Exception) {
            println("probe failed: ${failure.message}")
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    private fun brightestCode(frame: Frame): Int {
        val buffer = frame.image[0] as ByteBuffer
        var top = 0
        var i = 0
        while (i + 2 < buffer.limit()) {
            val r = buffer.get(i).toInt() and 0xFF
            val g = buffer.get(i + 1).toInt() and 0xFF
            val b = buffer.get(i + 2).toInt() and 0xFF
            top = maxOf(top, r, g, b)
            i += 4
        }
        return top
    }

    /** SMPTE ST 2084 from an 8-bit code to nits — approximate, and labelled so. */
    private fun nits(code: Int): Int {
        val p = (code / 255.0).pow(1 / 78.84375)
        val num = maxOf(p - 0.8359375, 0.0)
        return ((num / (18.8515625 - 18.6875 * p)).pow(1 / 0.1593017578125) * 10000).toInt()
    }
}
