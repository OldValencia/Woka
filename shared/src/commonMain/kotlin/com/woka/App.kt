package com.woka

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
import com.woka.data.SettingsRepository
import com.woka.navigation.Router
import com.woka.navigation.Screen
import com.woka.ui.FolderScreen
import com.woka.ui.HomeScreen
import com.woka.ui.components.DockTab
import com.woka.ui.components.MainShell
import com.woka.ui.components.SearchOverlay
import com.woka.ui.components.SettingsOverlay
import com.woka.ui.theme.Theme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.woka.ui.viewer.GlobalPipWindow

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
                linkedFolders = settings.linkedFolders
            ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val targetDepth = if (targetState is Screen.Folder) (targetState as Screen.Folder).path.length else 0
                    val initialDepth = if (initialState is Screen.Folder) (initialState as Screen.Folder).path.length else 0
    
                    val targetFolder = targetState as? Screen.Folder
                    val initialFolder = initialState as? Screen.Folder
                    
                    if (targetFolder != null && initialFolder != null && targetFolder.path == initialFolder.path) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetDepth > initialDepth) {
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
                    is Screen.Folder -> {
                        FolderScreen(
                            path = screen.path,
                            highlightFile = screen.highlightFile,
                            openViewerForId = screen.openViewerForId,
                            onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
                        )
                    }
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
                onShowHiddenFilesChange = { settingsRepo.setShowHiddenFiles(it) },
                carouselMode = settings.mediaViewerCarouselMode,
                onCarouselModeChange = { settingsRepo.setCarouselMode(it) },
                rememberSizeState = settings.rememberSizeState,
                onRememberSizeStateChange = { settingsRepo.setRememberSizeState(it) },
                defaultUseZoom = settings.defaultUseZoom,
                onDefaultUseZoomChange = { settingsRepo.setDefaultUseZoom(it) },
                defaultPreset = settings.scalePreset,
                onDefaultPresetChange = { settingsRepo.setScalePreset(it) },
                defaultZoomPercent = settings.defaultZoomPercent,
                onDefaultZoomPercentChange = { settingsRepo.setDefaultZoomPercent(it) }
            )

            // Search Overlay
            SearchOverlay(
                isOpen = isSearchOpen,
                onClose = { isSearchOpen = false },
                linkedFolders = settings.linkedFolders,
                onResultClick = { path -> 
                    val file = java.io.File(path)
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