package io.iris.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
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

    CompositionLocalProvider(LocalDockAccessories provides dockAccessories) {
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
                                dockAccessories.topAccessory?.invoke()
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    dockAccessories.leftAccessory?.invoke()
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
