package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.AcceleratedFrameGrabber
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** EK-002: non-8K material keeps the existing decoder, including seek and restart semantics. */
class AcceleratedFrameGrabberTest {
    @Test
    fun `ordinary video never attempts CUDA and retains software seek frames`() {
        val reference = FreshFrameGrabber(SampleMedia.file("rot90.mp4"))
        val candidate = AcceleratedFrameGrabber(SampleMedia.file("rot90.mp4"))
        for (grabber in listOf(reference, candidate)) {
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.imageWidth = 160
            grabber.imageHeight = 90
        }
        try {
            reference.start()
            candidate.start()
            assertFalse(candidate.hardwareActive)
            assertNull(candidate.accelerationFailure)
            for (at in listOf(0L, 500_000L, 700_000L, 200_000L)) {
                reference.setTimestamp(at)
                candidate.setTimestamp(at)
                repeat(3) {
                    val expected = checkNotNull(reference.grabImage())
                    val actual = checkNotNull(candidate.grabImage())
                    assertEquals(expected.timestamp, actual.timestamp)
                    val expectedHash = CRC32().apply { update((expected.image[0] as ByteBuffer).duplicate()) }
                    val actualHash = CRC32().apply { update((actual.image[0] as ByteBuffer).duplicate()) }
                    assertEquals(expectedHash.value, actualHash.value)
                }
            }
            candidate.restart()
            assertEquals(0L, checkNotNull(candidate.grabImage()).timestamp)
            assertFalse(candidate.hardwareActive)
        } finally {
            candidate.release()
            candidate.release()
            reference.release()
        }
    }
}
