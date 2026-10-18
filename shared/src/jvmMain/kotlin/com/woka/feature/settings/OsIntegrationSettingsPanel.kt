package com.woka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.ALL_SUPPORTED_MEDIA_EXTENSIONS
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import com.woka.core.system.FolderContextMenu
import com.woka.core.system.WindowsFileAssociations
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    delay(4000)
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

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    enabled: Boolean = true,
    statusMessage: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val btnBg = if (!enabled) {
        Theme.colors.surfaceHover
    } else if (isHovered) {
        Theme.colors.accentSoft
    } else {
        Theme.colors.surfaceElevated
    }
    val btnBorder = if (!enabled) {
        Theme.colors.border
    } else if (isHovered) {
        Theme.colors.accent
    } else {
        Theme.colors.border
    }
    val textColor = if (!enabled) {
        Theme.colors.textTertiary
    } else if (isHovered) {
        Theme.colors.accent
    } else {
        Theme.colors.textPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.surfacePressed)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = Theme.colors.textTertiary,
                    fontSize = 11.sp
                )
            )
            if (statusMessage != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = statusMessage,
                    style = TextStyle(
                        color = Theme.colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(btnBg)
                .border(1.dp, btnBorder, RoundedCornerShape(8.dp))
                .then(
                    if (enabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = buttonText,
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

