package com.woka.feature.viewer.pip

import androidx.compose.runtime.Composable
import com.woka.core.data.SettingsRepository

@Composable
expect fun GlobalPipWindow(settingsRepo: SettingsRepository)
