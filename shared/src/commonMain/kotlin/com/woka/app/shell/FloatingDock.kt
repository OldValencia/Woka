package com.woka.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.woka.core.ui.components.floatingPanel
import com.woka.feature.library.LibraryPopup

/**
 * The dock's destinations, and there are two.
 *
 * `Editor` was a third until 2026-09-01 and never worked: `App` maps `Screen` to this
 * enum and there is no `Screen.Editor`, so the item could not light, and `onTabSelected`
 * only ever acted on [Home], so it could not be clicked either. It could not be given a
 * job, because the editor is a **mode over a folder** (§4.2) and `FolderScreen` hides
 * this whole dock while one is open — a destination that hides the dock is not a
 * destination. The list of open editors that would have justified it was ME-128, dropped
 * the same day.
 */
enum class DockTab { Home, Navigation }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingDock(
    activeTab: DockTab,
    onTabSelected: (DockTab) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val pinnedSessions = settings.pinnedSessions
    val inlineAccessory = LocalDockAccessories.current.inlineAccessory
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {


        Row(
            modifier = Modifier.floatingPanel(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Before the separator, so the screen's own action reads as a tool and
            // the tabs after it read as navigation.
            if (inlineAccessory != null) {
                inlineAccessory()
                DockSeparator()
            }

            DockItem(
                text = "Home",
                iconRes = Icons.Static.Home,
                isActive = activeTab == DockTab.Home,
                activeColor = Theme.colors.accentHome,
                onClick = { onTabSelected(DockTab.Home) }
            )
            Box {
                val navInteractionSource = remember { MutableInteractionSource() }
                val isNavHovered by navInteractionSource.collectIsHoveredAsState()
                var isPopupHovered by remember { mutableStateOf(false) }
                val showPopup = (isNavHovered || isPopupHovered) && pinnedSessions.isNotEmpty()
                var delayedShowPopup by remember { mutableStateOf(false) }

                LaunchedEffect(showPopup) {
                    if (showPopup) {
                        delayedShowPopup = true
                    } else {
                        delay(150.milliseconds)
                        delayedShowPopup = false
                    }
                }

                DockItem(
                    text = "Library",
                    iconRes = Icons.Static.Folder,
                    isActive = activeTab == DockTab.Navigation,
                    activeColor = Theme.colors.accentFolders,
                    hasBadge = pinnedSessions.isNotEmpty(),
                    interactionSource = navInteractionSource,
                    onClick = { onTabSelected(DockTab.Navigation) }
                )

                if (delayedShowPopup) {
                    LibraryPopup(
                        pinnedSessions = pinnedSessions,
                        settingsRepo = settingsRepo,
                        onHoverEnter = { isPopupHovered = true },
                        onHoverExit = { isPopupHovered = false }
                    )
                }
            }

            DockSeparator()

            DockSecondaryItem(
                iconRes = Icons.Static.Search,
                hoverColor = Theme.colors.accentSearch,
                onClick = onSearchClick
            )
            DockSecondaryItem(
                iconRes = Icons.Static.Settings,
                hoverColor = Theme.colors.textSecondary,
                isWiggle = true,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun DockSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(Theme.colors.border)
    )
}
