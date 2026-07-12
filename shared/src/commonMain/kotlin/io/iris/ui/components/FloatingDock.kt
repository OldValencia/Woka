package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import io.iris.AppDependencies
import io.iris.navigation.Screen
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

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
        modifier = modifier
            .padding(bottom = 28.dp)
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
                    delay(150)
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
                Popup(
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, -35)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .width(220.dp)
                            .shadow(24.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                            .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
                            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
                            .onPointerEvent(PointerEventType.Enter) { isPopupHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { isPopupHovered = false }
                            .padding(6.dp)
                    ) {
                        BasicText(
                            text = "PINNED SESSIONS",
                            style = TextStyle(color = IrisTheme.colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        
                        pinnedSessions.forEach { session ->
                            val file = java.io.File(session.path)
                            val name = file.name.takeIf { it.isNotEmpty() } ?: session.path
                            
                            val src = remember { MutableInteractionSource() }
                            val hov by src.collectIsHoveredAsState()
                            val bg by animateColorAsState(if (hov) IrisTheme.colors.surfaceHover else Color.Transparent)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(interactionSource = src, indication = null) { 
                                        AppDependencies.router.navigateTo(Screen.Folder(session.path))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    text = name,
                                    style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 13.sp),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                if (hov) {
                                    Image(
                                        painter = painterResource(IrisIcons.Static.Close),
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { settingsRepo.removePinnedSession(session.path) }
                                            .padding(2.dp),
                                        colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
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

@Composable
private fun DockItem(
    text: String,
    iconRes: DrawableResource,
    isActive: Boolean,
    activeColor: Color,
    hasBadge: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> IrisTheme.colors.surfacePressed
            isHovered -> IrisTheme.colors.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) IrisTheme.colors.textPrimary else if (isHovered) IrisTheme.colors.textPrimary else IrisTheme.colors.textTertiary,
        animationSpec = tween(150)
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) activeColor else textColor,
        animationSpec = tween(150)
    )

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Image(
                painter = painterResource(iconRes),
                contentDescription = text,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(activeColor)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                )
            }
        }
        BasicText(
            text = text,
            style = TextStyle(
                color = textColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun DockSecondaryItem(
    iconRes: DrawableResource,
    hoverColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val iconTint by animateColorAsState(
        targetValue = if (isHovered) hoverColor else IrisTheme.colors.textTertiary,
        animationSpec = tween(150)
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(iconTint)
        )
    }
}
