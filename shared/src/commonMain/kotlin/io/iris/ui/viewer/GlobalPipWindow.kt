package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import io.iris.data.SettingsRepository

@Composable
expect fun GlobalPipWindow(settingsRepo: SettingsRepository)
