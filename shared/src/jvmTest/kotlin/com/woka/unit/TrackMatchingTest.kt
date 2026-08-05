package com.woka.unit

import com.woka.core.data.TrackRef
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.video.model.TrackType
import com.woka.feature.viewer.video.model.matchRef
import com.woka.feature.viewer.video.model.toRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private fun audio(
    index: Int,
    codec: String,
    language: String? = null,
    title: String? = null
) = MediaTrack(
    index = index,
    number = index + 1,
    type = TrackType.Audio,
    codec = codec,
    language = language,
    title = title
)

class TrackMatchingTest {

    /** The S01E01 shape: seven `rus` tracks that only their titles tell apart. */
    private val dubA = audio(1, "ac3", "rus", "Dub A")
    private val dubB = audio(2, "ac3", "rus", "Dub B")
    private val dubC = audio(3, "dts", "rus", "Dub C")
    private val original = audio(4, "aac", "eng", "Original")
    private val untitled = audio(5, "aac", "rus")
    private val tracks = listOf(dubA, dubB, dubC, original, untitled)

    @Test
    fun `a remembered track is found by its full tuple`() {
        assertSame(dubC, tracks.matchRef(TrackRef("rus", "Dub C", "dts")))
    }

    @Test
    fun `a round trip through the persisted ref finds the same track`() {
        tracks.forEach { track ->
            assertSame(track, tracks.matchRef(track.toRef()), "${track.label} did not survive")
        }
    }

    @Test
    fun `the title wins over a shared language`() {
        val ref = TrackRef(language = "rus", title = "Original", codec = "opus")
        assertSame(original, tracks.matchRef(ref))
    }

    @Test
    fun `a re-encoded track is still found by its title`() {
        val ref = TrackRef(language = "rus", title = "Dub B", codec = "eac3")
        assertSame(dubB, tracks.matchRef(ref))
    }

    @Test
    fun `language alone is the last resort`() {
        val ref = TrackRef(language = "eng", title = null, codec = "flac")
        assertSame(original, tracks.matchRef(ref))
    }

    @Test
    fun `two candidates sharing a language resolve to the first, deterministically`() {
        val ref = TrackRef(language = "rus", title = null, codec = "")
        repeat(5) { assertSame(dubA, tracks.matchRef(ref)) }
    }

    @Test
    fun `a track with no language is matched by the tuple it does have`() {
        assertSame(untitled, tracks.matchRef(TrackRef("rus", null, "aac")))
    }

    @Test
    fun `nothing remembered matches nothing`() {
        assertNull(tracks.matchRef(null))
    }

    @Test
    fun `a file with no tracks matches nothing`() {
        assertNull(emptyList<MediaTrack>().matchRef(TrackRef("rus", "Dub A", "ac3")))
    }

    @Test
    fun `a track the file does not carry is not substituted`() {
        assertNull(tracks.matchRef(TrackRef("jpn", "Japanese", "flac")))
    }

    @Test
    fun `the ref carries the identity tuple and not the stream index`() {
        assertEquals(TrackRef("rus", "Dub C", "dts"), dubC.toRef())
    }
}
