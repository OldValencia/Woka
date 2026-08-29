package com.woka.unit

import com.woka.core.data.MAX_MEDIA_VOLUME
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.volumeCeiling
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What the stored level may be, and what turning the boost off does to it.
 *
 * **`setMediaVolume`'s clamp is the gate.** It capped every write at 1f, so a slider that
 * reached 300 % would have been written down as 100 % and come back at 100 % on the next
 * restart — the failure that defeats the whole feature while every control on screen looks
 * right.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundBoostSettingsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun withSettings(block: (SettingsRepositoryImpl) -> Unit) =
        withTempStorageRoot { block(SettingsRepositoryImpl()) }

    @Test
    fun `the range ends where the switch says it does`() {
        assertEquals(1f, volumeCeiling(false))
        assertEquals(MAX_MEDIA_VOLUME, volumeCeiling(true))
    }

    @Test
    fun `a boosted level is stored rather than capped at 100 percent`() {
        withSettings { settings ->
            settings.setSoundBoost(true)
            settings.setMediaVolume(2.5f)
            assertEquals(2.5f, settings.settings.value.mediaVolume)

            settings.setMediaVolume(9f)
            assertEquals(MAX_MEDIA_VOLUME, settings.settings.value.mediaVolume, "the ceiling let go")
        }
    }

    @Test
    fun `turning the boost off brings the level down with it`() {
        withSettings { settings ->
            settings.setSoundBoost(true)
            settings.setMediaVolume(2.4f)

            settings.setSoundBoost(false)
            assertFalse(settings.settings.value.soundBoost)
            assertEquals(
                1f,
                settings.settings.value.mediaVolume,
                "a level left above a slider that no longer reaches it"
            )
        }
    }

    /** Off is the default, so an existing settings file plays exactly as it did. */
    @Test
    fun `boost is off until it is asked for`() {
        withSettings { settings -> assertFalse(settings.settings.value.soundBoost) }
    }
}
