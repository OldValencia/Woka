package com.woka.integration

import com.woka.feature.viewer.video.engine.subtitle.decodeEmbeddedSubtitles
import com.woka.fixtures.DUAL_VIDEO
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TEXT_SUBS
import com.woka.support.onSubtitleStreams
import com.woka.support.subtitleTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Embedded **text** tracks. The bitmap side is `BitmapSubtitleTest`. */
class SubtitleStreamTest {

    private fun textSubs() = SampleMedia.file(TEXT_SUBS)

    private fun episodeOne() = SampleMedia.file(SampleMedia.episode(1, ".mkv"))

    @Test
    fun `every embedded text track is parsed in the one pass`() {
        onSubtitleStreams(textSubs()) { streams ->
            assertEquals(2, streams.text.size, "parsed ${streams.text.keys}")
            assertTrue(streams.bitmap.isEmpty(), "a text track was indexed as bitmap")
            streams.text.forEach { (index, cues) ->
                assertTrue(cues.isNotEmpty(), "stream #$index parsed to nothing")
            }
        }
    }

    @Test
    fun `text cues are non-empty and run forwards`() {
        onSubtitleStreams(textSubs()) { streams ->
            streams.text.forEach { (index, cues) ->
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
        onSubtitleStreams(textSubs()) { streams ->
            val cues = streams.text.values.flatten()
            assertTrue(cues.isNotEmpty())
            cues.forEach { assertNull(it.image) }
        }
    }

    @Test
    fun `a Cyrillic embedded track survives the decode intact`() {
        onSubtitleStreams(textSubs()) { streams ->
            val text = streams.text.values.flatten().joinToString("\n") { it.text.orEmpty() }
            assertTrue(text.contains("Кириллица"), "the muxed Russian track came back as: ${text.take(80)}")
            assertTrue(!text.contains('�'), "the decoder substituted instead of deciding")
        }
    }

    @Test
    fun `a declared but empty subtitle track reports nothing rather than failing`() {
        // S01E01 and DualVideo were cut to a few seconds, which left three and two
        // `subrip` tracks declared with no packets behind them. The probe still
        // lists them, so the demux pass has to cope with finding nothing.
        listOf(episodeOne(), SampleMedia.file(DUAL_VIDEO)).forEach { file ->
            assertTrue(subtitleTracks(file).isNotEmpty(), "${file.name} declares no subtitles")
            onSubtitleStreams(file) { streams ->
                assertTrue(streams.text.values.all { it.isEmpty() }, "${file.name}: ${streams.text}")
                assertTrue(streams.bitmap.isEmpty())
            }
        }
    }

    @Test
    fun `a container with no subtitles wanted is not even opened`() {
        val streams = decodeEmbeddedSubtitles(episodeOne().absolutePath, emptySet(), emptySet())
        assertTrue(streams.text.isEmpty())
        assertTrue(streams.bitmap.isEmpty())
    }
}
