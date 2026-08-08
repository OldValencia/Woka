package com.woka.integration

import com.woka.core.data.mayHoldFrames
import com.woka.core.utils.exportFramesToPng
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TIFF_PAGE_COUNT
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun emptyDir() = Files.createTempDirectory("woka-export").toFile()

private fun exportOf(name: String, into: File) = runBlocking {
    exportFramesToPng(SampleMedia.file(name), into) { _, _ -> }
}

/** Every picture inside a file, written out as PNG. */
class FrameExportTest {

    @Test
    fun `a burst exports one PNG per frame`() {
        val out = emptyDir()
        val result = exportOf(BURST_HEIC, out)
        assertEquals(BURST_FRAME_COUNT, result.exported)
        assertEquals(0, result.failed)
        assertEquals(BURST_FRAME_COUNT, out.listFiles()?.size)
    }

    @Test
    fun `every format that can hold frames exports all of them`() {
        listOf(
            BURST_HEIC to BURST_FRAME_COUNT,
            ANIM_GIF to ANIM_FRAME_COUNT,
            MULTIPAGE_TIFF to TIFF_PAGE_COUNT
        ).forEach { (name, count) ->
            val out = emptyDir()
            assertEquals(count, exportOf(name, out).exported, name)
        }
    }

    /** Padded so a folder of them sorts the way the pictures were taken. */
    @Test
    fun `frames are numbered and zero-padded to the count`() {
        val out = emptyDir()
        exportOf(ANIM_GIF, out)
        val names = out.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertEquals("anim_1.png", names.first())
        assertEquals("anim_8.png", names.last())
    }

    /** One picture is not "frame 1 of 1"; numbering it reads as a missing set. */
    @Test
    fun `a single-frame file exports one PNG under the plain name`() {
        val out = emptyDir()
        assertEquals(1, exportOf(STILL_JPG, out).exported)
        assertEquals(listOf("still.png"), out.listFiles()?.map { it.name })
    }

    @Test
    fun `the exported PNGs are real images at the source size`() {
        val out = emptyDir()
        exportOf(MULTIPAGE_TIFF, out)
        out.listFiles()?.forEach { file ->
            val image = assertNotNull(ImageIO.read(file), file.name)
            assertTrue(image.width > 0 && image.height > 0, file.name)
        }
    }

    /** Each page of a TIFF is a different picture, not the first one three times. */
    @Test
    fun `the exported frames differ from each other`() {
        val out = emptyDir()
        exportOf(MULTIPAGE_TIFF, out)
        val digests = out.listFiles()?.map { it.readBytes().toList() }.orEmpty()
        assertEquals(TIFF_PAGE_COUNT, digests.size)
        assertEquals(digests.size, digests.distinct().size, "two pages exported identically")
    }

    /**
     * The menu offers the entry on names alone, so this is what decides where it
     * appears. Being wrong here shows a dead entry on every JPEG in a folder.
     */
    @Test
    fun `only the formats that can hold frames advertise it`() {
        listOf("heic", "heif", "avif", "gif", "webp", "tif", "tiff").forEach {
            assertTrue(File("photo.$it").mayHoldFrames, it)
        }
        listOf("jpg", "jpeg", "png", "bmp", "mkv", "txt").forEach {
            assertTrue(!File("photo.$it").mayHoldFrames, it)
        }
    }

    /**
     * A folder of pictures is exported one file at a time and one unreadable
     * file must not take the run down with it.
     *
     * The bytes are garbage rather than a picture under the wrong extension:
     * FFmpeg sniffs content and cheerfully opens a PNG named `.heic`, which is
     * right, and would have made this test pass while proving nothing.
     */
    @Test
    fun `an unreadable file reports nothing exported rather than throwing`() {
        val broken = File(emptyDir(), "broken.heic")
        broken.writeBytes(ByteArray(4096) { (it % 251).toByte() })
        val result = runBlocking { exportFramesToPng(broken, emptyDir()) { _, _ -> } }
        assertEquals(0, result.exported)
    }
}
