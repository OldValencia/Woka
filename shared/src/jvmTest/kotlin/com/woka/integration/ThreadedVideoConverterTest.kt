package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.ThreadedVideoConverter
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** EK-002: the ordinary fixture's display matrix catches per-frame side-data accumulation. */
class ThreadedVideoConverterTest {
    @Test
    fun `reused conversion preserves pixels and only the current frame metadata`() {
        val source = FreshFrameGrabber(SampleMedia.file("rot90.mp4"))
        val reference = FreshFrameGrabber(SampleMedia.file("rot90.mp4"))
        source.imageMode = FrameGrabber.ImageMode.RAW
        reference.pixelFormat = AV_PIX_FMT_RGBA
        reference.imageWidth = 160
        reference.imageHeight = 90
        val converter = ThreadedVideoConverter(160, 90)
        try {
            source.start()
            reference.start()
            var count = 0
            while (true) {
                val decoded = source.grabImage() ?: break
                val expected = checkNotNull(reference.grabImage())
                val raw = decoded.opaque as AVFrame
                val actual = converter.convert(raw, decoded.timestamp)
                val converted = actual.opaque as AVFrame
                assertEquals(pixelHash(expected), pixelHash(actual))
                assertEquals(expected.timestamp, actual.timestamp)
                assertEquals(raw.flags(), converted.flags())
                assertEquals(raw.color_trc(), converted.color_trc())
                assertTrue(raw.nb_side_data() > 0, "Fixture must carry side data")
                assertEquals(raw.nb_side_data(), converted.nb_side_data())
                for (index in 0 until raw.nb_side_data()) {
                    val original = raw.side_data(index)
                    val copy = converted.side_data(index)
                    assertEquals(original.type(), copy.type())
                    assertEquals(original.size(), copy.size())
                    assertEquals(bufferHash(original.data().capacity(original.size().toLong()).asByteBuffer()),
                        bufferHash(copy.data().capacity(copy.size().toLong()).asByteBuffer()))
                }
                count++
            }
            assertTrue(count > 10)
            source.setTimestamp(0)
            val raw = checkNotNull(source.grabImage()).opaque as AVFrame
            val width = raw.width()
            try {
                raw.width(width + 2)
                assertFailsWith<IllegalStateException> { converter.convert(raw, 0) }
            } finally {
                raw.width(width)
            }
            converter.close()
            converter.close()
            assertFailsWith<IllegalStateException> { converter.convert(raw, 0) }
        } finally {
            converter.close()
            source.release()
            reference.release()
        }
    }

    private fun pixelHash(frame: Frame): Long {
        val hash = CRC32()
        val buffer = (frame.image[0] as ByteBuffer).duplicate()
        repeat(frame.imageHeight) { row ->
            buffer.limit(buffer.capacity())
            buffer.position(row * frame.imageStride)
            buffer.limit(row * frame.imageStride + frame.imageWidth * 4)
            hash.update(buffer)
        }
        return hash.value
    }

    private fun bufferHash(buffer: ByteBuffer): Long = CRC32().apply { update(buffer) }.value
}
