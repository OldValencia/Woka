package io.iris.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.iris.AppDependencies
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
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
        modifier = modifier.padding(bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = activeTab == DockTab.Home,
            enter = fadeIn() + 
                    expandHorizontally(expandFrom = Alignment.End) + 
                    scaleIn(initialScale = 0.5f, transformOrigin = TransformOrigin(1f, 0.5f)),
            exit = fadeOut() + 
                   shrinkHorizontally(shrinkTowards = Alignment.End) + 
                   scaleOut(targetScale = 0.5f, transformOrigin = TransformOrigin(1f, 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(52.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = Color.Black.copy(alpha = 0.5f),
                        ambientColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .background(IrisTheme.colors.surface, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { 
                        val p = io.iris.utils.openDirectoryPicker("Link folder")
                        if (p != null) settingsRepo.addLinkedFolder(p)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(IrisIcons.Static.Add),
                    contentDescription = "Add Linked Folder",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                )
            }
        }

        Row(
            modifier = Modifier
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .background(IrisTheme.colors.surface, RoundedCornerShape(20.dp))
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
            onClick = onSettingsClick
        )
    }
    }
}
