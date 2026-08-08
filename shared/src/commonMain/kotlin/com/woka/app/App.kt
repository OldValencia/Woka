package com.woka.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.woka.core.data.SettingsRepository
import com.woka.app.navigation.Router
import com.woka.app.navigation.Screen
import com.woka.app.navigation.ScreenHost
import com.woka.app.shell.DockTab
import com.woka.app.shell.MainShell
import com.woka.core.ui.components.FrameExportBanner
import com.woka.feature.search.SearchOverlay
import com.woka.feature.settings.SettingsOverlay
import com.woka.core.ui.theme.Theme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.woka.feature.viewer.pip.GlobalPipWindow
import java.io.File

object AppDependencies : KoinComponent {
    val router: Router by inject()
    val settingsRepository: SettingsRepository by inject()
}

@Composable
fun App(
    topBarArea: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    onCloseWindow: () -> Unit = {},
    onMinimizeWindow: () -> Unit = {},
    onMaximizeWindow: () -> Unit = {},
    isMaximized: Boolean = false
) {
    val router = remember { AppDependencies.router }
    val settingsRepo = remember { AppDependencies.settingsRepository }
    // Two projections, not the whole record: see `collectField` (§10.1).
    val theme by settingsRepo.collectField { it.theme }
    val linkedFolders by settingsRepo.collectField { it.linkedFolders }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (theme) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemDark
    }

    var isSettingsOpen by remember { mutableStateOf(false) }
    val stack by router.stack.collectAsState()
    val currentScreen = stack.lastOrNull() ?: Screen.Home

    Theme(darkTheme = isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.colors.background)
                .border(1.dp, Theme.colors.border, RoundedCornerShape(12.dp))
        ) {
            val currentPath = (currentScreen as? Screen.Folder)?.path
            val activeTab = when (currentScreen) {
                is Screen.Home -> DockTab.Home
                is Screen.Folder -> DockTab.Navigation
            }

            var isSearchOpen by remember { mutableStateOf(false) }

            MainShell(
                topBarArea = topBarArea,
                currentPath = currentPath,
                activeTab = activeTab,
                onTabSelected = { tab -> 
                    if (tab == DockTab.Home) {
                        router.popTo(Screen.Home)
                    }
                },
                onCloseWindow = onCloseWindow,
                onMinimizeWindow = onMinimizeWindow,
                onMaximizeWindow = onMaximizeWindow,
                isMaximized = isMaximized,
                onOpenSettings = { isSettingsOpen = true },
                onOpenSearch = { isSearchOpen = true },
                onNavigateBack = if (stack.size > 1) { { router.pop() } } else null,
                onBreadcrumbClick = { path -> router.navigateTo(Screen.Folder(path)) },
                linkedFolders = linkedFolders
            ) {
            ScreenHost(
                currentScreen = currentScreen,
                onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
            )
            }

            // Above every screen, because an export started in the grid keeps
            // running once the viewer opens on top of it.
            FrameExportBanner()

            // Settings Overlay
            SettingsOverlay(
                isOpen = isSettingsOpen,
                onClose = { isSettingsOpen = false },
                repo = settingsRepo
            )

            // Search Overlay
            SearchOverlay(
                isOpen = isSearchOpen,
                onClose = { isSearchOpen = false },
                linkedFolders = linkedFolders,
                onResultClick = { path ->
                    val file = File(path)
                    val parent = file.parent
                    if (parent != null) {
                        val targetScreen = Screen.Folder(parent, highlightFile = file.name)
                        val current = router.stack.value.lastOrNull() as? Screen.Folder
                        if (current != null && current.path == parent) {
                            router.replaceTop(targetScreen)
                        } else {
                            router.navigateTo(targetScreen)
                        }
                    } else {
                        router.navigateTo(Screen.Folder(path))
                    }
                }
            )
            // Global PIP Player Window
            GlobalPipWindow(settingsRepo = settingsRepo)
        }
    }
}
