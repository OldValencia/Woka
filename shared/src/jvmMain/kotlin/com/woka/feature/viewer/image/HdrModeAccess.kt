package com.woka.feature.viewer.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.woka.app.collectField
import com.woka.core.data.HdrViewMode
import com.woka.core.data.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The HDR view mode as **Compose state**, for the two places that decode a picture
 * (ME-180).
 *
 * `HdrView.mode` is a plain getter — right for `displayable`, which is called on a
 * background thread and wants the value at that instant, and useless to a composable,
 * which needs to be *told* when it changes. Reading the getter inside a `remember` key
 * is exactly the bug this file exists to close: the key never changed, so the decode
 * never re-ran and flipping the mode did nothing until the file was reopened.
 *
 * The viewer does not use this — `MediaViewerActiveItemEffects` already receives the
 * whole `WokaSettings` and reads the field off it. The editor's proxy has no settings
 * anywhere near it, and threading a repository down through `rememberEditorFrame` to key
 * one `produceState` would be more plumbing than the seam it feeds.
 */
internal class HdrModeAccess : KoinComponent {

    private val settings: SettingsRepository by inject()

    @Composable
    fun current(): HdrViewMode {
        val mode by settings.collectField { it.hdrViewMode }
        return mode
    }

    fun set(mode: HdrViewMode) = settings.setHdrViewMode(mode)
}

@Composable
internal fun rememberHdrModeAccess(): HdrModeAccess = remember { HdrModeAccess() }
