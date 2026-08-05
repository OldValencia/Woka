package com.woka.integration

import com.woka.feature.viewer.video.engine.probe.probeChapters
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import com.woka.feature.viewer.video.model.MediaChapter
import com.woka.support.onGrabber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun chaptersOf(name: String): List<MediaChapter> =
    onGrabber(SampleMedia.file(name)) { probeChapters(it, it.lengthInTime / 1000L) }

class MediaChapterProbeTest {

    @Test
    fun `chapters come back with the boundaries the container declares`() {
        val chapters = chaptersOf(CHAPTERS)
        assertEquals(3, chapters.size, chapters.toString())
        assertEquals(0L, chapters[0].startMs)
        assertEquals(800L, chapters[0].endMs)
        assertEquals(800L, chapters[1].startMs)
        assertEquals(2000L, chapters[1].endMs)
        assertEquals(2000L, chapters[2].startMs)
        assertEquals(3000L, chapters[2].endMs)
    }

    @Test
    fun `chapter boundaries never overlap or run backwards`() {
        val chapters = chaptersOf(CHAPTERS)
        chapters.forEach { assertTrue(it.endMs > it.startMs, "$it is empty or inverted") }
        chapters.zipWithNext { a, b -> assertTrue(b.startMs >= a.endMs, "$a overlaps $b") }
    }

    @Test
    fun `every chapter has a title, invented if the container gave none`() {
        chaptersOf(CHAPTERS).forEach { assertTrue(it.title.isNotBlank()) }
    }

    @Test
    fun `chapters sit inside the file's duration`() {
        val duration = onGrabber(SampleMedia.file(CHAPTERS)) { it.lengthInTime / 1000L }
        chaptersOf(CHAPTERS).forEach {
            assertTrue(it.startMs >= 0L && it.endMs <= duration, "$it runs outside 0..$duration")
        }
    }

    @Test
    fun `a file without chapters gives an empty list, not an exception`() {
        assertTrue(chaptersOf(SampleMedia.episode(2, ".mkv")).isEmpty())
        assertTrue(chaptersOf(NO_AUDIO).isEmpty())
    }
}
