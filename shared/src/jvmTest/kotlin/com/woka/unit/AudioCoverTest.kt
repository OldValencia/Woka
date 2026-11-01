package com.woka.unit

import com.woka.feature.viewer.audio.AudioCover
import com.woka.feature.viewer.audio.AudioTags
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A track's cover and tags, read from the AP-001 fixtures (AP-003). */
class AudioCoverTest {

    private fun tone(name: String) = File(javaClass.getResource("/media/audio/$name")!!.toURI())

    @Test
    fun `the cover a file carries is found and decoded`() {
        val cover = assertNotNull(AudioCover.load(tone("tone-cover.mp3")))
        assertEquals(64, cover.width)
        assertEquals(64, cover.height)
    }

    @Test
    fun `a file with no cover and nothing beside it has none`() {
        val dir = createTempDirectory("woka-cover").toFile()
        try {
            val bare = tone("tone.mp3").copyTo(File(dir, "bare.mp3"))
            assertNull(AudioCover.load(bare))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a cover jpg beside the file is the fallback, matched without case`() {
        val dir = createTempDirectory("woka-cover").toFile()
        try {
            val track = tone("tone.mp3").copyTo(File(dir, "track.mp3"))
            File(dir, "Cover.JPG").writeText("")
            assertEquals("Cover.JPG", AudioCover.sidecar(track)?.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `tags give the title and artist, and their absence the file and folder name`() {
        val tagged = AudioTags.read(tone("tone-cover.mp3"))
        assertEquals("Woka_Tone", tagged.title)
        assertEquals("Woka", tagged.subtitle)
        assertEquals("Woka", tagged.artist)
        assertNull(tagged.album)
        val bare = AudioTags.read(tone("tone.mp3"))
        assertEquals("tone", bare.title)
        assertEquals("audio", bare.subtitle)
    }
}
