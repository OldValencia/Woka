package com.woka.unit

import com.woka.core.data.CarouselVisibility
import com.woka.core.data.PinnedSession
import com.woka.core.data.RepeatMode
import com.woka.core.data.ResumeMode
import com.woka.core.data.ScalePreset
import com.woka.core.data.SortType
import com.woka.core.data.SubtitleSize
import com.woka.core.data.TrackPrefs
import com.woka.core.data.TrackRef
import com.woka.core.data.ViewLayout
import com.woka.core.data.WatchRecord
import com.woka.core.data.WokaSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors the configuration `SettingsStore` reads the real file with. */
private val json = Json { ignoreUnknownKeys = true }

class WokaSettingsTest {

    @Test
    fun `a fully populated record survives the round trip`() {
        val settings = WokaSettings(
            linkedFolders = listOf("D:/Photos", "D:/Films"),
            gridLayout = ViewLayout.BigGrid,
            folderSorts = mapOf("D:/Films" to SortType.DateDesc),
            folderShowTitles = mapOf("D:/Photos" to false),
            theme = "Dark",
            mediaViewerCarouselMode = CarouselVisibility.ALWAYS_ON,
            pinnedSessions = listOf(PinnedSession("D:/Films", 12, 40)),
            lastSession = PinnedSession("D:/Films/Series", 3, 8),
            sidebarVisible = false,
            mediaFilter = "Videos",
            scalePreset = ScalePreset.Fill,
            defaultZoomPercent = 175,
            mediaVolume = 0.42f,
            subtitleSize = SubtitleSize.Large,
            subtitleColorArgb = 0xFFFFCC00L,
            trackPrefsPerFile = mapOf("D:/Films/a.mkv" to TrackPrefs(audio = TrackRef("rus", "Dub A", "ac3"))),
            repeatMode = RepeatMode.Folder,
            playbackSpeed = 1.25f,
            resumeMode = ResumeMode.LongOnly,
            watchRecords = mapOf("D:/Films/a.mkv" to WatchRecord(90_000L, 5_400_000L))
        )
        val back = json.decodeFromString<WokaSettings>(json.encodeToString(settings))
        assertEquals(settings, back)
    }

    @Test
    fun `an empty object decodes to the shipped defaults`() {
        assertEquals(WokaSettings(), json.decodeFromString<WokaSettings>("{}"))
    }

    @Test
    fun `the shipped defaults are the conservative ones`() {
        val defaults = WokaSettings()
        assertEquals(ResumeMode.Off, defaults.resumeMode)
        assertEquals(RepeatMode.Off, defaults.repeatMode)
        assertEquals(ScalePreset.Fit, defaults.scalePreset)
        assertFalse(defaults.shuffle)
        assertFalse(defaults.defaultUseZoom)
        assertFalse(defaults.carryTracksAcrossFolder)
        assertTrue(defaults.systemMediaControls)
        // Not conservative on purpose: a PiP window that falls behind whatever you
        // clicked next is not doing the one job it exists for.
        assertTrue(defaults.pipAlwaysOnTop)
    }

    @Test
    fun `turning Always on Top off survives the round trip`() {
        val settings = WokaSettings(pipAlwaysOnTop = false)
        assertFalse(json.decodeFromString<WokaSettings>(json.encodeToString(settings)).pipAlwaysOnTop)
    }

    @Test
    fun `a field written by a newer build is ignored, not fatal`() {
        val text = """{"theme":"Dark","somethingFromTheFuture":{"a":[1,2,3]},"shuffle":true}"""
        val settings = json.decodeFromString<WokaSettings>(text)
        assertEquals("Dark", settings.theme)
        assertTrue(settings.shuffle)
    }

    @Test
    fun `a field missing from an older file takes its default`() {
        val settings = json.decodeFromString<WokaSettings>("""{"theme":"Dark"}""")
        assertEquals("Dark", settings.theme)
        assertEquals(WokaSettings().mediaVolume, settings.mediaVolume)
        assertEquals(WokaSettings().resumeMinDurationMinutes, settings.resumeMinDurationMinutes)
    }

    @Test
    fun `malformed json raises a serialization error the caller can catch`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<WokaSettings>("{ this is not json")
        }
        assertFailsWith<SerializationException> {
            json.decodeFromString<WokaSettings>("""{"mediaVolume":"loud"}""")
        }
    }

    @Test
    fun `enums are stored by name so the file stays readable`() {
        val text = json.encodeToString(WokaSettings(repeatMode = RepeatMode.Folder))
        assertTrue(text.contains(""""repeatMode":"Folder""""), text)
    }

    @Test
    fun `nested track records round trip with their nulls intact`() {
        val prefs = TrackPrefs(
            audio = TrackRef("rus", null, "ac3"),
            subtitleOff = true,
            subtitleExternalAdded = listOf("D:/Films/extra.srt")
        )
        val settings = WokaSettings(trackPrefsPerFile = mapOf("D:/Films/a.mkv" to prefs))
        val back = json.decodeFromString<WokaSettings>(json.encodeToString(settings))
        assertEquals(prefs, back.trackPrefsPerFile["D:/Films/a.mkv"])
    }
}
