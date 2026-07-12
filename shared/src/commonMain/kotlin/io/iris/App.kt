package io.iris

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import io.iris.data.SettingsRepository
import io.iris.navigation.Router
import io.iris.navigation.Screen
import io.iris.ui.FolderScreen
import io.iris.ui.HomeScreen
import io.iris.ui.components.DockTab
import io.iris.ui.components.MainShell
import io.iris.ui.components.SettingsOverlay
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    val settings by settingsRepo.settings.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (settings.theme) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemDark
    }

    var isSettingsOpen by remember { mutableStateOf(false) }
    val stack by router.stack.collectAsState()
    val currentScreen = stack.lastOrNull() ?: Screen.Home

    IrisTheme(darkTheme = isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(IrisTheme.colors.background)
                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
        ) {
            val currentPath = (currentScreen as? Screen.Folder)?.path
            val activeTab = when (currentScreen) {
                is Screen.Home -> DockTab.Home
                is Screen.Folder -> DockTab.Navigation
                else -> DockTab.Editor
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
                linkedFolders = settings.linkedFolders
            ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val targetDepth = if (targetState is Screen.Folder) (targetState as Screen.Folder).path.length else 0
                    val initialDepth = if (initialState is Screen.Folder) (initialState as Screen.Folder).path.length else 0
    
                    if (targetDepth > initialDepth) {
                        (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(animationSpec = tween(220))
                        )
                    } else {
                        (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(animationSpec = tween(220))
                        )
                    }
                }
            ) { screen ->
                when (screen) {
                    is Screen.Home -> HomeScreen(
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
                    )
                    is Screen.Folder -> FolderScreen(
                        path = screen.path,
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
                    )
                }
            }
            }

            // Settings Overlay
            SettingsOverlay(
                isOpen = isSettingsOpen,
                onClose = { isSettingsOpen = false },
                currentTheme = settings.theme,
                onThemeChange = { settingsRepo.setTheme(it) },
                globalShowTitles = settings.showTitles,
                onGlobalShowTitlesChange = { settingsRepo.setShowTitles(it) },
                showHiddenFiles = settings.showHiddenFiles,
                onShowHiddenFilesChange = { settingsRepo.setShowHiddenFiles(it) }
            )

            // Search Overlay
            io.iris.ui.components.SearchOverlay(
                isOpen = isSearchOpen,
                onClose = { isSearchOpen = false },
                linkedFolders = settings.linkedFolders,
                onResultClick = { path -> router.navigateTo(Screen.Folder(path)) }
            )
        }
    }
}