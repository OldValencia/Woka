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
import com.woka.feature.library.AccessoryPill

/**
 * The offer the roadmap's "Direct OS Launch Behavior" asks for: a folder reached by
 * launching `Woka.exe <path>` is not on Home, so once the viewer is closed there is
 * nothing pointing back to it.
 *
 * It takes the dock's top accessory slot rather than a banner over the grid — the
 * dock already hides while the viewer is open and returns exactly when `X` lands
 * the user in Folder View.
 */
@Composable
fun FolderPinPrompt(
    folderName: String,
    onPin: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.floatingPanel(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "Pin \"$folderName\" to Home?",
            style = TextStyle(
                color = Theme.colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        AccessoryPill("Pin", isActive = true, onClick = onPin, isSmall = true)
        AccessoryPill("Not now", onClick = onDismiss, isSmall = true)
    }
}
