package com.woka.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.woka.feature.folder.FolderScreen
import com.woka.feature.home.HomeScreen

private const val TRANSITION_MS = 220

/**
 * Draws whichever [Screen] is on top of the stack, and the slide between them.
 * Split out of `App.kt` (§11): `App` wires the shell, the overlays and the theme,
 * while which screen is showing — and how it arrives — is navigation's business.
 *
 * Direction comes from path length, so going deeper slides in from the right and
 * going back slides in from the left. Re-entering the *same* folder path animates
 * not at all: it happens on an in-place `replaceTop`, where a slide would read as
 * a navigation that did not occur.
 */
@Composable
fun ScreenHost(
    currentScreen: Screen,
    onNavigateToFolder: (String) -> Unit
) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val target = targetState as? Screen.Folder
            val initial = initialState as? Screen.Folder
            val deeper = (target?.path?.length ?: 0) > (initial?.path?.length ?: 0)

            if (target != null && initial != null && target.path == initial.path) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val sign = if (deeper) 1 else -1
                val slide = tween<IntOffset>(TRANSITION_MS, easing = FastOutSlowInEasing)
                val fade = tween<Float>(TRANSITION_MS)
                (slideInHorizontally(slide) { width -> sign * width } + fadeIn(fade))
                    .togetherWith(
                        slideOutHorizontally(slide) { width -> -sign * width } + fadeOut(fade)
                    )
            }
        }
    ) { screen ->
        when (screen) {
            is Screen.Home -> HomeScreen(onNavigateToFolder = onNavigateToFolder)
            is Screen.Folder -> FolderScreen(
                path = screen.path,
                highlightFile = screen.highlightFile,
                openViewerForId = screen.openViewerForId,
                offerPin = screen.offerPin,
                onNavigateToFolder = onNavigateToFolder
            )
        }
    }
}
