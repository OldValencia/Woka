package com.woka.integration

import androidx.compose.ui.graphics.asSkiaBitmap
import com.woka.core.data.HdrViewMode
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.posterFrame
import com.woka.feature.viewer.video.engine.saveVideoPoster
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.support.posterDifference
import com.woka.support.posterReference
import com.woka.support.withTempStorageRoot
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacv.Java2DFrameConverter
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Optional real-file regression; the deterministic colour and cache tests always run. */
class RealVideoPosterHdrTest {
    @Test
    fun `P8 poster uses its declared 244 nit peak and matches the viewer on the same frame`() = withTempStorageRoot { root ->
        val file = File("D:/Movies/DV samples/Test Jellyfin 4K DV P8.1.mp4")
        assumeTrue("The author's P8.1 sample is not present", file.isFile)
        val grabber = FreshFrameGrabber(file).apply {
            pixelFormat = AV_PIX_FMT_BGR24
            imageWidth = 400
            imageHeight = 225
        }
        val converter = Java2DFrameConverter()
        try {
            grabber.start()
            for (seconds in listOf(5L, 10L, 20L)) {
                val frame = requireNotNull(grabber.grabImageAt(seconds * 1_000_000L))
                val candidate = grabber.posterFrame(frame, converter)
                assertEquals(2.44f, candidate.colour.whitePoint)
                val actual = saveVideoPoster(candidate, 0, File(root, "$seconds.webp"), "real-poster", HdrViewMode.Auto)
                val expected = posterReference(file, HdrViewMode.Auto, "PQ", seconds * 1_000_000L, 400, 225)
                try {
                    val difference = posterDifference(expected, actual)
                    assertTrue(difference <= 1f, "P8.1 at ${seconds}s differs by $difference RGB levels")
                    println("P8POSTER seconds=$seconds whitePoint=${candidate.colour.whitePoint} maxRgbDifference=$difference")
                } finally {
                    expected.asSkiaBitmap().close()
                    ImageBitmapCache.remove("real-poster")
                    actual.asSkiaBitmap().close()
                }
            }
        } finally {
            runCatching { grabber.stop() }
            grabber.release()
            converter.close()
        }
    }
}
