package com.woka.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The whole settings contract, assembled from its two halves.
 *
 * The class was split in ME-006 under the file-size mandate: forty-odd one-line
 * overrides had filled it back to the ceiling after the first split sent their
 * bodies to `SettingsMutations`. Kotlin delegation means the halves compose
 * without a base class and without callers noticing — `SettingsRepository` still
 * extends both, so nothing that holds one had to change.
 *
 * One [SettingsMutator] is shared by both halves, passed as a constructor
 * parameter so the delegation list can reach it. That sharing is load bearing:
 * two mutable cells over one `settings.json` would be two writers disagreeing,
 * which is exactly the failure `code-style/state.md` warns about. It is
 * per-instance and **must not** become a singleton — tests build several
 * repositories over separate temporary storage roots.
 */
class SettingsRepositoryImpl private constructor(
    private val mutator: SettingsMutator
) : SettingsRepository,
    LibrarySettings by LibrarySettingsImpl(mutator),
    PlaybackSettings by PlaybackSettingsImpl(mutator) {

    constructor() : this(SettingsMutator(CoroutineScope(Dispatchers.IO)))

    override val settings: StateFlow<WokaSettings> get() = mutator.settings

    init {
        mutator.load()
    }

    override fun setEditorToolEnabled(id: String, enabled: Boolean) =
        mutator.update { it.withEditorTool(id, enabled) }
}
