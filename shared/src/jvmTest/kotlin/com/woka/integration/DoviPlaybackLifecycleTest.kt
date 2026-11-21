package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.PictureGraph
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVFrame.AV_FRAME_FLAG_INTERLACED
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoviPlaybackLifecycleTest {
    @Test
    fun `a delayed geometry frame keeps its original RPU while the decoder advances`() {
        val file = File("D:/Movies/DV samples/Mystery Box Dolby Vision Profile 5.mp4")
        assumeTrue("The author's 4K P5 sample is required", file.isFile)
        FreshFrameGrabber(file).use { grabber ->
            grabber.imageMode = ImageMode.RAW
            grabber.start()
            val first = assertNotNull(grabber.grabImageAt(1_001_000L))
            val picture = first.opaque as AVFrame
            val expected = grabber.readStreamHdrInfo().readFrom(picture)
            val firstTime = first.timestamp
            picture.flags(picture.flags() or AV_FRAME_FLAG_INTERLACED)
            val timeBase = grabber.formatContext.streams(grabber.videoStream).time_base()
            PictureGraph(timeBase, 480, 270, true, 0).use { graph ->
                assertNull(graph.process(picture))
                val next = assertNotNull(grabber.grabImageAt(10_010_000L))
                assertNotEquals(expected.scenePeak, grabber.readStreamHdrInfo().readFrom(grabber.lastPicture).scenePeak)
                val delayed = assertNotNull(graph.process(next.opaque as AVFrame))
                assertEquals(firstTime, delayed.timestamp)
                assertEquals(expected, grabber.readStreamHdrInfo().readFrom(graph.lastPicture))
            }
        }
    }

    @Test
    fun `4K changing RPU stays attached to its pixels through backward seek and EOF`() {
        val file = File("D:/Movies/DV samples/Mystery Box Dolby Vision Profile 5.mp4")
        assumeTrue("The author's 4K P5 sample is required", file.isFile)
        FreshFrameGrabber(file).use { grabber ->
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.imageWidth = 480
            grabber.imageHeight = 270
            grabber.setVideoOption("threads", "4")
            grabber.p5Playback = true
            grabber.start()
            assertEquals("4", grabber.videoOptions["threads"], "Playback tuning must not overwrite the caller's options")
            val frames = mutableMapOf<Int, Pair<Int, HdrInfo>>()
            var sideCount = 0
            repeat(601) { index ->
                val frame = assertNotNull(grabber.grabImage())
                if (index in listOf(60, 300, 600)) {
                    frames[index] = hash(frame) to grabber.readStreamHdrInfo().readFrom(grabber.lastPicture)
                    sideCount = assertNotNull(grabber.lastPicture).nb_side_data()
                }
            }
            assertNotEquals(frames.getValue(60).second.scenePeak, frames.getValue(600).second.scenePeak)
            for (index in listOf(300, 60, 600, 60, 300)) {
                val frame = assertNotNull(grabber.grabImageAt(index * 1_001_000L / 60))
                assertEquals(frames.getValue(index).first, hash(frame))
                assertEquals(frames.getValue(index).second, grabber.readStreamHdrInfo().readFrom(grabber.lastPicture))
                assertEquals(sideCount, assertNotNull(grabber.lastPicture).nb_side_data())
            }
            assertNotNull(grabber.grabImageAt(grabber.lengthInTime - 250_000L))
            var remaining = 0
            while (grabber.grabImage() != null) {
                remaining++
                assertTrue(remaining < 30, "Seek must land near EOF")
            }
            assertNull(grabber.grabImage())
            grabber.restart()
            assertEquals("4", grabber.videoOptions["threads"])
            val restarted = assertNotNull(grabber.grabImageAt(1_001_000L))
            assertEquals(frames.getValue(60).first, hash(restarted))
            assertEquals(480, restarted.imageWidth)
            assertEquals(270, restarted.imageHeight)
        }
    }

    private fun hash(frame: Frame): Int {
        val buffer = (frame.image[0] as ByteBuffer).duplicate()
        var hash = 1
        for (y in 0 until frame.imageHeight) for (x in 0 until frame.imageWidth * 4) {
            hash = 31 * hash + buffer.get(y * frame.imageStride + x)
        }
        return hash
    }
}
