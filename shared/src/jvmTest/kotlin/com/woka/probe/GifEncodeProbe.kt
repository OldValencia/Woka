package com.woka.probe

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.woka.feature.viewer.image.forwardFrames
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_PAL8
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test

/**
 * Can Woka write an animated GIF through the bundled ffmpeg? (copy with edits, 2026-11-21)
 *
 * **Reports, does not assert.** Three things decide the design: paletteuse's PAL8 frame
 * reaching the `gif` encoder through JavaCV with its palette (JavaCV's filter copies the
 * palette after the indices, and the recorder's `av_image_fill_arrays` reads it from
 * there — the probe checks that reading the source is right), transparency surviving
 * the encoder's frame differencing, and per-frame delays plus the loop count round-trip.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.GifEncodeProbe" -i
 */
class GifEncodeProbe {

    private val width = 64
    private val height = 48
    private val delaysMs = listOf(50, 100, 200)

    @Test
    fun `synthetic frames with transparency round-trip`() {
        listOf("default", "0").forEach { flags ->
            val out = File.createTempFile("probe-gif-$flags", ".gif")
            val frames = delaysMs.indices.map { synthetic(it) }
            runCatching { encode(out, frames, delaysMs, loop = 2, gifflags = flags) }
                .onFailure { println("PROBE gifflags=$flags encode=FAILED $it"); return@forEach }
            report("synthetic gifflags=$flags", out, frames)
        }
    }

    @Test
    fun `the anim fixture re-encodes`() {
        val source = File("src/jvmTest/resources/media/anim.gif")
        val frames = mutableListOf<ByteArray>()
        var size = 0 to 0
        forwardFrames(source) { _, bitmap ->
            size = bitmap.width to bitmap.height
            frames += rgbaOf(bitmap)
        }
        val codec = Codec.makeFromData(Data.makeFromBytes(source.readBytes()))
        val delays = codec.framesInfo.map { it.duration }
        println("PROBE fixture ${size.first}x${size.second} frames=${frames.size} delays=$delays loop=${codec.repetitionCount}")
        val out = File.createTempFile("probe-gif-fixture", ".gif")
        encode(out, frames, delays, loop = codec.repetitionCount, gifflags = "default", w = size.first, h = size.second)
        report("fixture", out, frames, size.first, size.second)
    }

    private fun encode(out: File, frames: List<ByteArray>, delays: List<Int>, loop: Int, gifflags: String, w: Int = width, h: Int = height) {
        val chain = "split[a][b];[a]palettegen=stats_mode=single:reserve_transparent=1[p];[b][p]paletteuse=new=1:alpha_threshold=128"
        val filter = FFmpegFrameFilter(chain, w, h).apply {
            pixelFormat = AV_PIX_FMT_RGBA
            frameRate = 100.0
            start()
        }
        val recorder = FFmpegFrameRecorder(out, w, h, 0).apply {
            format = "gif"
            videoCodecName = "gif"
            pixelFormat = AV_PIX_FMT_PAL8
            frameRate = 100.0
            setOption("loop", (if (loop < 0) 0 else if (loop == 0) -1 else loop).toString())
            setOption("final_delay", (delays.last() / 10).toString())
            if (gifflags != "default") setVideoOption("gifflags", gifflags)
            start()
        }
        val input = Frame(w, h, Frame.DEPTH_UBYTE, 4, w * 4)
        // The filter renumbers pts, so the clock is ours: frame n starts at the sum of the
        // delays before it, and paletteuse answers one frame per frame, in order.
        val startsUs = delays.runningFold(0L) { at, ms -> at + ms * 1000L }
        var written = 0
        frames.forEachIndexed { index, rgba ->
            (input.image[0] as ByteBuffer).apply { rewind(); put(rgba); rewind() }
            input.timestamp = startsUs[index]
            filter.push(input, AV_PIX_FMT_RGBA)
            while (true) {
                val pal = filter.pullImage() ?: break
                recorder.timestamp = startsUs[written++]
                recorder.record(pal, AV_PIX_FMT_PAL8)
            }
        }
        println("PROBE written=$written of ${frames.size}")
        filter.stop(); filter.release()
        recorder.stop(); recorder.release()
        input.close()
    }

    private fun report(label: String, out: File, sent: List<ByteArray>, w: Int = width, h: Int = height) {
        val codec = Codec.makeFromData(Data.makeFromBytes(out.readBytes()))
        println("PROBE $label bytes=${out.length()} frames=${codec.frameCount} delays=${codec.framesInfo.map { it.duration }} loop=${codec.repetitionCount}")
        var index = 0
        forwardFrames(out) { _, bitmap ->
            val back = rgbaOf(bitmap)
            val want = sent.getOrNull(index) ?: return@forwardFrames
            var colourErr = 0L; var opaque = 0; var alphaWrong = 0
            for (p in 0 until w * h) {
                val a = want[p * 4 + 3].toInt() and 0xFF
                val b = back[p * 4 + 3].toInt() and 0xFF
                if ((a >= 128) != (b >= 128)) alphaWrong++
                if (a < 128) continue
                opaque++
                for (c in 0..2) colourErr += abs((want[p * 4 + c].toInt() and 0xFF) - (back[p * 4 + c].toInt() and 0xFF))
            }
            println("PROBE $label frame=$index meanErr=${"%.2f".format(colourErr / (opaque * 3.0).coerceAtLeast(1.0))} alphaWrong=$alphaWrong/${w * h}")
            index++
        }
    }

    private fun rgbaOf(bitmap: ImageBitmap): ByteArray {
        val skia = bitmap.asSkiaBitmap()
        val info = ImageInfo(skia.width, skia.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        return skia.readPixels(info, info.minRowBytes, 0, 0) ?: error("no pixels")
    }

    /** A moving gradient with a transparent block that moves the other way. */
    private fun synthetic(k: Int): ByteArray {
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val p = (y * width + x) * 4
            val hole = x in (40 - k * 12) until (52 - k * 12) && y in 10 until 30
            bytes[p] = ((x * 4 + k * 40) and 0xFF).toByte()
            bytes[p + 1] = (y * 5).toByte()
            bytes[p + 2] = (255 - x * 4).toByte()
            bytes[p + 3] = if (hole) 0 else 255.toByte()
        }
        return bytes
    }
}
