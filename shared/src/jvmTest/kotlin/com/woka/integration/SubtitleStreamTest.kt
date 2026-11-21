package com.woka.integration

import com.woka.feature.viewer.video.engine.subtitle.openSubtitleWindow
import com.woka.fixtures.DUAL_VIDEO
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TEXT_SUBS
import com.woka.support.onSubtitleWindows
import com.woka.support.playThrough
import com.woka.support.subtitleTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Embedded **text** tracks, read the way playback reads them — window by window as the
 * play head moves (`SubtitleWindow`). The bitmap side is `BitmapSubtitleTest`; seeking
 * and the reads a window stops short of are `SubtitleWindowTest`.
 */
class SubtitleStreamTest {

    private fun textSubs() = SampleMedia.file(TEXT_SUBS)

    private fun episodeOne() = SampleMedia.file(SampleMedia.episode(1, ".mkv"))

    @Test
    fun `every embedded text track is read`() {
        onSubtitleWindows(textSubs()) { windows ->
            assertEquals(2, windows.size, "opened ${windows.keys}")
            windows.forEach { (index, window) ->
                assertTrue(!window.isBitmap, "stream #$index was opened as bitmap")
                window.playThrough()
                assertTrue(window.textCues.isNotEmpty(), "stream #$index read to nothing")
            }
        }
    }

    @Test
    fun `text cues are non-empty and run forwards`() {
        onSubtitleWindows(textSubs()) { windows ->
            windows.forEach { (index, window) ->
                window.playThrough()
                val cues = window.textCues
                cues.forEach {
                    assertTrue(!it.text.isNullOrBlank(), "stream #$index: empty cue $it")
                    assertTrue(it.endMs > it.startMs, "stream #$index: $it never ends")
                }
                cues.zipWithNext { a, b ->
                    assertTrue(b.startMs >= a.startMs, "stream #$index is out of order")
                }
            }
        }
    }

    @Test
    fun `a decoded text cue carries no picture`() {
        onSubtitleWindows(textSubs()) { windows ->
            val cues = windows.values.flatMap { it.playThrough(); it.textCues }
            assertTrue(cues.isNotEmpty())
            cues.forEach { assertNull(it.image) }
        }
    }

    @Test
    fun `a Cyrillic embedded track survives the decode intact`() {
        onSubtitleWindows(textSubs()) { windows ->
            val text = windows.values.flatMap { it.playThrough(); it.textCues }.joinToString("\n") { it.text.orEmpty() }
            assertTrue(text.contains("Кириллица"), "the muxed Russian track came back as: ${text.take(80)}")
            assertTrue(!text.contains('�'), "the decoder substituted instead of deciding")
        }
    }

    @Test
    fun `a declared but empty subtitle track reports nothing rather than failing`() {
        // S01E01 and DualVideo were cut to a few seconds, which left three and two
        // `subrip` tracks declared with no packets behind them. The probe still
        // lists them, so reading has to cope with finding nothing.
        listOf(episodeOne(), SampleMedia.file(DUAL_VIDEO)).forEach { file ->
            assertTrue(subtitleTracks(file).isNotEmpty(), "${file.name} declares no subtitles")
            onSubtitleWindows(file) { windows ->
                windows.values.forEach { window ->
                    window.playThrough(untilMs = 10_000L)
                    assertTrue(window.textCues.isEmpty(), "${file.name}: ${window.textCues}")
                }
            }
        }
    }

    @Test
    fun `a stream that is not a subtitle opens to nothing`() {
        assertNull(openSubtitleWindow(textSubs().absolutePath, index = 0, bitmap = false), "the video opened")
        assertNull(openSubtitleWindow(textSubs().absolutePath, index = 99, bitmap = false), "a missing stream opened")
    }
}
