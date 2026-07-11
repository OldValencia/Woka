package io.iris

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.iris.data.SettingsRepository
import io.iris.navigation.Router
import io.iris.navigation.Screen
import io.iris.ui.FolderScreen
import io.iris.ui.HomeDependencies
import io.iris.ui.HomeScreen
import io.iris.ui.components.SettingsOverlay
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppDependencies : KoinComponent {
    val router: Router by inject()
    val settingsRepository: SettingsRepository by inject()
}

@Composable
fun App() {
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

    IrisTheme(darkTheme = isDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            val stack by router.stack.collectAsState()
            val currentScreen = stack.lastOrNull() ?: Screen.Home
            
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
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) },
                        onRemoveFolder = { path -> HomeDependencies.viewModel.removeLinkedFolder(path) },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                    is Screen.Folder -> FolderScreen(
                        path = screen.path,
                        stack = stack,
                        onPopTo = { s -> router.popTo(s) },
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                }
            }

            // Settings Overlay
            SettingsOverlay(
                isOpen = isSettingsOpen,
                onClose = { isSettingsOpen = false },
                currentTheme = settings.theme,
                onThemeChange = { settingsRepo.setTheme(it) }
            )
        }
    }
}