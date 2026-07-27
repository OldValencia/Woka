package com.woka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.AppDependencies
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

enum class DockTab { Home, Navigation, Editor }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingDock(
    activeTab: DockTab,
    onTabSelected: (DockTab) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val pinnedSessions = settings.pinnedSessions
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {


        Row(
            modifier = Modifier.floatingPanel(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DockItem(
                text = "Home",
                iconRes = Icons.Static.Home,
                isActive = activeTab == DockTab.Home,
                activeColor = Theme.colors.accentHome,
                onClick = { onTabSelected(DockTab.Home) }
            )
            Box {
                val navInteractionSource = remember { MutableInteractionSource() }
                val isNavHovered by navInteractionSource.collectIsHoveredAsState()
                var isPopupHovered by remember { mutableStateOf(false) }
                val showPopup = (isNavHovered || isPopupHovered) && pinnedSessions.isNotEmpty()
                var delayedShowPopup by remember { mutableStateOf(false) }

                LaunchedEffect(showPopup) {
                    if (showPopup) {
                        delayedShowPopup = true
                    } else {
                        delay(150.milliseconds)
                        delayedShowPopup = false
                    }
                }

                DockItem(
                    text = "Library",
                    iconRes = Icons.Static.Folder,
                    isActive = activeTab == DockTab.Navigation,
                    activeColor = Theme.colors.accentFolders,
                    hasBadge = pinnedSessions.isNotEmpty(),
                    interactionSource = navInteractionSource,
                    onClick = { onTabSelected(DockTab.Navigation) }
                )

                if (delayedShowPopup) {
                    LibraryPopup(
                        pinnedSessions = pinnedSessions,
                        settingsRepo = settingsRepo,
                        onHoverEnter = { isPopupHovered = true },
                        onHoverExit = { isPopupHovered = false }
                    )
                }
            }
            DockItem(
                text = "Editor",
                iconRes = Icons.Static.Settings, // Replace with Editor icon if available
                isActive = activeTab == DockTab.Editor,
                activeColor = Theme.colors.accentEditor,
                onClick = { onTabSelected(DockTab.Editor) }
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(Theme.colors.border)
            )

            DockSecondaryItem(
                iconRes = Icons.Static.Search,
                hoverColor = Theme.colors.accentSearch,
                onClick = onSearchClick
            )
            DockSecondaryItem(
                iconRes = Icons.Static.Settings,
                hoverColor = Theme.colors.textSecondary,
                isWiggle = true,
                onClick = onSettingsClick
            )
        }
    }
}
