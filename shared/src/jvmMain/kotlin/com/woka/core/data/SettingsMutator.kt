package com.woka.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The live settings record and the one path every change takes to reach it.
 *
 * Extracted when `SettingsRepositoryImpl` hit the file-size mandate a second time
 * (ME-006). The first time, the setter *bodies* moved to `SettingsMutations`; by
 * the second there was nothing left to move, so the class itself was split into
 * [LibrarySettingsImpl] and [PlaybackSettingsImpl] — and both halves need the same
 * mutable cell and the same "copy, then schedule a save" rule. This is that shared
 * piece, not a layer.
 *
 * Named for what it does to a `WokaSettings` rather than for where it sits:
 * `SettingsStore` is already the disk underneath, and `*State` would promise an
 * immutable snapshot (`code-style/naming.md`).
 *
 * `update` is no longer `inline` as it was inside the old class. Settings are
 * written when a person changes one, so the lambda allocation is irrelevant, and
 * inlining across files would drag the mutable cell into the public API through
 * `@PublishedApi`.
 */
internal class SettingsMutator(scope: CoroutineScope) {

    // Seeded at the current schema version, not at the field's default: with no
    // file behind it there is nothing to migrate, and leaving it at 0 made the
    // next launch treat this session's own writes as pre-Step-8b records (F8).
    private val _settings = MutableStateFlow(WokaSettings(sizeSchemaVersion = SIZE_SCHEMA_VERSION))

    val settings: StateFlow<WokaSettings> = _settings.asStateFlow()

    // Disk access, debouncing and atomic writes live in SettingsStore (§2 size mandate).
    private val store = SettingsStore(scope)

    fun load() {
        store.load()?.let { _settings.value = it }
        store.start { _settings.value }
    }

    /** The update-copy-save pattern every setter goes through. */
    fun update(transform: (WokaSettings) -> WokaSettings) {
        _settings.update { current ->
            store.requestSave()
            transform(current)
        }
    }

    /**
     * For the one setter that decides *whether* to save. Re-adding a linked folder
     * that is already there must not touch state or schedule a pointless write, so
     * it needs to inspect the current record before committing to a save.
     */
    fun updateIf(transform: (WokaSettings) -> WokaSettings?) {
        _settings.update { current ->
            val next = transform(current) ?: return@update current
            store.requestSave()
            next
        }
    }
}
