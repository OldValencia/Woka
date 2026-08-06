package com.woka.support

import com.woka.core.data.SettingsRepository
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.WokaStorage

/** Distinctive enough that no test could set it by accident. */
private const val PROBE_THEME = "woka-writer-ready-probe"

/**
 * Blocks until the settings writer has actually written something.
 *
 * This began as the workaround for F9 — the save channel had no replay, so a
 * `requestSave()` issued before `start`'s collector existed was dropped, and four
 * of these tests hung on it. F9 is fixed (`replay = 1`), so nothing is lost any
 * more; what remains is *timing*: the writer subscribes on `Dispatchers.IO` and
 * debounces 150 ms, so a test that changes one setting and immediately reads the
 * file still races it. This is the "the file is now live" barrier, not a bug
 * workaround.
 *
 * Asking repeatedly at a cadence slower than the debounce, rather than hammering:
 * re-emitting faster than 150 ms would keep cancelling the write it waits for.
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
