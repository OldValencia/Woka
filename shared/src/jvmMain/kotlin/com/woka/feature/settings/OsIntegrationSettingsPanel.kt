package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.ALL_SUPPORTED_MEDIA_EXTENSIONS
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import com.woka.core.system.FolderContextMenu
import com.woka.core.system.WindowsFileAssociations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Operating system integration settings panel.
 *
 * Provides controls for file associations, Windows Explorer folder context menu
 * integration, and system-level media keys.
 */
@Composable
actual fun OsIntegrationSettingsPanel(settings: WokaSettings, repo: SettingsRepository) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("OS Integration")
        Spacer(modifier = Modifier.height(6.dp))
        PanelHint("Integrate Woka with Windows Explorer and system shortcuts")
        Spacer(modifier = Modifier.height(14.dp))

        val isWindows = remember { FolderContextMenu.isSupported() }
        var isContextMenuRegistered by remember { mutableStateOf(FolderContextMenu.isRegistered()) }
        var isAssociating by remember { mutableStateOf(false) }
        var assocStatus by remember { mutableStateOf<String?>(null) }
        val coroutineScope = rememberCoroutineScope()

        SettingsActionRow(
            title = "Default Media App",
            subtitle = if (isWindows) {
                "Associate Woka with ${ALL_SUPPORTED_MEDIA_EXTENSIONS.size} supported media formats in Windows"
            } else {
                "Available on Windows only"
            },
            buttonText = if (isAssociating) "Associating…" else "Associate Formats",
            enabled = isWindows && !isAssociating,
            statusMessage = assocStatus,
            onClick = {
                isAssociating = true
                coroutineScope.launch {
                    val success = withContext(Dispatchers.IO) { WindowsFileAssociations.register() }
                    if (success) {
                        WindowsFileAssociations.openDefaultAppsSettings()
                        assocStatus = "Registered ${ALL_SUPPORTED_MEDIA_EXTENSIONS.size} formats. Choose Woka in Windows Settings."
                    } else {
                        assocStatus = "Could not register file associations."
                    }
                    isAssociating = false
                    delay(4000.milliseconds)
                    assocStatus = null
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingsToggleRow(
            title = "Explorer Context Menu",
            subtitle = if (isWindows) {
                "Show \"Open with Woka\" on folders in Windows Explorer"
            } else {
                "Available on Windows Explorer only"
            },
            checked = isContextMenuRegistered && isWindows,
            onCheckedChange = { enable ->
                if (!isWindows) return@SettingsToggleRow
                if (enable) {
                    if (FolderContextMenu.register()) {
                        isContextMenuRegistered = true
                    }
                } else {
                    if (FolderContextMenu.unregister()) {
                        isContextMenuRegistered = false
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingsToggleRow(
            title = "Hardware Media Keys",
            subtitle = "Control video playback using system media keys and Now Playing notifications",
            checked = settings.systemMediaControls,
            onCheckedChange = { repo.setSystemMediaControls(it) }
        )
    }
}
