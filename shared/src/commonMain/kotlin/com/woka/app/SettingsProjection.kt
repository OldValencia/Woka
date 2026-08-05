package com.woka.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Subscribes to one field of the settings rather than the whole object.
 *
 * [WokaSettings] is a single record, so collecting it wholesale means every write
 * — the volume slider, the play head that the resume feature stores every five
 * seconds — produces a new instance and invalidates whatever scope did the
 * collecting. In `App` that scope contains the shell, the screen transition and
 * the current screen, so a playing video recomposed the entire tree on a timer
 * (§10.1). `distinctUntilChanged` on a projection stops the write at the flow.
 *
 * The initial value is read straight off the backing StateFlow rather than waiting
 * a frame for the first emission, so nothing renders against a placeholder.
 */
@Composable
fun <T> SettingsRepository.collectField(select: (WokaSettings) -> T): State<T> =
    remember(this, select) { settings.map(select).distinctUntilChanged() }
        .collectAsState(select(settings.value))
