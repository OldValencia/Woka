package com.woka.integration

import com.woka.feature.viewer.video.engine.probe.measuredDurationMs
import com.woka.fixtures.SampleMedia
import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** One 25 fps frame. */
private const val FRAME_MS = 40L

/** VF-001's counted length, against files whose real length is known. */
class ContainerDurationTest {

    private fun counted(name: String): Long =
        assertNotNull(measuredDurationMs(SampleMedia.file(name)), "$name: nothing counted")

    @Test
    fun `the count agrees with a container that states its length`() {
        val grabber = FFmpegFrameGrabber(SampleMedia.file("realvideo.rm"))
        val declared = try {
            grabber.start()
            grabber.lengthInTime / 1000L
        } finally {
            grabber.close()
        }
        val difference = abs(counted("realvideo.rm") - declared)
        assertTrue(difference <= FRAME_MS, "counted vs declared $declared ms: off by $difference ms")
    }

    @Test
    fun `a Dahua export is counted from its first packet`() {
        assertEquals(1939L, counted("dahua.dav"))
    }

    @Test
    fun `a container that states no length is counted to its last frame`() {
        listOf("noseek.roq", "nolength.amv").forEach { name ->
            val difference = abs(counted(name) - 2000L)
            assertTrue(difference <= FRAME_MS, "$name: ${counted(name)} ms for a 2 s file")
        }
    }
}
