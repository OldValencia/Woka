package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.SettingsRepository

@Composable
expect fun GlobalPipWindow(settingsRepo: SettingsRepository)
