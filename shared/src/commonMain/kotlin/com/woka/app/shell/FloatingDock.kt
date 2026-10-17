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
 * What is left of the dock after SIDE-002: the screen's inline accessory, Search and
 * Settings. Its destinations are gone — Library in SIDE-001, Home in SIDE-002, both
 * to the sidebar — and SIDE-003 moves the rest into the toolbar and deletes it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingDock(
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
            // Before the separator, so the screen's own action reads apart from the
            // two items that are not navigation at all.
            if (inlineAccessory != null) {
                inlineAccessory()
                DockSeparator()
            }

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
