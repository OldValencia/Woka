package com.woka.probe

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.readStillColor
import com.woka.feature.viewer.image.stillDecoderFor
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test

/**
 * Can this repository write an AVIF at all? (ME-181)
 *
 * **It reports, it does not assert** (`code-style/test.md`). The question is a
 * measurement standing in front of a design decision: `To SDR` has to bake pixels into
 * the user's own `.avif`, Skia's `EncodedImageFormat` has no AVIF entry, and nothing in
 * the tree has ever encoded with ffmpeg. `FFmpegFrameRecorder` is on the classpath and
 * the bundled ffmpeg has the muxer — neither of which says the two can be driven from
 * Java into a *single-frame* AVIF that Woka's own decoder reads back.
 *
 * Three things have to be true, and the probe prints which of them are:
 * 1. the recorder opens with `avif` / `libaom-av1` and closes without throwing;
 * 2. the file it leaves is one Woka's decoder opens;
 * 3. what comes back is tagged SDR and holds the pixels that went in.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.FfmpegEncodeProbe" -i
 */
class FfmpegEncodeProbe {

    private val width = 64
    private val height = 48

    /** A flat mid-grey, so a channel swap or a range shift is obvious in one number. */
    private val level = 128

    @Test
    fun `write a single-frame AVIF and read it back`() {
        val out = File.createTempFile("probe-encode", ".avif").also { it.delete() }
        println("PROBE target=${out.absolutePath}")

        val wrote = runCatching { encode(out) }
        if (wrote.isFailure) {
            println("PROBE encode=FAILED ${wrote.exceptionOrNull()}")
            return
        }
        println("PROBE encode=ok bytes=${out.length()}")
        if (out.length() == 0L) {
            println("PROBE verdict=NO — the recorder produced an empty file")
            return
        }

        val colour = runCatching { readStillColor(out) }
        println("PROBE colour=${colour.getOrNull()}")

        val decoded = runCatching { stillDecoderFor(out).frame(out, 0) }
        if (decoded.isFailure) {
            println("PROBE decode=FAILED ${decoded.exceptionOrNull()}")
            return
        }
        val bitmap = decoded.getOrThrow()
        val centre = bitmap.toPixelMap()[width / 2, height / 2]
        val red = (centre.red * 255f).toInt()
        println("PROBE size=${bitmap.width}x${bitmap.height} centre=$red wanted=$level")

        val sane = bitmap.width == width &&
            bitmap.height == height &&
            abs(red - level) <= 6 &&
            colour.getOrNull()?.transfer == TransferKind.SDR
        println("PROBE verdict=${if (sane) "YES" else "NO"}")
        out.delete()
    }

    /**
     * One frame through `FFmpegFrameRecorder`.
     *
     * `setVideoCodecName` rather than an `AV_CODEC_ID`: libaom is a named external
     * encoder, and the id would resolve to whatever built-in AV1 encoder the build
     * happens to carry.
     */
    private fun encode(target: File) {
        val recorder = FFmpegFrameRecorder(target, width, height, 0)
        recorder.format = "avif"
        recorder.videoCodecName = "libaom-av1"
        recorder.pixelFormat = AV_PIX_FMT_YUV420P
        recorder.frameRate = 1.0
        recorder.videoQuality = 20.0
        // BT.709 throughout: this is the *SDR* half of the question, and a file that came
        // back tagged PQ would answer a different one.
        recorder.setVideoOption("color_primaries", "bt709")
        recorder.setVideoOption("color_trc", "bt709")
        recorder.setVideoOption("colorspace", "bt709")
        recorder.start()
        try {
            recorder.record(greyFrame())
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    /**
     * A BGR24 frame, filled **by stride rather than by width**: `Frame` pads its rows,
     * and writing `width * 3` bytes per line walks diagonally down a padded buffer.
     */
    private fun greyFrame(): Frame {
        val frame = Frame(width, height, Frame.DEPTH_UBYTE, 3)
        val buffer = frame.image[0] as ByteBuffer
        val row = ByteArray(frame.imageStride) { level.toByte() }
        repeat(height) { line ->
            buffer.position(line * frame.imageStride)
            buffer.put(row, 0, minOf(row.size, buffer.remaining()))
        }
        buffer.rewind()
        return frame
    }
}

/** `AV_PIX_FMT_YUV420P`, named here so the probe does not import the whole avutil surface. */
private const val AV_PIX_FMT_YUV420P = 0
