package com.woka.integration

import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.engine.video.render.convertToMp4
import com.woka.probe.readBack
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * "Save as MP4" on the corpus' own rescue formats (VF-104): a Dahua `.dav` holds H.264 and
 * is copied, a RealVideo `.rm` holds nothing `mp4` plays and is encoded. Both must come out
 * as a decodable `.mp4` beside a copy of the source, which is left as it was.
 */
class SaveAsMp4Test {

    private fun converted(fixture: String, block: (source: File, out: File) -> Unit) {
        val dir = Files.createTempDirectory("woka-mp4").toFile()
        try {
            val source = SampleMedia.file(fixture).copyTo(File(dir, fixture))
            val before = source.readBytes()
            val result = convertToMp4(source, alive = { true }, progress = {})
            val out = File(assertIs<EditSaveResult.Saved>(result, "$fixture: $result").path)
            assertTrue(source.readBytes().contentEquals(before), "$fixture was touched")
            block(source, out)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an H264 dav is copied into an mp4 of its own name`() = converted("dahua.dav") { _, out ->
        assertEquals("dahua.mp4", out.name)
        val back = readBack(out)
        assertTrue(back.firstDecodes && back.frames > 10, "the copy does not play: $back")
    }

    @Test
    fun `a RealVideo file is encoded`() = converted("realvideo.rm") { _, out ->
        assertEquals("realvideo.mp4", out.name)
        val back = readBack(out)
        assertTrue(back.firstDecodes && back.frames > 10, "the encode does not play: $back")
    }
}
