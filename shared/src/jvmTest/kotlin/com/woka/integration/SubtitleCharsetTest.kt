package com.woka.integration

import com.woka.feature.viewer.video.engine.subtitle.parseSubtitleFile
import com.woka.feature.viewer.video.engine.subtitle.readSubtitleText
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun episode(number: Int, suffix: String) =
    SampleMedia.file(SampleMedia.episode(number, suffix))

/** Reading a sidecar off disk: which encoding it is, and what it parses to. */
class SubtitleCharsetTest {

    @Test
    fun `a UTF-8 sidecar reads as itself`() {
        val text = readSubtitleText(episode(1, ".ru.srt"))
        assertTrue(text.contains("Кириллица"), "UTF-8 came back as: ${text.take(80)}")
        assertFalse(text.contains('�'), "the decoder substituted instead of deciding")
    }

    @Test
    fun `a CP1251 sidecar is not read as broken UTF-8`() {
        val text = readSubtitleText(episode(1, ".rus.srt"))
        assertTrue(text.contains("Кириллица"), "CP1251 came back as: ${text.take(80)}")
        assertFalse(text.contains('�'), "the decoder substituted instead of deciding")
    }

    @Test
    fun `an ASCII sidecar is unaffected by the guessing`() {
        val text = readSubtitleText(episode(2, ".en.srt"))
        assertTrue(text.contains("Language-suffixed"), "ASCII came back as: ${text.take(80)}")
    }

    @Test
    fun `an empty file reads as an empty string rather than throwing`() {
        val empty = File(Files.createTempDirectory("woka-charset").toFile(), "empty.srt")
        empty.writeBytes(ByteArray(0))
        assertEquals("", readSubtitleText(empty))
    }

    @Test
    fun `a UTF-8 BOM is consumed rather than left in the first cue`() {
        val dir = Files.createTempDirectory("woka-charset").toFile()
        val file = File(dir, "bom.srt")
        val body = "1\n00:00:01,000 --> 00:00:02,000\nWith a mark\n"
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray())
        assertFalse(readSubtitleText(file).startsWith('﻿'), "the BOM survived the read")
        assertEquals("With a mark", parseSubtitleFile(file).single().text)
    }

    @Test
    fun `both encodings parse to the same shape of cue list`() {
        val utf8 = parseSubtitleFile(episode(1, ".ru.srt"))
        val cp1251 = parseSubtitleFile(episode(1, ".rus.srt"))
        assertTrue(utf8.isNotEmpty())
        assertEquals(utf8.size, cp1251.size)
        assertEquals(utf8.map { it.startMs }, cp1251.map { it.startMs })
        cp1251.forEach { assertTrue(!it.text.isNullOrBlank()) }
    }

    @Test
    fun `sidecar cues are ordered and non-empty`() {
        listOf(episode(1, ".srt"), episode(2, ".en.srt"), episode(2, ".ass")).forEach { file ->
            val cues = parseSubtitleFile(file)
            assertTrue(cues.isNotEmpty(), "${file.name} parsed to nothing")
            cues.forEach { assertTrue(it.endMs > it.startMs, "${file.name}: $it") }
            cues.zipWithNext { a, b -> assertTrue(b.startMs >= a.startMs, "${file.name} is out of order") }
        }
    }

    @Test
    fun `an ASS sidecar keeps its text and loses its override tags`() {
        val cues = parseSubtitleFile(episode(2, ".ass"))
        assertTrue(cues.isNotEmpty())
        cues.forEach {
            val text = it.text.orEmpty()
            assertFalse(text.contains('{'), "an override tag survived: $text")
            assertFalse(text.contains("\\N"), "an escaped break survived: $text")
        }
    }

    @Test
    fun `a file that is not a subtitle parses to nothing instead of throwing`() {
        assertTrue(parseSubtitleFile(episode(1, ".mkv")).isEmpty())
        val missing = File(Files.createTempDirectory("woka-charset").toFile(), "gone.srt")
        assertTrue(parseSubtitleFile(missing).isEmpty())
    }
}
