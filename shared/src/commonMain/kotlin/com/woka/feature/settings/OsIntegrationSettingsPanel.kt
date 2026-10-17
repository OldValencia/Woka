package com.woka.feature.settings

import androidx.compose.runtime.Composable
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings

/**
 * Operating system integrations:
 * Explorer context menus, single-instance routing, and system media controls.
 */
@Composable
expect fun OsIntegrationSettingsPanel(settings: WokaSettings, repo: SettingsRepository)
