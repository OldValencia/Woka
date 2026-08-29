package com.woka.integration

import com.woka.core.data.MAX_MEDIA_VOLUME
import com.woka.core.data.PinnedSession
import com.woka.core.data.TrackPrefs
import com.woka.core.data.TrackRef
import com.woka.support.awaitSettingsWrite
import com.woka.support.onSettingsDisk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the repository does to a value on its way in, and what it does to the
 * records keyed by path. The disk round trip itself is `SettingsPersistenceTest`;
 * the size state is `SizeStatePersistenceTest`.
 */
class SettingsRecordsTest {

    @Test
    fun `the zoom percentage is held inside its range`() {
        onSettingsDisk { repository ->
            repository.setDefaultZoomPercent(5)
            assertEquals(10, repository.settings.value.defaultZoomPercent)
            repository.setDefaultZoomPercent(9000)
            assertEquals(1600, repository.settings.value.defaultZoomPercent)
            repository.setDefaultZoomPercent(175)
            assertEquals(175, repository.settings.value.defaultZoomPercent)
        }
    }

    @Test
    fun `volume and box opacity are held inside their ranges`() {
        onSettingsDisk { repository ->
            repository.setMediaVolume(-3f)
            assertEquals(0f, repository.settings.value.mediaVolume)
            // The top is `MAX_MEDIA_VOLUME` since Sound boost, not 1f — the switch decides
            // what a control may *reach*, and the record holds whatever it reached.
            repository.setMediaVolume(4f)
            assertEquals(MAX_MEDIA_VOLUME, repository.settings.value.mediaVolume)
            repository.setSubtitleBoxOpacity(-1f)
            assertEquals(0f, repository.settings.value.subtitleBoxOpacity)
            repository.setSubtitleBoxOpacity(2f)
            assertEquals(1f, repository.settings.value.subtitleBoxOpacity)
        }
    }

    @Test
    fun `the resume threshold is held between one minute and four hours`() {
        onSettingsDisk { repository ->
            repository.setResumeMinDuration(0)
            assertEquals(1, repository.settings.value.resumeMinDurationMinutes)
            repository.setResumeMinDuration(999)
            assertEquals(240, repository.settings.value.resumeMinDurationMinutes)
        }
    }

    @Test
    fun `playback speed is snapped on the way in`() {
        onSettingsDisk { repository ->
            repository.setPlaybackSpeed(1.01f)
            assertEquals(1f, repository.settings.value.playbackSpeed)
            repository.setPlaybackSpeed(9f)
            assertEquals(2f, repository.settings.value.playbackSpeed)
        }
    }

    @Test
    fun `re-adding a linked folder does not duplicate it`() {
        onSettingsDisk { repository ->
            repeat(3) { repository.addLinkedFolder("D:/Photos") }
            assertEquals(listOf("D:/Photos"), repository.settings.value.linkedFolders)
        }
    }

    @Test
    fun `unlinking a folder sweeps every record underneath it`() {
        onSettingsDisk { repository ->
            val separator = File.separator
            val folder = "D:${separator}Films"
            val inside = "$folder${separator}a.mkv"
            val elsewhere = "D:${separator}Other${separator}b.mkv"
            repository.addLinkedFolder(folder)
            repository.setTrackPrefs(inside, folder, TrackPrefs(audio = TrackRef("rus")), TrackPrefs())
            repository.setTrackPrefs(elsewhere, "D:${separator}Other", TrackPrefs(), TrackPrefs())
            repository.setWatchPosition(inside, 90_000L, 5_400_000L)
            repository.setWatchPosition(elsewhere, 12_000L, 5_400_000L)
            repository.removeLinkedFolder(folder)

            val after = repository.settings.value
            assertTrue(after.linkedFolders.isEmpty())
            assertNull(after.trackPrefsPerFile[inside])
            assertNull(after.trackPrefsPerFolder[folder])
            assertNull(after.watchRecords[inside])
            assertEquals(
                12_000L,
                after.watchRecords[elsewhere]?.positionMs,
                "an unrelated folder was swept"
            )
        }
    }

    @Test
    fun `clearing one file's track choices leaves the rest alone`() {
        onSettingsDisk { repository ->
            repository.setTrackPrefs("D:/a.mkv", "D:/", TrackPrefs(audio = TrackRef("rus")), TrackPrefs())
            repository.setTrackPrefs("D:/b.mkv", "D:/", TrackPrefs(audio = TrackRef("eng")), TrackPrefs())
            repository.clearTrackPrefsForFile("D:/a.mkv")
            assertNull(repository.settings.value.trackPrefsPerFile["D:/a.mkv"])
            assertEquals("eng", repository.settings.value.trackPrefsPerFile["D:/b.mkv"]?.audio?.language)
        }
    }

    @Test
    fun `clearing every track choice empties both layers`() {
        onSettingsDisk { repository ->
            repository.setTrackPrefs("D:/a.mkv", "D:/", TrackPrefs(audio = TrackRef("rus")), TrackPrefs())
            repository.clearAllTrackPrefs()
            assertTrue(repository.settings.value.trackPrefsPerFile.isEmpty())
            assertTrue(repository.settings.value.trackPrefsPerFolder.isEmpty())
        }
    }

    @Test
    fun `a pinned session replaces the earlier one for the same folder`() {
        onSettingsDisk { repository ->
            repository.addPinnedSession(PinnedSession("D:/Films", 3, 10))
            repository.addPinnedSession(PinnedSession("D:/Films", 40, 5))
            assertEquals(PinnedSession("D:/Films", 40, 5), repository.settings.value.pinnedSessions.single())
        }
    }

    /**
     * `encodeDefaults` is off, so the on-disk assertion can only be made for the
     * value that differs from the default — `true` reaches the file by being
     * absent, which asserting its presence would get exactly backwards.
     */
    @Test
    fun `turning Always on Top off is written and read back`() {
        onSettingsDisk { repository ->
            repository.setPipAlwaysOnTop(false)
            assertEquals(false, repository.settings.value.pipAlwaysOnTop)
            awaitSettingsWrite(""""pipAlwaysOnTop": false""")
            repository.setPipAlwaysOnTop(true)
            assertEquals(true, repository.settings.value.pipAlwaysOnTop)
        }
    }
}
