package com.woka.integration

import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** One 25 fps frame, the tolerance of every landing below. */
private const val FRAME_US = 40_000L

/**
 * VF-001: the containers added in 1.0.4, opened the way the player opens them and
 * read through the timeline helpers every video path shares.
 */
class VideoFormatsTest {

    private fun open(name: String) = FFmpegFrameGrabber(SampleMedia.file(name)).apply {
        pixelFormat = AV_PIX_FMT_RGBA
        start()
    }

    private fun <T> onGrabber(name: String, block: (FFmpegFrameGrabber) -> T): T {
        val grabber = open(name)
        try {
            return block(grabber)
        } finally {
            grabber.close()
        }
    }

    @Test
    fun `each new container decodes a first frame of its size and shows a length`() {
        val sizes = mapOf(
            "dahua.dav" to (1920 to 1080),
            "blank.bik" to (640 to 480),
            "realvideo.rm" to (160 to 120),
            "pal.gxf" to (720 to 576)
        )
        sizes.forEach { (name, size) ->
            onGrabber(name) { grabber ->
                val frame = assertNotNull(grabber.grabImage(), "$name decoded nothing")
                assertEquals(size, frame.imageWidth to frame.imageHeight, name)
                assertTrue(grabber.lengthMs(SampleMedia.file(name)) > 0, "$name shows no length")
            }
        }
    }

    @Test
    fun `a Dahua export is timed from its first frame, not from its wall clock`() {
        onGrabber("dahua.dav") { grabber ->
            grabber.grabImage()
            assertEquals(0L, grabber.mediaTimeUs)
            // `dhav`'s own figure for the cut export is 1000 ms; its packets run 1939.
            assertEquals(1939L, grabber.lengthMs(SampleMedia.file("dahua.dav")))
        }
    }

    @Test
    fun `a container that cannot seek still lands on the target`() {
        onGrabber("noseek.roq") { grabber ->
            val refused = runCatching { grabber.setTimestamp(1_000_000L) }.isFailure
            assertTrue(refused, "fixture: noseek.roq was expected to refuse a seek")
        }
        onGrabber("noseek.roq") { grabber ->
            assertNotNull(grabber.grabImageAt(1_000_000L), "nothing landed")
            assertTrue(grabber.mediaTimeUs in 1_000_000L until 1_000_000L + FRAME_US, "landed at ${grabber.mediaTimeUs}")
        }
    }
}
