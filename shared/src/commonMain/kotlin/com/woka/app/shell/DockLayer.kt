package com.woka.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The floating dock and the accessories a screen hangs around it, over the bottom of
 * the content.
 *
 * Split out of `MainShell` (SIDE-001) so the shell had room for the sidebar under the
 * size mandate. Behaviour is unchanged; the whole layer is deleted by SIDE-003, when
 * the toolbar takes over everything the dock carries.
 */
@Composable
fun BoxScope.DockLayer(
    dockAccessories: DockAccessoriesState,
    activeTab: DockTab?,
    onTabSelected: (DockTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!dockAccessories.isVisible) return

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
                        Theme.colors.background.copy(alpha = 0.9f)
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
            AnimatedAccessory(dockAccessories.topAccessory)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedAccessory(dockAccessories.leftAccessory)
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

@Composable
private fun AnimatedAccessory(accessory: (@Composable () -> Unit)?) {
    var savedAccessory by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    if (accessory != null) savedAccessory = accessory

    AnimatedVisibility(
        visible = accessory != null,
        enter = slideInVertically(
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 2 }
        ) + fadeIn(animationSpec = tween(150)),
        exit = slideOutVertically(
            animationSpec = tween(250, easing = FastOutLinearInEasing),
            targetOffsetY = { it / 2 }
        ) + fadeOut(animationSpec = tween(250))
    ) {
        savedAccessory?.invoke()
    }
}
