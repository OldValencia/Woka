package com.woka.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.woka.ui.theme.Theme

@Composable
fun MainShell(
    topBarArea: @Composable (content: @Composable () -> Unit) -> Unit,
    currentPath: String?,
    activeTab: DockTab,
    onTabSelected: (DockTab) -> Unit,
    onCloseWindow: () -> Unit,
    onMinimizeWindow: () -> Unit,
    onMaximizeWindow: () -> Unit,
    isMaximized: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onBreadcrumbClick: ((String) -> Unit)?,
    linkedFolders: List<String> = emptyList(),
    content: @Composable () -> Unit
) {
    val dockAccessories = remember { DockAccessoriesState() }
    val topBarAccessories = remember { TopBarAccessoriesState() }
    val globalShortcuts = remember { GlobalShortcutState() }

    CompositionLocalProvider(
        LocalDockAccessories provides dockAccessories,
        LocalTopBarAccessories provides topBarAccessories,
        LocalGlobalShortcuts provides globalShortcuts
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.background)
                // Runs in the preview (tunneling) phase from the root, so a
                // registered screen shortcut sees keys before any focused button
                // and can consume them (see GlobalShortcutState).
                .onPreviewKeyEvent { globalShortcuts.onPreviewKey?.invoke(it) ?: false }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                topBarArea {
                    GlobalTopBar(
                        currentPath = currentPath,
                        onClose = onCloseWindow,
                        onMinimize = onMinimizeWindow,
                        onMaximize = onMaximizeWindow,
                        onNavigateBack = onNavigateBack,
                        onBreadcrumbClick = onBreadcrumbClick,
                        linkedFolders = linkedFolders,
                        isMaximized = isMaximized
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    content()

                    if (dockAccessories.isVisible) {
                        // Protection gradient/shadow behind the dock
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Theme.colors.background.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                        )

                        // Floating Dock + Accessories
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 60.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AnimatedAccessory(dockAccessories.topAccessory)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AnimatedAccessory(dockAccessories.leftAccessory)
                                    FloatingDock(
                                        activeTab = activeTab,
                                        onTabSelected = onTabSelected,
                                        onSearchClick = onOpenSearch,
                                        onSettingsClick = onOpenSettings
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedAccessory(accessory: (@Composable () -> Unit)?) {
    var savedAccessory by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    if (accessory != null) savedAccessory = accessory

    AnimatedVisibility(
        visible = accessory != null,
        enter = slideInVertically(
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 2 }
        ) + fadeIn(animationSpec = tween(150)),
        exit = slideOutVertically(
            animationSpec = tween(250, easing = FastOutLinearInEasing),
            targetOffsetY = { it / 2 }
        ) + fadeOut(animationSpec = tween(250))
    ) {
        savedAccessory?.invoke()
    }
}
