package com.woka.integration

import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.feature.viewer.video.engine.decode.PictureGraph
import com.woka.feature.viewer.video.engine.probe.clockwiseTurns
import com.woka.fixtures.SampleMedia
import com.woka.support.awaitTrue
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.videoCopy
import com.woka.support.withTempStorageRoot
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 160×96 H.264 stored landscape, display matrix 90 (`ffprobe` rotation) — shown 96×160. */
private const val ROT90 = "rot90.mp4"

/** The same picture, display matrix 180 — shown upside down the right way up. */
private const val ROT180 = "rot180.mp4"

/** Per-channel slack between the grabber's swscale and the graph's. */
private const val TOLERANCE = 16

/** VF-105: a video's display matrix honoured in the player, the poster and the previews. */
class RotationTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the grabber reads the turn the stream asks for`() {
        assertEquals(3, turnsOf(ROT90))
        assertEquals(2, turnsOf(ROT180))
        assertEquals(0, turnsOf(SampleMedia.episode(2, ".mkv")))
    }

    @Test
    fun `the graph turns a 90 clip upright, as the CLI's autorotate does`() {
        val stored = firstRgba(ROT90)
        val shown = graphPicture(ROT90, turns = 3)
        assertEquals(96 to 160, shown.imageWidth to shown.imageHeight)
        // Counter-clockwise: shown(x, y) is stored(W − 1 − y, x).
        for ((x, y) in listOf(10 to 10, 80 to 20, 40 to 140, 90 to 150)) {
            assertClose(pixel(stored, stored.imageWidth - 1 - y, x), pixel(shown, x, y), "($x, $y)")
        }
    }

    @Test
    fun `the player shows a rotated clip upright`() {
        onPlayer(SampleMedia.file(ROT90)) { player ->
            awaitTrue("the graph", timeoutMs = 15_000L) { player.pictureFilter.filtering }
            awaitTrue("a picture", timeoutMs = 15_000L) { player.currentFrame != null }
            val frame = assertNotNull(player.currentFrame)
            assertTrue(frame.height > frame.width, "still on its side: ${frame.width}×${frame.height}")
        }
    }

    @Test
    fun `the poster of a rotated clip is upright`() {
        withTempStorageRoot {
            val poster = assertNotNull(VideoThumbnailExtractor.load(videoCopy(ROT90, "portrait.mp4")))
            assertTrue(poster.height > poster.width, "poster on its side: ${poster.width}×${poster.height}")
        }
    }

    private fun turnsOf(name: String): Int = FFmpegFrameGrabber(SampleMedia.file(name)).run {
        start()
        try { clockwiseTurns } finally { close() }
    }

    private fun firstRgba(name: String): Frame = FFmpegFrameGrabber(SampleMedia.file(name)).run {
        pixelFormat = AV_PIX_FMT_RGBA
        start()
        try { assertNotNull(grabImage()).clone() } finally { close() }
    }

    private fun graphPicture(name: String, turns: Int): Frame {
        val grabber = FFmpegFrameGrabber(SampleMedia.file(name))
        grabber.imageMode = FrameGrabber.ImageMode.RAW
        grabber.start()
        val base = grabber.formatContext.streams(grabber.videoStream).time_base()
        val graph = PictureGraph(base, 0, 0, deinterlace = false, clockwiseTurns = turns)
        try {
            return assertNotNull(graph.process(grabber.grabImage().opaque as AVFrame)).clone()
        } finally {
            graph.close()
            grabber.close()
        }
    }

    private fun pixel(frame: Frame, x: Int, y: Int): List<Int> {
        val buffer = frame.image[0] as ByteBuffer
        val at = y * frame.imageStride + x * 4
        return (0 until 3).map { buffer.get(at + it).toInt() and 0xFF }
    }

    private fun assertClose(expected: List<Int>, actual: List<Int>, where: String) {
        val off = expected.zip(actual).maxOf { (e, a) -> abs(e - a) }
        assertTrue(off <= TOLERANCE, "pixel $where: expected $expected, got $actual")
    }
}
