package com.woka.feature.editor.save

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File

/** `AV_PIX_FMT_YUV420P`. Named here rather than importing the whole avutil surface. */
private const val PIX_FMT_YUV420P = 0

/** libaom's constant-quality target. 20 is what ME-172 encoded the corpus fixture at. */
private const val AV1_QUALITY = 20.0

/**
 * A single picture written as AVIF, through the bundled ffmpeg (ME-181).
 *
 * **The first thing in this repository that encodes with anything but Skia.** It exists
 * because `EncodedImageFormat` has no AVIF entry and `formatFor` answers null for the
 * extension — so the one format genuine HDR photographs arrive in is a format Woka could
 * read and never write. `To SDR` has to put pixels back into the user's own `.avif`, and
 * this is the only road there.
 *
 * **Bytes out, not a file written.** The recorder insists on a path, so one is used and
 * deleted; what comes back is a `ByteArray` so this drops into [writeAtomically] exactly
 * where `encodeToData` does. The atomic replace stays the single place that touches the
 * real path, which is what keeps a failed encode from leaving a half-written photograph.
 *
 * Proved by `FfmpegEncodeProbe` before a line of this was written: 64×48 grey in, read
 * back at 64×48, tagged SDR, centre 124 against 128 — lossy AV1's own cost and nothing
 * else's.
 */
internal fun encodeSdrAvif(bitmap: ImageBitmap): ByteArray {
    val scratch = File.createTempFile("woka-encode", ".avif")
    try {
        record(bitmap, scratch)
        return scratch.readBytes()
    } finally {
        scratch.delete()
    }
}

/**
 * **BT.709 throughout, and that is the point of the function.** The bitmap arriving here
 * has already been tone mapped — `displayable` did it at the decode (ME-177) — so what is
 * being written is ordinary SDR light. Tagging it PQ would describe pixels that no longer
 * exist and hand the next reader a second tone map to apply.
 */
private fun record(bitmap: ImageBitmap, target: File) {
    val recorder = FFmpegFrameRecorder(target, bitmap.width, bitmap.height, 0)
    recorder.format = "avif"
    // The named external encoder, not an `AV_CODEC_ID`: the id resolves to whichever
    // built-in AV1 encoder a build happens to carry, and libaom is the one ME-010
    // measured as writing correctly.
    recorder.videoCodecName = "libaom-av1"
    recorder.pixelFormat = PIX_FMT_YUV420P
    recorder.frameRate = 1.0
    recorder.videoQuality = AV1_QUALITY
    // **`still-picture`, which the CLI recipe in the corpus README carries and this did
    // not.** Without it libaom writes a one-frame *sequence*, and an AVIF sequence is
    // read back as a tiled image — which is why the round trip came out with the right
    // half black and its rows out of order, identically through two unrelated ways of
    // building the frame.
    recorder.setVideoOption("still-picture", "1")
    recorder.gopSize = 1
    recorder.setVideoOption("color_primaries", "bt709")
    recorder.setVideoOption("color_trc", "bt709")
    recorder.setVideoOption("colorspace", "bt709")
    recorder.start()
    // **The converter outlives the `record` call, deliberately.** `getFrame` hands back a
    // `Frame` whose buffer the converter owns and frees on close, so letting it escape a
    // `use` block and recording afterwards reads memory that has been released — which
    // encodes a black picture, and only sometimes. `FfmpegStillDecoder` carries the same
    // warning about the grabber for the same reason.
    Java2DFrameConverter().use { converter ->
        try {
            recorder.record(frameOf(bitmap, converter))
        } finally {
            recorder.stop()
            recorder.release()
        }
    }
}

/**
 * The bitmap as a frame the recorder accepts.
 *
 * **Through `Java2DFrameConverter`, and that is a correction.** This filled a
 * three-channel `Frame` by hand first — `readPixels()` into a BGR buffer, row by row
 * against `imageStride`. Every number checked out (96×64: stride 288, capacity 18432,
 * source 24576, no padding, whole buffer written) and the picture still came out wrong:
 * the right half black and the rows walking. `FfmpegStillEncoderTest` caught it because
 * it paints four different quadrants; the flat grey the earlier probe used had passed
 * the same broken code.
 *
 * The measurements said the buffer was right, so the fault was in what the recorder does
 * with a hand-built `Frame` — and rather than keep guessing at an undocumented contract,
 * this uses the converter JavaCV ships for the purpose.
 *
 * **It costs a `BufferedImage`**, which on a 36-megapixel source is 138 MB of JVM heap
 * (`EditorProxy` records the same figure). That is affordable exactly here and nowhere
 * else: a save happens once, on an IO thread, and the bitmap is dropped immediately
 * after — the same trade `ImageClipboard` already makes with `toAwtImage`.
 */
private fun frameOf(bitmap: ImageBitmap, converter: Java2DFrameConverter): Frame {
    val source = bitmap.toAwtImage()
    // **Repainted as `TYPE_3BYTE_BGR` first, and that is the fix.** `toAwtImage` hands
    // back `TYPE_INT_ARGB`, whose raster is int-packed; the converter turns it into a
    // four-channel frame that the recorder wrote out black. Three interleaved bytes is
    // the layout `AV_PIX_FMT_BGR24` *is*, so nothing has to be inferred.
    val bgr = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_3BYTE_BGR)
    val canvas = bgr.createGraphics()
    try {
        canvas.drawImage(source, 0, 0, null)
    } finally {
        canvas.dispose()
    }
    return converter.getFrame(bgr)
}
