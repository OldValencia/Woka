package com.woka.integration

import com.woka.fixtures.BITMAP_SUBS
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TEXT_SUBS
import com.woka.support.onSubtitleWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An embedded track is read around the play head, not end to end (reported 2026-11-20:
 * on a 63.6 GB film no subtitle appeared for the 32.7 s a whole-container pass took).
 *
 * Both fixtures run ten minutes with lines near the five- and ten-minute marks — `textsubs`
 * at 300 s for 8 s on both tracks (and at 20 s for 6 s), `BitmapSubs` at 299 s and 599 s — so
 * a read that stopped near five minutes is told apart from one that went to the end. Times
 * are on the player's clock: `textsubs` starts its container 21 ms in, so 300 s reads 299 979.
 * Its video and audio last 3 s, so nothing there can stop a read early; how far a read goes
 * is asserted on `BitmapSubs`, whose video runs to the end.
 */
class SubtitleWindowTest {

    @Test
    fun `a line mid-film is found without reading to the end`() {
        onSubtitleWindows(SampleMedia.file(TEXT_SUBS)) { windows ->
            windows.forEach { (index, window) ->
                val cue = assertNotNull(window.textAt(300_500L), "stream #$index: nothing at 5:00.5")
                assertTrue(cue.startMs in FIVE_MINUTES, "stream #$index found $cue")
                assertFalse(window.hasRead(100_000L), "stream #$index read from the start")
            }
        }
    }

    /** The seek lands 5 s into an 8 s line; the window reads 10 s behind it, so the line is there. */
    @Test
    fun `a line that began before the seek point is still shown`() {
        onSubtitleWindows(SampleMedia.file(TEXT_SUBS)) { windows ->
            windows.forEach { (index, window) ->
                val cue = assertNotNull(window.textAt(305_000L), "stream #$index lost the line it landed in")
                assertTrue(cue.startMs in FIVE_MINUTES, "stream #$index found $cue")
            }
        }
    }

    @Test
    fun `going back into what was read reads nothing again and still answers`() {
        onSubtitleWindows(SampleMedia.file(TEXT_SUBS)) { windows ->
            windows.forEach { (index, window) ->
                window.textAt(300_500L)
                assertNotNull(window.textAt(20_500L), "stream #$index: nothing at 0:20.5")
                assertNotNull(window.textAt(300_500L), "stream #$index forgot the first window")
                assertTrue(window.hasRead(300_500L) && window.hasRead(20_500L))
            }
        }
    }

    @Test
    fun `a bitmap line mid-film is indexed and drawn from its window`() {
        onSubtitleWindows(SampleMedia.file(BITMAP_SUBS)) { windows ->
            assertEquals(2, windows.size, "opened ${windows.keys}")
            windows.forEach { (index, window) ->
                assertTrue(window.isBitmap, "stream #$index was opened as text")
                val cue = assertNotNull(window.bitmapAt(300_000L), "stream #$index: nothing at 5:00")
                assertTrue(cue.startMs in 299_000L..300_000L, "stream #$index found $cue")
                val image = assertNotNull(window.decode(cue), "stream #$index decoded to nothing")
                assertTrue(image.rects.isNotEmpty(), "stream #$index produced no rectangles")
                // Stream #2's next line after 5:00 is at 9:59. Read by its own packets alone
                // the window would run to it; the video's timestamps stop it near 5:40.
                assertFalse(window.hasRead(500_000L), "stream #$index read on to its next line")
            }
        }
    }
}

/** Where `textsubs`' five-minute line starts on the player's clock. */
private val FIVE_MINUTES = 299_900L..300_000L
