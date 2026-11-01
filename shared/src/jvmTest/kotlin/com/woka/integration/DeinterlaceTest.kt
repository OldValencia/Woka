package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.DeinterlaceGraph
import com.woka.feature.viewer.video.engine.decode.setDeinterlace
import com.woka.feature.viewer.video.engine.seek
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.SampleMedia
import com.woka.support.awaitTrue
import com.woka.support.combScore
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 720×576 MPEG-2, 25 frames, every one flagged interlaced TFF (feature doc §7). */
private const val INTERLACED = "interlaced.ts"

/** One 25 fps frame, in µs: how far the graph's picture trails the grabber. */
private const val FRAME_US = 40_000L

/** VF-102 / VF-106: deinterlacing in the player, decided by the frames' own flag. */
class DeinterlaceTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the graph takes the comb out of an interlaced frame, one frame behind`() {
        val raw = FFmpegFrameGrabber(SampleMedia.file(INTERLACED)).apply {
            pixelFormat = AV_PIX_FMT_RGBA
            start()
        }
        val decoded = FFmpegFrameGrabber(SampleMedia.file(INTERLACED)).apply {
            imageMode = FrameGrabber.ImageMode.RAW
            start()
        }
        val base = decoded.formatContext.streams(decoded.videoStream).time_base()
        val graph = DeinterlaceGraph(base, 0, 0)
        try {
            repeat(12) { raw.grabImage() }
            val rawComb = combScore(assertNotNull(raw.grabImage()))
            repeat(13) { graph.process(decoded.grabImage().opaque as AVFrame) }
            val pushedUs = decoded.timestamp
            val picture = assertNotNull(graph.process(decoded.grabImage().opaque as AVFrame))
            assertEquals(pushedUs, picture.timestamp, "the picture is not the frame before the push")
            assertEquals(720 to 576, picture.imageWidth to picture.imageHeight)
            val comb = combScore(picture)
            assertTrue(rawComb > 2.0, "the fixture no longer combs: $rawComb")
            assertTrue(comb < rawComb / 2, "bwdif left $comb of $rawComb")
            assertTrue(decoded.timestamp - picture.timestamp == FRAME_US)
        } finally {
            graph.close()
            decoded.close()
            raw.close()
        }
    }

    @Test
    fun `an interlaced file is found so and plays through the graph`() {
        onPlayer(SampleMedia.file(INTERLACED)) { player ->
            awaitTrue("the interlaced flag to be seen", timeoutMs = 15_000L) { player.deinterlace.isInterlaced }
            awaitTrue("the grabber to re-open behind the graph", timeoutMs = 15_000L) {
                player.deinterlace.filtering
            }
            awaitTrue("a picture to reach the screen", timeoutMs = 15_000L) { player.currentFrame != null }
        }
    }

    @Test
    fun `a seek behind the graph lands a picture`() {
        onPlayer(SampleMedia.file(INTERLACED)) { player ->
            awaitTrue("the graph", timeoutMs = 15_000L) { player.deinterlace.filtering && !player.isReloadingVideo }
            awaitTrue("a picture", timeoutMs = 15_000L) { player.currentFrame != null }
            // Paused, so the only new picture can come from the seek's landing.
            player.isPlaying = false
            waitFor(300L) { false }
            val before = player.currentFrame
            player.seek(500L)
            awaitTrue("a picture after the seek", timeoutMs = 15_000L) { player.currentFrame !== before }
            assertTrue(player.deinterlace.filtering, "the seek left the graph")
        }
    }

    @Test
    fun `Off leaves an interlaced file on the plain path and still says it is interlaced`() {
        onPlayer(SampleMedia.file(INTERLACED)) { player ->
            player.setDeinterlace(false)
            awaitTrue("the interlaced flag to be seen", timeoutMs = 15_000L) { player.deinterlace.isInterlaced }
            awaitTrue("no re-open left pending", timeoutMs = 15_000L) {
                player.pendingVideoReopenIndex == null && !player.isReloadingVideo
            }
            assertFalse(player.deinterlace.filtering, "Off still filters")
        }
    }

    @Test
    fun `a progressive file never shows the row or opens a graph`() {
        onPlayer(SampleMedia.file(CHAPTERS)) { player ->
            awaitTrue("a picture to reach the screen", timeoutMs = 15_000L) { player.currentFrame != null }
            waitFor(1_000L) { player.deinterlace.isInterlaced }
            assertFalse(player.deinterlace.isInterlaced, "a progressive file was flagged interlaced")
            assertFalse(player.deinterlace.filtering)
        }
    }
}
