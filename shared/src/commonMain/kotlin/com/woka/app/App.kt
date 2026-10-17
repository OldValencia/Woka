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
import com.woka.app.shell.MainShell
import com.woka.app.shell.StatusBanners
import com.woka.feature.folder.FolderScrollMemory
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
    val scrollMemory: FolderScrollMemory by inject()
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
    val currentScreen = stack.lastOrNull() ?: Screen.Welcome

    ThumbnailHousekeeping()

    Theme(darkTheme = isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.colors.background)
                .border(1.dp, Theme.colors.border, RoundedCornerShape(12.dp))
        ) {
            val currentPath = (currentScreen as? Screen.Folder)?.path

            var isSearchOpen by remember { mutableStateOf(false) }

            MainShell(
                topBarArea = topBarArea,
                currentPath = currentPath,
                onCloseWindow = onCloseWindow,
                onMinimizeWindow = onMinimizeWindow,
                onMaximizeWindow = onMaximizeWindow,
                isMaximized = isMaximized,
                onOpenSettings = { isSettingsOpen = true },
                onOpenSearch = { isSearchOpen = true }
            ) {
            ScreenHost(
                currentScreen = currentScreen,
                onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
            )
            }

            // Above every screen, because the work being reported outlives the screen
            // it started on: an export keeps running once the viewer opens over the
            // grid, and a save keeps running once the editor has closed (ME-130).
            StatusBanners()

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
