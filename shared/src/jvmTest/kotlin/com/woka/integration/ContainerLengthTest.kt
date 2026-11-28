package com.woka.integration

import com.woka.feature.viewer.video.engine.probe.containerLengthMs
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The carousel's film length (RD-502) agrees with the length the transport shows, which a
 * started grabber answers (`lengthMs`): a declared length, and `dhav`'s counted one.
 */
class ContainerLengthTest {

    private fun grabberLength(file: File): Long {
        val grabber = FFmpegFrameGrabber(file)
        try {
            grabber.start()
            return grabber.lengthMs(file)
        } finally {
            grabber.close()
        }
    }

    @Test
    fun `a declared length matches the transport's`() {
        listOf(CHAPTERS, NO_AUDIO, "blank.bik").forEach { name ->
            val file = SampleMedia.file(name)
            assertEquals(grabberLength(file), containerLengthMs(file), name)
        }
    }

    /** `dhav` declares its wall clock (1000 ms); its packets run 1939 (`VideoFormatsTest`). */
    @Test
    fun `a Dahua export is counted, not declared`() {
        assertEquals(1939L, containerLengthMs(SampleMedia.file("dahua.dav")))
    }

    @Test
    fun `a file that does not open has no length`() {
        val dir = Files.createTempDirectory("woka-length").toFile()
        try {
            val file = File(dir, "empty.mp4").apply { writeBytes(ByteArray(0)) }
            assertEquals(0L, containerLengthMs(file))
        } finally {
            dir.deleteRecursively()
        }
    }
}
