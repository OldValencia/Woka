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
import com.woka.feature.search.SearchResults
import com.woka.feature.search.SearchState
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

            MainShell(
                topBarArea = topBarArea,
                currentPath = currentPath,
                onCloseWindow = onCloseWindow,
                onMinimizeWindow = onMinimizeWindow,
                onMaximizeWindow = onMaximizeWindow,
                isMaximized = isMaximized,
                onOpenSettings = { isSettingsOpen = true },
                onOpenSearch = { SearchState.focus.requestFocus() }
            ) {
                ScreenHost(
                    currentScreen = currentScreen,
                    onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
                )
                // Over the screen, not instead of it (SIDE-008): the folder stays
                // composed underneath, so clearing the box returns to it exactly.
                if (SearchState.query.isNotBlank()) {
                    SearchResults(currentPath, linkedFolders, onOpen = { file -> openHit(router, file) })
                }
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

            // Global PIP Player Window
            GlobalPipWindow(settingsRepo = settingsRepo)
        }
    }
}

/**
 * A search hit opened: a folder is gone into, a file opens in the viewer over its
 * folder with its tile marked. The query is cleared either way — the result was
 * the question's answer, and the folder is where the user now wants to be.
 */
private fun openHit(router: Router, file: File) {
    SearchState.query = ""
    val parent = file.parent
    if (file.isDirectory || parent == null) {
        router.navigateTo(Screen.Folder(file.absolutePath))
        return
    }
    router.navigateTo(
        Screen.Folder(
            path = parent,
            highlightFile = file.name,
            openViewerForId = file.absolutePath,
            nonce = System.nanoTime()
        )
    )
}
