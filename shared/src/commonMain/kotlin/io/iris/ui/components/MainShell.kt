package io.iris.ui.components

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
import androidx.compose.ui.unit.dp
import io.iris.ui.theme.IrisTheme

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

    CompositionLocalProvider(
        LocalDockAccessories provides dockAccessories,
        LocalTopBarAccessories provides topBarAccessories
    ) {
        Box(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
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
                                            IrisTheme.colors.background.copy(alpha = 0.9f)
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
                                val topAccessory = dockAccessories.topAccessory
                                var savedTopAccessory by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                                if (topAccessory != null) savedTopAccessory = topAccessory
                                
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = topAccessory != null,
                                    enter = slideInVertically(
                                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                                        initialOffsetY = { it / 2 }
                                    ) + fadeIn(animationSpec = tween(150)),
                                    exit = slideOutVertically(
                                        animationSpec = tween(250, easing = FastOutLinearInEasing),
                                        targetOffsetY = { it / 2 }
                                    ) + fadeOut(animationSpec = tween(250))
                                ) {
                                    savedTopAccessory?.invoke()
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val leftAccessory = dockAccessories.leftAccessory
                                    var savedLeftAccessory by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                                    if (leftAccessory != null) savedLeftAccessory = leftAccessory

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = leftAccessory != null,
                                        enter = slideInVertically(
                                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                                            initialOffsetY = { it / 2 }
                                        ) + fadeIn(animationSpec = tween(150)),
                                        exit = slideOutVertically(
                                            animationSpec = tween(250, easing = FastOutLinearInEasing),
                                            targetOffsetY = { it / 2 }
                                        ) + fadeOut(animationSpec = tween(250))
                                    ) {
                                        savedLeftAccessory?.invoke()
                                    }
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
