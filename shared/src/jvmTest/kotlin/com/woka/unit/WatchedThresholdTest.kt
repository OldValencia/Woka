package com.woka.unit

import com.woka.core.data.WatchedWhen
import com.woka.feature.viewer.video.engine.creditsStartMs
import com.woka.feature.viewer.video.engine.finishedFromMs
import com.woka.feature.viewer.video.engine.watchedFromMs
import com.woka.feature.viewer.video.model.MediaChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * Where "watched" begins under each setting, and how far a container's chapter
 * titles are allowed to move it.
 *
 * The file that prompted the setting: 95:03 long, end credits from 88:15. Stopping
 * there left it recorded as unfinished, because the flat tail it was judged against
 * is the last half-minute of the file.
 */
class WatchedThresholdTest {

    private val film = 95 * MINUTE + 3_000L
    private val credits = 88 * MINUTE + 15_000L

    private fun mostOfIt(durationMs: Long, creditsStartMs: Long? = null): Long =
        watchedFromMs(durationMs, WatchedWhen.MostOfIt, creditsStartMs)

    private fun theVeryEnd(durationMs: Long): Long =
        watchedFromMs(durationMs, WatchedWhen.TheVeryEnd, null)

    private fun chapters(vararg titled: Pair<Long, String>): List<MediaChapter> =
        titled.mapIndexed { i, (start, title) ->
            MediaChapter(
                startMs = start,
                endMs = titled.getOrNull(i + 1)?.first ?: (start + MINUTE),
                title = title
            )
        }

    @Test
    fun `the very end is the behaviour that shipped`() {
        listOf(8_000L, MINUTE, 30 * MINUTE, film).forEach {
            assertEquals(finishedFromMs(it), theVeryEnd(it), "the tail moved for a ${it}ms file")
        }
    }

    @Test
    fun `most of a two-hour film is six minutes short of its end`() {
        val duration = 2 * 60 * MINUTE
        assertEquals(114 * MINUTE, mostOfIt(duration))
        assertEquals(duration - 30_000L, theVeryEnd(duration))
    }

    @Test
    fun `the film that prompted this is marked, chapter or no chapter`() {
        assertEquals(credits, mostOfIt(film, creditsStartMs = credits))
        assertEquals(90 * MINUTE + 17_850L, mostOfIt(film))
        assertTrue(mostOfIt(film) < film - 4 * MINUTE, "stopping in the credits must still mark it")
    }

    @Test
    fun `the mark is never later than the old one`() {
        listOf(8_000L, 20_000L, MINUTE, 10 * MINUTE, film, 2 * 60 * MINUTE).forEach {
            assertTrue(
                mostOfIt(it) <= finishedFromMs(it),
                "a ${it}ms file is marked later than The very end"
            )
        }
        // The clamp, not the share, governs here: 95% of a minute is 0:57, past the
        // 0:55.2 the tail ratio puts that file at.
        assertEquals(finishedFromMs(MINUTE), mostOfIt(MINUTE))
    }

    @Test
    fun `a credits chapter only ever moves the mark earlier`() {
        val duration = 100 * MINUTE
        assertEquals(80 * MINUTE, mostOfIt(duration, creditsStartMs = 80 * MINUTE))
        assertEquals(95 * MINUTE, mostOfIt(duration, creditsStartMs = 98 * MINUTE))
    }

    @Test
    fun `an unknown duration marks nothing in either mode`() {
        assertEquals(Long.MAX_VALUE, mostOfIt(0L))
        assertEquals(Long.MAX_VALUE, theVeryEnd(0L))
        assertEquals(Long.MAX_VALUE, mostOfIt(-1L, creditsStartMs = 10L))
    }

    @Test
    fun `a named closing chapter late in the file is believed`() {
        val found = creditsStartMs(chapters(0L to "Part 01", credits to "End Credits"), film)
        assertEquals(credits, found)
        assertEquals(credits, creditsStartMs(chapters(0L to "Feature", credits to "ТИТРЫ"), film))
    }

    @Test
    fun `a badly named chapter list is dropped rather than trusted`() {
        // Opening titles: the same word, the wrong end of the file.
        assertNull(creditsStartMs(chapters(0L to "Credits", MINUTE to "Episode"), film))
        // The whole film labelled as its own credits, and one starting at 5%.
        assertNull(creditsStartMs(chapters(0L to "Intro", 0L to "End credits"), film))
        assertNull(creditsStartMs(chapters(0L to "Intro", 5 * MINUTE to "End credits"), film))
        // Nothing claimed at all.
        assertNull(creditsStartMs(chapters(0L to "Chapter 11", credits to "Chapter 12"), film))
        assertNull(creditsStartMs(emptyList(), film))
        assertNull(creditsStartMs(chapters(0L to "End credits"), film))
    }
}
