package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `rot90.mp4`'s display matrix is handed to every decoded frame as side data by FFmpeg 8,
 * so the phone-video fixture is enough to show the RGBA frame's side data piling up (ME-237).
 */
private const val ROT90 = "rot90.mp4"

/** ME-237: the RGBA picture carries one frame's side data, however long the film plays. */
class FreshFrameGrabberTest {

    @Test
    fun `the RGBA frame holds one frame's side data after a whole run`() {
        val counts = sideDataCounts(FreshFrameGrabber(SampleMedia.file(ROT90)))
        assertTrue(counts.first() > 0, "the fixture no longer carries frame side data")
        assertEquals(counts.first(), counts.last(), "side data piled up across ${counts.size} pictures")
    }

    /** The defect itself, so the test above cannot pass by not looking. */
    @Test
    fun `JavaCV's own grabber piles it up`() {
        val counts = sideDataCounts(FFmpegFrameGrabber(SampleMedia.file(ROT90)))
        assertTrue(counts.last() > counts.first(), "JavaCV no longer appends — ME-237 can go")
    }

    private fun sideDataCounts(grabber: FFmpegFrameGrabber): List<Int> {
        grabber.pixelFormat = AV_PIX_FMT_RGBA
        grabber.start()
        try {
            return generateSequence { grabber.grabImage() }
                .map { (it.opaque as AVFrame).nb_side_data() }
                .toList()
        } finally {
            grabber.stop()
            grabber.release()
        }
    }
}
