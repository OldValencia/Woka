package com.woka.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.FrameExportStatus
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long a finished message stays up before it takes itself away. */
private const val SETTLE_MS = 2600L

/**
 * What an export is doing, wherever the user happens to be.
 *
 * One banner in the shell rather than one per screen: an export started from
 * the grid keeps running when the viewer opens over it, and a 96-picture
 * container takes twelve seconds — long enough that silence reads as the app
 * having hung.
 *
 * A pill at the bottom, out of the way of both the dock and the viewer's
 * controls, in the same surface-and-border language as every other floating
 * panel.
 */
@Composable
fun FrameExportBanner() {
    val message = FrameExportStatus.message
    // Only a finished message clears itself; a running one stays until the next
    // update replaces it, however long that takes.
    LaunchedEffect(message) {
        if (message != null && !message.startsWith("Exporting")) {
            delay(SETTLE_MS.milliseconds)
            FrameExportStatus.clear()
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    style = TextStyle(
                        color = Theme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
