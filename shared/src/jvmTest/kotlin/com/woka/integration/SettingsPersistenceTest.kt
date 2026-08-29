package com.woka.integration

import com.woka.core.data.MAX_MEDIA_VOLUME
import com.woka.core.data.ResumeMode
import com.woka.core.data.SIZE_SCHEMA_VERSION
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.SortType
import com.woka.core.data.ViewLayout
import com.woka.core.data.WokaSettings
import com.woka.core.data.WokaStorage
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.persistSizeState
import com.woka.support.awaitSettingsWrite
import com.woka.support.awaitTrue
import com.woka.support.awaitWriterReady
import com.woka.support.onSettingsDisk
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The disk layer: what reaches `settings.json` and what comes back from it. The
 * setter coercions and the record bookkeeping are in `SettingsRecordsTest`.
 *
 * The three fields `migrateSizeSchema` touches — `defaultUseZoom`, `scalePreset`,
 * `defaultZoomPercent` — are asserted **on disk**. That began as a way around F8,
 * which is now fixed; it stays because the file is what the next launch reads, and
 * the last case here asserts the reopen as well.
 */
/**
 * What a repository with nothing behind it holds. Not `WokaSettings()`: the field's
 * own default stays 0 so a file written before it existed can still be recognised
 * as old, and the repository seeds the current version instead (F8).
 */
private val freshDefaults = WokaSettings(sizeSchemaVersion = SIZE_SCHEMA_VERSION)

class SettingsPersistenceTest {

    @Test
    fun `a change reaches the file and comes back on the next launch`() {
        onSettingsDisk { repository ->
            repository.setTheme("Dark")
            repository.addLinkedFolder("D:/Photos")
            awaitSettingsWrite(""""theme": "Dark"""")
            val reopened = SettingsRepositoryImpl().settings.value
            assertEquals("Dark", reopened.theme)
            assertEquals(listOf("D:/Photos"), reopened.linkedFolders)
        }
    }

    @Test
    fun `no file yet means the shipped defaults, and no crash`() {
        withTempStorageRoot {
            assertFalse(WokaStorage.settingsFile.exists())
            assertEquals(freshDefaults, SettingsRepositoryImpl().settings.value)
        }
    }

    @Test
    fun `a corrupt file falls back to the defaults instead of taking the app down`() {
        withTempStorageRoot { root ->
            root.mkdirs()
            WokaStorage.settingsFile.writeText("{ this was truncated mid-writ")
            assertEquals(freshDefaults, SettingsRepositoryImpl().settings.value)
        }
    }

    @Test
    fun `a corrupt file is replaced by the next write rather than kept forever`() {
        withTempStorageRoot { root ->
            root.mkdirs()
            WokaStorage.settingsFile.writeText("not json at all")
            val repository = SettingsRepositoryImpl()
            repository.awaitWriterReady()
            repository.setTheme("Light")
            awaitSettingsWrite(""""theme": "Light"""")
            assertEquals("Light", SettingsRepositoryImpl().settings.value.theme)
        }
    }

    @Test
    fun `a coerced value is what lands on disk, not the raw one`() {
        onSettingsDisk { repository ->
            repository.setDefaultZoomPercent(9000)
            repository.setResumeMinDuration(999)
            repository.setMediaVolume(4f)
            awaitSettingsWrite(""""defaultZoomPercent": 1600""")
            assertEquals(240, SettingsRepositoryImpl().settings.value.resumeMinDurationMinutes)
            assertEquals(MAX_MEDIA_VOLUME, SettingsRepositoryImpl().settings.value.mediaVolume)
        }
    }

    @Test
    fun `two folders keep their own layout and sort across a restart`() {
        onSettingsDisk { repository ->
            repository.setFolderLayout("D:/Photos", ViewLayout.BigGrid)
            repository.setFolderSort("D:/Photos", SortType.DateDesc)
            repository.setFolderLayout("D:/Films", ViewLayout.SmallGrid)
            repository.setFolderSort("D:/Films", SortType.NameDesc)
            awaitSettingsWrite("D:/Films")
            val reopened = SettingsRepositoryImpl().settings.value
            assertEquals(ViewLayout.BigGrid, reopened.folderLayouts["D:/Photos"])
            assertEquals(SortType.DateDesc, reopened.folderSorts["D:/Photos"])
            assertEquals(ViewLayout.SmallGrid, reopened.folderLayouts["D:/Films"])
            assertEquals(SortType.NameDesc, reopened.folderSorts["D:/Films"])
        }
    }

    @Test
    fun `resume settings survive the round trip`() {
        onSettingsDisk { repository ->
            repository.setResumeMode(ResumeMode.LongOnly)
            repository.setResumeMinDuration(45)
            repository.setWatchPosition("D:/Films/a.mkv", 640_000L, 7_200_000L)
            awaitSettingsWrite(""""resumeMode": "LongOnly"""")
            val reopened = SettingsRepositoryImpl().settings.value
            assertEquals(ResumeMode.LongOnly, reopened.resumeMode)
            assertEquals(45, reopened.resumeMinDurationMinutes)
            val record = reopened.watchRecords["D:/Films/a.mkv"]
            assertEquals(640_000L, record?.positionMs)
            // The duration rides along, or the grid would have nothing to divide by.
            assertEquals(7_200_000L, record?.durationMs)
        }
    }

    @Test
    fun `a burst of writes is coalesced into one valid file`() {
        onSettingsDisk { repository ->
            repeat(60) { repository.setTheme("Theme$it") }
            awaitSettingsWrite(""""theme": "Theme59"""")
            assertEquals("Theme59", SettingsRepositoryImpl().settings.value.theme)
            // `.woka-tmp` since ME-009 lifted the write into `replaceAtomically`.
            // Watching the old `.tmp` name here would pass by never existing.
            awaitTrue("the temp file to be cleaned up") {
                !File(WokaStorage.root, "settings.json.woka-tmp").exists()
            }
        }
    }

    @Test
    fun `size defaults chosen on a fresh install survive the first restart`() {
        onSettingsDisk { repository ->
            persistSizeState(repository, SizeMode.Zoom(200))
            awaitSettingsWrite(""""defaultZoomPercent": 200""")
            val restarted = SettingsRepositoryImpl().settings.value
            assertTrue(restarted.defaultUseZoom, "the migration ran on a file it wrote itself")
            assertEquals(200, restarted.defaultZoomPercent)
        }
    }
}
