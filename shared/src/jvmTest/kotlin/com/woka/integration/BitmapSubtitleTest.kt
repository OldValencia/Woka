package com.woka.integration

import com.woka.feature.viewer.video.engine.subtitle.openBitmapSubtitleFile
import com.woka.fixtures.BITMAP_SUBS
import com.woka.fixtures.BROKEN
import com.woka.fixtures.SampleMedia
import com.woka.support.firstCueStart
import com.woka.support.onSubtitleWindows
import com.woka.support.playThrough
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PGS and VobSub: indexing, on-demand expansion, and the two sidecar formats. */
class BitmapSubtitleTest {

    private fun bitmapSubs() = SampleMedia.file(BITMAP_SUBS)

    @Test
    fun `both bitmap formats in one container are indexed`() {
        onSubtitleWindows(bitmapSubs()) { windows ->
            assertEquals(2, windows.size, "opened: ${windows.keys}")
            windows.forEach { (index, window) ->
                assertTrue(window.isBitmap, "stream #$index was opened as text")
                window.playThrough()
                assertTrue(window.cueCount > 0, "stream #$index indexed no cues")
            }
        }
    }

    @Test
    fun `the index keeps compressed payloads, not pictures`() {
        onSubtitleWindows(bitmapSubs()) { windows ->
            windows.forEach { (index, source) ->
                source.playThrough()
                assertTrue(source.retainedBytes > 0, "stream #$index retained nothing")
                val perCue = source.retainedBytes / source.cueCount
                // A single 1920×1080 RGBA rectangle would be 8 MB on its own.
                assertTrue(perCue < 1_000_000, "stream #$index retains ${perCue}B per cue")
            }
        }
    }

    @Test
    fun `a cue expands to at least one positioned rectangle`() {
        onSubtitleWindows(bitmapSubs()) { windows ->
            windows.forEach { (index, source) ->
                val cue = assertNotNull(source.bitmapAt(firstCueStart(source)), "stream #$index")
                val image = assertNotNull(source.decode(cue), "stream #$index decoded to nothing")
                assertTrue(image.rects.isNotEmpty(), "stream #$index produced no rectangles")
                image.rects.forEach {
                    assertTrue(it.bitmap.width > 0 && it.bitmap.height > 0, "an empty rectangle")
                    assertTrue(it.x >= 0 && it.y >= 0, "a rectangle placed off the canvas")
                }
            }
        }
    }

    @Test
    fun `a cue is released once the play head passes its end`() {
        onSubtitleWindows(bitmapSubs()) { windows ->
            windows.forEach { (index, source) ->
                val cue = assertNotNull(source.bitmapAt(firstCueStart(source)), "stream #$index")
                assertTrue(cue.endMs > cue.startMs, "stream #$index: $cue never ends")
                assertEquals(cue, source.bitmapAt(cue.endMs), "stream #$index dropped a cue early")
                val after = source.bitmapAt(cue.endMs + 1)
                assertTrue(after == null || after != cue, "stream #$index held $cue past its end")
            }
        }
    }

    @Test
    fun `nothing is showing before the first cue`() {
        onSubtitleWindows(bitmapSubs()) { windows ->
            windows.forEach { (index, source) ->
                val start = firstCueStart(source)
                if (start > 0L) assertNull(source.bitmapAt(0L), "stream #$index shows a cue at zero")
            }
        }
    }

    @Test
    fun `a raw PGS sidecar opens and indexes`() {
        val path = SampleMedia.file(SampleMedia.episode(3, ".sup")).absolutePath
        val source = assertNotNull(openBitmapSubtitleFile(path), "the .sup sidecar did not open")
        try {
            assertTrue(source.cueCount > 0)
            val cue = assertNotNull(source.cueAt(firstCueStart(source)))
            assertTrue(assertNotNull(source.decode(cue)).rects.isNotEmpty())
        } finally {
            source.close()
        }
    }

    @Test
    fun `a VobSub pair opens through its idx`() {
        val path = SampleMedia.file(SampleMedia.episode(4, ".idx")).absolutePath
        val source = assertNotNull(openBitmapSubtitleFile(path), "the .idx sidecar did not open")
        try {
            assertTrue(source.cueCount > 0)
            val cue = assertNotNull(source.cueAt(firstCueStart(source)))
            assertTrue(cue.endMs > cue.startMs, "$cue never ends")
            // The .idx fixture has a four-minute gap and VobSub declares the DVD
            // maximum when a cue has no stop command; the indexer caps both.
            assertTrue(cue.endMs - cue.startMs <= 30_000L, "$cue was left on screen")
            assertTrue(assertNotNull(source.decode(cue)).rects.isNotEmpty())
        } finally {
            source.close()
        }
    }

    @Test
    fun `a file that is not a subtitle track opens to nothing`() {
        assertNull(openBitmapSubtitleFile(SampleMedia.file(BROKEN).absolutePath))
        val absent = File(SampleMedia.root, "absent.sup").absolutePath
        assertNull(openBitmapSubtitleFile(absent))
    }
}
