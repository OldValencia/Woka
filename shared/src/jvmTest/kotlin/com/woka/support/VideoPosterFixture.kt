package com.woka.support

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.data.HdrViewMode
import com.woka.core.data.applyTo
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.HdrImagePainter
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.probe.toColour
import com.woka.feature.viewer.video.engine.probe.withSceneOf
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * Identical frames at every time: random poster selection cannot change the comparison.
 * These are tagged SDR codes, deliberately, to isolate the colour path from the encoder.
 * The reference decodes independently and draws the viewer's actual painter.
 */
internal fun writePosterFixture(file: File, transfer: String, flatFallback: Boolean = false) {
    assumeTrue("libopenh264 is required for the poster fixture", avcodec_find_encoder_by_name("libopenh264") != null)
    val recorder = FFmpegFrameRecorder(file, 64, 32).apply {
        format = "matroska"
        videoCodecName = "libopenh264"
        frameRate = 30.0
        gopSize = 1
        setVideoOption("color_trc", transfer)
        setVideoOption("color_primaries", if (transfer == "bt709") "bt709" else "bt2020")
    }
    try {
        recorder.start()
        Frame(64, 32, Frame.DEPTH_UBYTE, 3).use { frame ->
            val buffer = frame.image[0] as ByteBuffer
            for (y in 0 until 32) for (x in 0 until 64) {
                val offset = y * frame.imageStride + x * 3
                repeat(3) { buffer.put(offset + it, (32 + x * 3).toByte()) }
            }
            repeat(if (flatFallback) 120 else 12) { index ->
                if (flatFallback) {
                    // Every candidate is monochrome, but only the first search band is dark.
                    // A retained converter buffer would silently turn the fallback bright.
                    val level = if (index < 20) 64 else 200
                    for (offset in 0 until frame.imageStride * 32) buffer.put(offset, level.toByte())
                }
                buffer.rewind()
                recorder.record(frame, AV_PIX_FMT_BGR24)
            }
        }
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}

internal fun posterReference(
    file: File,
    mode: HdrViewMode,
    expectedTransfer: String,
    atUs: Long = 0L,
    width: Int = 0,
    height: Int = 0
): ImageBitmap {
    val grabber = FreshFrameGrabber(file).apply { pixelFormat = AV_PIX_FMT_BGR24 }
    try {
        grabber.imageWidth = width
        grabber.imageHeight = height
        grabber.start()
        val info = grabber.readStreamHdrInfo()
        assertEquals(expectedTransfer, info.transfer.name, "the fixture's colour tag was not encoded")
        val frame = requireNotNull(grabber.grabImageAt(atUs))
        val picture = frame.opaque as? AVFrame
        val colour = mode.applyTo(info.readFrom(picture).withSceneOf(picture).toColour())
        val raw = Java2DFrameConverter().getBufferedImage(frame).toComposeImageBitmap()
        if (!colour.needsColorManagement) return raw
        try {
            val painter = HdrImagePainter(raw, colour.transfer, colour.isWideGamut,
                whitePoint = colour.whitePoint ?: ASSUMED_WHITE_POINT)
            val size = Size(raw.width.toFloat(), raw.height.toFloat())
            return Surface.makeRasterN32Premul(raw.width, raw.height).use { surface ->
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), size) {
                    with(painter) { draw(size) }
                }
                surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
            }
        } finally {
            raw.asSkiaBitmap().close()
        }
    } finally {
        runCatching { grabber.stop() }
        grabber.release()
    }
}

internal fun posterDifference(first: ImageBitmap, second: ImageBitmap): Float {
    assertEquals(first.width, second.width)
    assertEquals(first.height, second.height)
    val a = first.toPixelMap()
    val b = second.toPixelMap()
    var largest = 0f
    for (y in 0 until first.height) for (x in 0 until first.width) {
        largest = maxOf(largest, abs(a[x, y].red - b[x, y].red),
            abs(a[x, y].green - b[x, y].green), abs(a[x, y].blue - b[x, y].blue))
    }
    return largest * 255f
}
