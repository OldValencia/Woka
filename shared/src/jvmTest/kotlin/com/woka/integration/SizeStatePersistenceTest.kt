package com.woka.integration

import com.woka.core.data.ScalePreset
import com.woka.core.data.WokaStorage
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.persistSizeState
import com.woka.support.awaitSettingsWrite
import com.woka.support.onSettingsDisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What "remember the size I left it at" writes. Asserted on disk rather than
 * through a reopened repository — see F8 in `SettingsPersistenceTest`.
 */
class SizeStatePersistenceTest {

    @Test
    fun `a persisted free zoom is written as a percentage, not a preset`() {
        onSettingsDisk { repository ->
            persistSizeState(repository, SizeMode.Zoom(240))
            assertTrue(repository.settings.value.defaultUseZoom)
            assertEquals(240, repository.settings.value.defaultZoomPercent)
            awaitSettingsWrite(""""defaultZoomPercent": 240""")
            assertTrue(WokaStorage.settingsFile.readText().contains(""""defaultUseZoom": true"""))
        }
    }

    @Test
    fun `a persisted preset turns the free zoom back off`() {
        onSettingsDisk { repository ->
            persistSizeState(repository, SizeMode.Zoom(240))
            persistSizeState(repository, SizeMode.Preset(ScalePreset.Fill))
            assertFalse(repository.settings.value.defaultUseZoom)
            assertEquals(ScalePreset.Fill, repository.settings.value.scalePreset)
            awaitSettingsWrite(""""scalePreset": "Fill"""")
            // `encodeDefaults` is off, so `false` is written by being absent —
            // asserting its presence would be asserting the wrong thing.
            assertFalse(WokaStorage.settingsFile.readText().contains("defaultUseZoom"))
        }
    }

    @Test
    fun `a zoom outside the allowed range is clamped before it is written`() {
        onSettingsDisk { repository ->
            persistSizeState(repository, SizeMode.Zoom(9000))
            assertEquals(1600, repository.settings.value.defaultZoomPercent)
            awaitSettingsWrite(""""defaultZoomPercent": 1600""")
        }
    }
}
