package com.woka.integration

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.engine.decode.DoviColourConverter
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.PictureGraph
import com.woka.feature.viewer.video.engine.probe.UnsupportedVideoException
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_METADATA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoviColourPathTest {
    @Test
    fun `P5 matches reference pixels and sequential seek restart keep the same frame`() {
        FreshFrameGrabber(sample()).use { grabber ->
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.setVideoOption("threads", "4")
            grabber.start()
            assertEquals(TransferKind.PQ, grabber.readStreamHdrInfo().transfer)
            var frame: Frame? = null
            repeat(301) { frame = assertNotNull(grabber.grabImage()) }
            val sequential = bytes(assertNotNull(frame))
            assertEquals(5_000_000L, frame!!.timestamp)
            // BtbN/libplacebo v7.387.0 P010 MAIN reference, Jellyfin at frame 300 (DV5-101).
            for ((point, rgb) in listOf((100 to 100) to listOf(125, 102, 146), (1143 to 599) to listOf(30, 3, 44))) {
                for (c in 0..2) assertTrue(abs((sequential[point.second * frame!!.imageStride + point.first * 4 + c].toInt() and 255) - rgb[c]) <= 1)
            }
            val metadataCount = grabber.lastPicture!!.nb_side_data()
            val hdr = grabber.readStreamHdrInfo().readFrom(grabber.lastPicture)
            assertTrue(assertNotNull(hdr.whitePoint) > assertNotNull(hdr.scenePeak))
            assertNotNull(grabber.grabImageAt(1_000_000L))
            assertContentEquals(sequential, bytes(assertNotNull(grabber.grabImageAt(5_000_000L))))
            assertEquals(metadataCount, grabber.lastPicture!!.nb_side_data())
            grabber.restart()
            assertEquals(ImageMode.COLOR, grabber.imageMode)
            assertContentEquals(sequential, bytes(assertNotNull(grabber.grabImageAt(5_000_000L))))
        }
    }

    @Test
    fun `RAW geometry receives reconstructed RGBA with matching peaks`() {
        FreshFrameGrabber(sample()).use { grabber ->
            grabber.imageMode = ImageMode.RAW
            grabber.start()
            val frame = assertNotNull(grabber.grabImageAt(5_000_000L))
            val picture = frame.opaque as AVFrame
            assertEquals(ImageMode.RAW, grabber.imageMode)
            assertEquals(AV_PIX_FMT_RGBA, picture.format())
            val before = grabber.readStreamHdrInfo().readFrom(picture)
            PictureGraph(grabber.formatContext.streams(grabber.videoStream).time_base(), 0, 0, false, 1).use { graph ->
                val rotated = assertNotNull(graph.process(picture))
                assertEquals(1080, rotated.imageWidth)
                assertEquals(1920, rotated.imageHeight)
                assertEquals(before, grabber.readStreamHdrInfo().readFrom(rotated.opaque as AVFrame))
            }
        }
    }

    @Test
    fun `BGR poster and RGBA preview use the same resized colour`() {
        fun resized(format: Int): ByteArray = FreshFrameGrabber(sample()).use { grabber ->
            grabber.pixelFormat = format
            grabber.imageWidth = 400
            grabber.imageHeight = 226
            grabber.start()
            val frame = assertNotNull(grabber.grabImageAt(5_000_000L))
            assertEquals(400, frame.imageWidth)
            assertEquals(226, frame.imageHeight)
            val result = ByteArray(400 * 226 * frame.imageChannels)
            val buffer = frame.image[0] as ByteBuffer
            for (y in 0 until 226) {
                buffer.position(y * frame.imageStride)
                buffer.get(result, y * 400 * frame.imageChannels, 400 * frame.imageChannels)
            }
            result
        }
        val rgba = resized(AV_PIX_FMT_RGBA)
        val bgr = resized(AV_PIX_FMT_BGR24)
        for (pixel in 0 until 400 * 226 step 31) for (c in 0..2) {
            assertTrue(abs((rgba[pixel * 4 + c].toInt() and 255) - (bgr[pixel * 3 + 2 - c].toInt() and 255)) <= 2)
        }
    }

    @Test
    fun `missing RPU never reuses the previous conversion`() {
        FreshFrameGrabber(sample(), allowUnsupportedColour = true).use { grabber ->
            grabber.imageMode = ImageMode.RAW
            grabber.start()
            DoviColourConverter().use { converter ->
                converter.convert(assertNotNull(grabber.grabImage()))
                val next = assertNotNull(grabber.grabImage())
                av_frame_remove_side_data(next.opaque as AVFrame, AV_FRAME_DATA_DOVI_METADATA)
                assertFailsWith<UnsupportedVideoException> { converter.convert(next) }
                converter.convert(assertNotNull(grabber.grabImage()))
            }
        }
    }

    private fun bytes(frame: Frame): ByteArray {
        val buffer = frame.image[0] as ByteBuffer
        buffer.rewind()
        return ByteArray(frame.imageStride * frame.imageHeight).also { buffer.get(it) }
    }

    private fun sample(): File = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4").also {
        assumeTrue("The author's P5 sample is required", it.isFile)
    }
}
