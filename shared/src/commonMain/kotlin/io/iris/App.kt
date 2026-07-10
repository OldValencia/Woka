package io.iris

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.iris.navigation.Router
import io.iris.navigation.Screen
import io.iris.ui.HomeScreen
import io.iris.ui.FolderScreen
import io.iris.ui.HomeDependencies
import io.iris.ui.theme.IrisTheme
import io.iris.data.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.iris.ui.components.SettingsOverlay
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import io.iris.ui.icons.IrisIcons

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
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                }
            ) { screen ->
                when (screen) {
                    is Screen.Home -> HomeScreen(
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) },
                        onRemoveFolder = { path -> HomeDependencies.viewModel.removeLinkedFolder(path) }
                    )
                    is Screen.Folder -> FolderScreen(
                        path = screen.path,
                        stack = stack,
                        onPopTo = { s -> router.popTo(s) },
                        onNavigateToFolder = { path -> router.navigateTo(Screen.Folder(path)) }
                    )
                }
            }

            // Settings Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(IrisTheme.colors.surface)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { isSettingsOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(IrisIcons.Static.Settings),
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
                )
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