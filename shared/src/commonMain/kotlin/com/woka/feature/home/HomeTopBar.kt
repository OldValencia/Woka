package com.woka.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.shell.LocalTopBarAccessories
import com.woka.core.ui.theme.Theme

private const val BAR_KEY = "home"

/**
 * Home's half of the top bar: what screen this is, and how many folders are on it.
 * Sixty pixels in the most prominent part of the window held three Windows buttons
 * and nothing else before this.
 *
 * Two things are deliberately **not** here. Search stays in the dock beside the
 * settings gear, where it already was. Density has no permanent control at all —
 * Ctrl+wheel and the background menu change it, and `GridDensityHud` says what it
 * became, which costs the bar nothing at rest.
 */
@Composable
internal fun HomeTopBarSetup(folderCount: Int) {
    val accessories = LocalTopBarAccessories.current
    // The slot is set once and reads the count live: re-registering the lambda on
    // every change would rebuild the accessory to change one word.
    val count by rememberUpdatedState(folderCount)

    DisposableEffect(accessories) {
        accessories.setLeading(BAR_KEY) { HomeTitle(count) }
        onDispose { accessories.clearLeading(BAR_KEY) }
    }
}

@Composable
private fun HomeTitle(folderCount: Int) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(
            text = "Home",
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp
            )
        )
        BasicText(
            text = linkedFolderCountText(folderCount),
            style = TextStyle(
                color = Theme.colors.textTertiary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

private fun linkedFolderCountText(count: Int) = when (count) {
    0 -> "no linked folders"
    1 -> "1 linked folder"
    else -> "$count linked folders"
}
