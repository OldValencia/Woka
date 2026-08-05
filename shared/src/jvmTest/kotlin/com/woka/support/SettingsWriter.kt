package com.woka.support

import com.woka.core.data.SettingsRepository
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.WokaStorage

/** Distinctive enough that no test could set it by accident. */
private const val PROBE_THEME = "woka-writer-ready-probe"

/**
 * Blocks until the settings writer is actually listening.
 *
 * `SettingsStore.start` subscribes to its save channel inside a coroutine
 * launched on `Dispatchers.IO`, and the channel has no replay — so a
 * `requestSave()` issued in the moment after the constructor returns can reach a
 * flow with no collector and be dropped (**F9**). Production never notices: the
 * next change writes, and a shutdown hook flushes on exit. A test that changes
 * one setting and waits for the file would hang, which is exactly what four of
 * these tests did before this existed.
 *
 * Asking repeatedly at a cadence slower than the 150 ms debounce, rather than
 * hammering: re-emitting faster than the debounce would keep cancelling the
 * write it is waiting for.
 */
fun SettingsRepository.awaitWriterReady(timeoutMs: Long = 10_000L) {
    val original = settings.value.theme
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        setTheme(PROBE_THEME)
        val landed = waitFor(400L) {
            WokaStorage.settingsFile.isFile && WokaStorage.settingsFile.readText().contains(PROBE_THEME)
        }
        if (landed) {
            setTheme(original)
            return
        }
    }
    throw AssertionError("the settings writer never came up within ${timeoutMs}ms")
}

/** A repository on a throwaway root whose writer is already listening. */
fun onSettingsDisk(block: (SettingsRepositoryImpl) -> Unit) = withTempStorageRoot {
    val repository = SettingsRepositoryImpl()
    repository.awaitWriterReady()
    block(repository)
}

/** Waits for [contains] to appear in the settings file the store actually wrote. */
fun awaitSettingsWrite(contains: String) = awaitTrue("settings.json to contain '$contains'") {
    WokaStorage.settingsFile.isFile && WokaStorage.settingsFile.readText().contains(contains)
}
