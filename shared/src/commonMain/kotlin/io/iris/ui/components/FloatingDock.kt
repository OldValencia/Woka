package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.iris.AppDependencies
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
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
            modifier = Modifier
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .background(IrisTheme.colors.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DockItem(
                text = "Home",
                iconRes = IrisIcons.Static.Home,
                isActive = activeTab == DockTab.Home,
                activeColor = IrisTheme.colors.accentHome,
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
                iconRes = IrisIcons.Static.Folder,
                isActive = activeTab == DockTab.Navigation,
                activeColor = IrisTheme.colors.accentFolders,
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
            iconRes = IrisIcons.Static.Settings, // Replace with Editor icon if available
            isActive = activeTab == DockTab.Editor,
            activeColor = IrisTheme.colors.accentEditor,
            onClick = { onTabSelected(DockTab.Editor) }
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(20.dp)
                .background(IrisTheme.colors.border)
        )

        DockSecondaryItem(
            iconRes = IrisIcons.Static.Search,
            hoverColor = IrisTheme.colors.accentSearch,
            onClick = onSearchClick
        )
        DockSecondaryItem(
            iconRes = IrisIcons.Static.Settings,
            hoverColor = IrisTheme.colors.textSecondary,
            isWiggle = true,
            onClick = onSettingsClick
        )
    }
    }
}
