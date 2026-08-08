package com.woka.integration

import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.feature.viewer.video.model.MediaTracks
import com.woka.fixtures.DUAL_VIDEO
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import com.woka.support.onGrabber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stream enumeration. Chapters are `MediaChapterProbeTest`. */
class MediaProbeTest {

    private fun tracksOf(name: String): MediaTracks =
        onGrabber(SampleMedia.file(name)) { probeMediaTracks(it) }

    private val episodeOne = SampleMedia.episode(1, ".mkv")
    private val episodeTwo = SampleMedia.episode(2, ".mkv")

    @Test
    fun `a season file with eight audio tracks enumerates all of them`() {
        val tracks = tracksOf(episodeOne)
        assertEquals(8, tracks.audio.size, "audio: ${tracks.audio.map { it.label }}")
        assertEquals(3, tracks.subtitle.size)
        assertEquals(1, tracks.video.size)
        assertTrue(tracks.hasMultipleAudio)
        assertTrue(tracks.hasSubtitle)
    }

    @Test
    fun `seven tracks share a language and are told apart by title alone`() {
        val russian = tracksOf(episodeOne).audio.filter { it.language == "rus" }
        assertEquals(7, russian.size, "the fixture stopped being the hard case")
        val titles = russian.mapNotNull { it.title }
        assertEquals(russian.size, titles.size, "a russian track lost its title")
        assertEquals(titles.size, titles.toSet().size, "two titles collided")
    }

    @Test
    fun `a track with no language declared does not break the walk`() {
        val subtitles = tracksOf(episodeOne).subtitle
        assertEquals(1, subtitles.count { it.language == null }, subtitles.map { it.language }.toString())
        subtitles.forEach { assertTrue(it.codec.isNotBlank(), "a subtitle track lost its codec") }
    }

    @Test
    fun `every track reports the identity tuple matching is done on`() {
        tracksOf(episodeOne).audio.forEach { track ->
            assertTrue(track.codec.isNotBlank(), "${track.label} has no codec")
            assertTrue(track.index >= 0)
            assertNotNull(track.channels, "${track.label} has no channel count")
        }
    }

    @Test
    fun `stream indices are absolute and unique across the container`() {
        val tracks = tracksOf(episodeOne)
        val all = tracks.video + tracks.audio + tracks.subtitle
        assertEquals(all.size, all.map { it.index }.toSet().size, "two tracks claim one stream")
        assertEquals(12, all.size)
    }

    @Test
    fun `a second episode carries only its own single audio track`() {
        val tracks = tracksOf(episodeTwo)
        assertEquals(1, tracks.audio.size)
        assertEquals("eng", tracks.audio.single().language)
        assertTrue(!tracks.hasMultipleAudio)
    }

    @Test
    fun `two video streams in one container are both found`() {
        val tracks = tracksOf(DUAL_VIDEO)
        assertEquals(2, tracks.video.size)
        assertTrue(tracks.hasMultipleVideo)
        assertEquals(2, tracks.audio.size)
        assertEquals(2, tracks.subtitle.size)
    }

    @Test
    fun `the two audio streams differ by codec, not only by language`() {
        val audio = tracksOf(DUAL_VIDEO).audio
        assertEquals(2, audio.map { it.codec }.toSet().size, audio.map { it.codec }.toString())
        assertEquals(setOf("eng", "rus"), audio.mapNotNull { it.language }.toSet())
    }

    @Test
    fun `the video streams disagree about tone mapping, and the probe reads which`() {
        val video = tracksOf(DUAL_VIDEO).video.sortedBy { it.index }
        val file = SampleMedia.file(DUAL_VIDEO)
        val first = onGrabber(file, video[0].index) { it.readStreamHdrInfo() }
        val second = onGrabber(file, video[1].index) { it.readStreamHdrInfo() }
        assertTrue(first.needsColorManagement, "the PQ/BT.2020 stream was read as SDR")
        assertTrue(first.isWideGamut)
        assertTrue(!second.needsColorManagement, "the BT.709 stream was read as HDR")
    }

    @Test
    fun `a file with no audio stream reports none rather than an empty track`() {
        val tracks = tracksOf(NO_AUDIO)
        assertTrue(tracks.audio.isEmpty())
        assertEquals(1, tracks.video.size)
        assertTrue(!tracks.hasMultipleAudio)
    }
}
