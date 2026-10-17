package com.woka.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.floatingPanel

/**
 * The dock's destinations, and there is one.
 *
 * `Navigation` — the Library item — went in SIDE-001: it lit up while you stood in a
 * folder, which is not a place, its hover popup listed the pinned folders the sidebar
 * now shows permanently, and its click did nothing. Home goes in SIDE-002.
 *
 * `Editor` was a third until 2026-09-01 and never worked: `App` maps `Screen` to this
 * enum and there is no `Screen.Editor`, so the item could not light, and `onTabSelected`
 * only ever acted on [Home], so it could not be clicked either. It could not be given a
 * job, because the editor is a **mode over a folder** (§4.2) and `FolderScreen` hides
 * this whole dock while one is open — a destination that hides the dock is not a
 * destination. The list of open editors that would have justified it was ME-128, dropped
 * the same day.
 */
enum class DockTab { Home }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingDock(
    activeTab: DockTab?,
    onTabSelected: (DockTab) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
