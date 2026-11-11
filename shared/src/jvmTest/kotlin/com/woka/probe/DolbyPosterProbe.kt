package com.woka.probe

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.ui.image.encodePng
import com.woka.feature.viewer.video.HdrImagePainter
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import com.woka.feature.viewer.video.engine.probe.declaredWhitePoint
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test

/**
 * TEMPORARY (1.0.8 P8.1 poster investigation): identical frame, raw thumbnail codes versus
 * the actual viewer painter with the frame's declared peak. No comparison of random scenes
 * or existing cache contents; no application behaviour is changed by this probe.
 */
class DolbyPosterProbe {
    @Test
    fun `compare a P8 poster with the viewer colour path`() {
        val root = File("build/dv-investigation/poster").absoluteFile.also { it.mkdirs() }
        val rows = mutableListOf<String>()
        val source = File("D:/Movies/DV samples/Test Jellyfin 4K DV P8.1.mp4")
        val grabber = FreshFrameGrabber(source)
        try {
            grabber.setVideoOption("threads", "4")
            grabber.audioStream = Int.MAX_VALUE
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.imageWidth = 400
            grabber.imageHeight = 225
            grabber.start()
            for (seconds in listOf(5, 10, 20)) {
                grabber.setTimestamp(seconds * 1_000_000L)
                val frame = grabber.grabImage() ?: error("No image")
                val peak = (frame.opaque as AVFrame).declaredWhitePoint()
                val raw = frame.toComposeBitmap()
                val mapped = drawMapped(raw, peak)
                try {
                    File(root, "$seconds-raw.png").writeBytes(raw.encodePng())
                    File(root, "$seconds-viewer.png").writeBytes(mapped.encodePng())
                    rows += "t=$seconds actualUs=${grabber.timestamp} whitePoint=$peak raw=${brightness(raw)} viewer=${brightness(mapped)}"
                } finally {
                    raw.asSkiaBitmap().close()
                    mapped.asSkiaBitmap().close()
                }
            }
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
        File(root, "results.txt").writeText(rows.joinToString("\n"))
        rows.forEach { println("DVPOSTER $it") }
    }

    private fun drawMapped(raw: ImageBitmap, peak: Float): ImageBitmap {
        val painter = HdrImagePainter(raw, whitePoint = peak)
        val size = Size(raw.width.toFloat(), raw.height.toFloat())
        return Surface.makeRasterN32Premul(raw.width, raw.height).use { surface ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), size) {
                with(painter) { draw(size) }
            }
            surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
        }
    }

    private fun brightness(bitmap: ImageBitmap): String {
        val pixels = bitmap.toPixelMap()
        var sum = 0.0
        var top = 0.0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = pixels[x, y]
            val value = (0.2126 * pixel.red + 0.7152 * pixel.green + 0.0722 * pixel.blue) * 255
            sum += value
            top = maxOf(top, value)
        }
        return "mean=${"%.2f".format(sum / (bitmap.width * bitmap.height))},peak=${"%.2f".format(top)}"
    }
}
