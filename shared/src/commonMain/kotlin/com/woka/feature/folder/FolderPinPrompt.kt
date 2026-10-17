package com.woka.feature.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.floatingPanel
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.AccessoryPill

/**
 * The offer the roadmap's "Direct OS Launch Behavior" asks for: a folder reached by
 * launching `Woka.exe <path>` is not in the sidebar, so once the viewer is closed there is
 * nothing pointing back to it.
 *
 * A strip at the top of the grid since SIDE-003; it rode on the dock until the dock
 * was deleted. The viewer draws over it, so it still appears exactly when `X` lands
 * the user in Folder View.
 */
@Composable
fun FolderPinPrompt(
    folderName: String,
    onPin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.floatingPanel(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "Link \"$folderName\"?",
            style = TextStyle(
                color = Theme.colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        AccessoryPill("Link", isActive = true, onClick = onPin, isSmall = true)
        AccessoryPill("Not now", onClick = onDismiss, isSmall = true)
    }
}
