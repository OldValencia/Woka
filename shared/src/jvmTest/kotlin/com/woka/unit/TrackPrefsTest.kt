package com.woka.unit

import com.woka.core.data.PlaybackSpeed
import com.woka.core.data.RepeatMode
import com.woka.core.data.ResumeMode
import com.woka.core.data.TrackPrefs
import com.woka.core.data.TrackRef
import com.woka.core.data.WokaSettings
import com.woka.core.data.resumeAppliesTo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class TrackPrefsTest {

    @Test
    fun `a ref round trips through json`() {
        val ref = TrackRef("rus", "Dub A", "ac3")
        assertEquals(ref, json.decodeFromString<TrackRef>(json.encodeToString(ref)))
    }

    @Test
    fun `an untitled ref keeps its nulls rather than gaining empty strings`() {
        val ref = TrackRef(language = "rus", title = null, codec = "aac")
        val back = json.decodeFromString<TrackRef>(json.encodeToString(ref))
        assertEquals(null, back.title)
        assertEquals(ref, back)
    }

    @Test
    fun `an empty record says nothing about subtitles`() {
        assertFalse(TrackPrefs().hasSubtitleChoice)
    }

    @Test
    fun `each of the four subtitle fields counts as a choice`() {
        assertTrue(TrackPrefs(subtitleOff = true).hasSubtitleChoice)
        assertTrue(TrackPrefs(subtitleOff = false).hasSubtitleChoice)
        assertTrue(TrackPrefs(subtitle = TrackRef("eng", null, "subrip")).hasSubtitleChoice)
        assertTrue(TrackPrefs(subtitleExternal = "D:/Films/a.srt").hasSubtitleChoice)
        assertTrue(TrackPrefs(subtitleExternalLanguage = "rus").hasSubtitleChoice)
    }

    @Test
    fun `hiding a sidecar is not choosing a subtitle`() {
        val prefs = TrackPrefs(subtitleExternalHidden = listOf("D:/Films/a.ru.srt"))
        assertFalse(prefs.hasSubtitleChoice)
    }

    @Test
    fun `choosing audio alone leaves the subtitle dimension untouched`() {
        val prefs = TrackPrefs(audio = TrackRef("rus", "Dub A", "ac3"))
        assertFalse(prefs.hasSubtitleChoice)
        assertEquals(null, prefs.subtitleOff)
    }

    @Test
    fun `resume off never applies, however long the video`() {
        val settings = WokaSettings(resumeMode = ResumeMode.Off)
        assertFalse(settings.resumeAppliesTo(0L))
        assertFalse(settings.resumeAppliesTo(3 * 60 * 60_000L))
    }

    @Test
    fun `resume always applies to anything with a known duration`() {
        val settings = WokaSettings(resumeMode = ResumeMode.Always)
        assertFalse(settings.resumeAppliesTo(0L))
        assertTrue(settings.resumeAppliesTo(1L))
        assertTrue(settings.resumeAppliesTo(2 * 60_000L))
    }

    @Test
    fun `long-only turns over exactly at the threshold`() {
        val settings = WokaSettings(resumeMode = ResumeMode.LongOnly, resumeMinDurationMinutes = 20)
        assertFalse(settings.resumeAppliesTo(20 * 60_000L - 1L))
        assertTrue(settings.resumeAppliesTo(20 * 60_000L))
        assertTrue(settings.resumeAppliesTo(20 * 60_000L + 1L))
    }

    @Test
    fun `long-only ignores a video of unknown length`() {
        val settings = WokaSettings(resumeMode = ResumeMode.LongOnly, resumeMinDurationMinutes = 1)
        assertFalse(settings.resumeAppliesTo(0L))
    }

    @Test
    fun `the repeat modes cycle back to off`() {
        var mode = RepeatMode.Off
        val walk = List(3) { mode = mode.next(); mode }
        assertEquals(listOf(RepeatMode.File, RepeatMode.Folder, RepeatMode.Off), walk)
    }

    @Test
    fun `speed snaps onto normal and stays inside its bounds`() {
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.snap(1.02f))
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.snap(0.97f))
        assertEquals(1.25f, PlaybackSpeed.snap(1.25f))
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.snap(0.1f))
        assertEquals(PlaybackSpeed.MAX, PlaybackSpeed.snap(9f))
    }

    @Test
    fun `speed labels trim their trailing zeroes`() {
        assertEquals("1×", PlaybackSpeed.label(1.0f))
        assertEquals("0.5×", PlaybackSpeed.label(0.5f))
        assertEquals("1.25×", PlaybackSpeed.label(1.25f))
        assertEquals("2×", PlaybackSpeed.label(2.0f))
    }
}
