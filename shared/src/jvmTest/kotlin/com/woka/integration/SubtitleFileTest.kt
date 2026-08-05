package com.woka.integration

import com.woka.feature.viewer.video.engine.subtitle.SUBTITLE_FILE_EXTENSIONS
import com.woka.feature.viewer.video.engine.subtitle.isBitmapSubtitleFile
import com.woka.feature.viewer.video.engine.subtitle.scanExternalSubtitles
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sidecar scan. Encodings and parsing are `SubtitleCharsetTest`.
 *
 * A scan is anchored on a path that does not have to exist: E03 and E04 are in
 * the corpus only as sidecars — a `.sup` and an `.idx`/`.sub` pair, with no video
 * of their own — and the scan reads the stem and the folder, never the file.
 */
private fun scanOf(number: Int) =
    scanExternalSubtitles(File(SampleMedia.root, SampleMedia.episode(number, ".mkv")))

class SubtitleFileTest {

    @Test
    fun `every sidecar sharing the video's stem is found`() {
        val found = scanOf(1).map { File(it.path).name }
        assertEquals(3, found.size, found.toString())
        assertTrue(found.any { it.endsWith(".TEST.srt") })
        assertTrue(found.any { it.endsWith(".ru.srt") })
        assertTrue(found.any { it.endsWith(".rus.srt") })
    }

    @Test
    fun `a sidecar of one episode does not attach to another`() {
        val second = scanOf(2).map { File(it.path).name }
        assertEquals(2, second.size, second.toString())
        assertTrue(second.all { it.contains("S01E02") }, second.toString())
        assertTrue(scanOf(1).none { it.path.contains("S01E02") })
    }

    @Test
    fun `the language suffix is read off the name`() {
        assertEquals(setOf("ru", "rus", null), scanOf(1).map { it.language }.toSet())
        assertEquals(setOf("en", null), scanOf(2).map { it.language }.toSet())
    }

    @Test
    fun `an exact-name sidecar claims no language and is listed last`() {
        val entries = scanOf(1)
        assertNull(entries.last().language, "an unlabelled sidecar outranked a labelled one")
        assertEquals("Subtitle file", entries.last().label)
    }

    @Test
    fun `the format is what it is called on screen`() {
        assertEquals(setOf("SRT"), scanOf(1).map { it.format }.toSet())
        assertEquals(setOf("SRT", "ASS"), scanOf(2).map { it.format }.toSet())
        assertEquals(listOf("PGS"), scanOf(3).map { it.format })
        assertEquals(listOf("VobSub"), scanOf(4).map { it.format })
    }

    @Test
    fun `a bare sub file is not offered beside the idx that owns it`() {
        val fourth = scanOf(4)
        assertEquals(1, fourth.size, fourth.map { it.fileName }.toString())
        assertTrue(fourth.single().fileName.endsWith(".idx"))
        assertFalse(SUBTITLE_FILE_EXTENSIONS.contains("sub"))
    }

    @Test
    fun `bitmap sidecars are recognised as such and text ones are not`() {
        assertTrue(isBitmapSubtitleFile("D:/a/movie.sup"))
        assertTrue(isBitmapSubtitleFile("D:/a/movie.IDX"))
        assertFalse(isBitmapSubtitleFile("D:/a/movie.srt"))
        assertFalse(isBitmapSubtitleFile("D:/a/movie.ass"))
        assertFalse(isBitmapSubtitleFile("D:/a/movie"))
    }

    @Test
    fun `the extension is matched whatever its case`() {
        val dir = Files.createTempDirectory("woka-subs").toFile()
        try {
            val video = File(dir, "Clip.mkv").also { it.writeText("x") }
            listOf("Clip.SRT", "Clip.ru.Ass", "Clip.EN.srt").forEach {
                File(dir, it).writeText("x")
            }
            assertEquals(3, scanExternalSubtitles(video).size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an unrelated subtitle in the same folder is left alone`() {
        val dir = Files.createTempDirectory("woka-subs").toFile()
        try {
            val video = File(dir, "Clip.mkv").also { it.writeText("x") }
            File(dir, "SomethingElse.srt").writeText("x")
            File(dir, "Clip.ru.srt").writeText("x")
            assertEquals(listOf("Clip.ru.srt"), scanExternalSubtitles(video).map { it.fileName })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a folder with no sidecars scans to nothing`() {
        val dir = Files.createTempDirectory("woka-subs").toFile()
        try {
            val video = File(dir, "Clip.mkv").also { it.writeText("x") }
            assertTrue(scanExternalSubtitles(video).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
