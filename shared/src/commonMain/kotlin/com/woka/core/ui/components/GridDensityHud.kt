package com.woka.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.ViewLayout
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * The density readout, in the middle of the grid and only while it is changing.
 *
 * This is what replaced the S/M/L control in the top bar. A segmented control sits
 * there permanently to answer a question asked once in a session; a HUD costs
 * nothing at rest and, unlike the control, can say what the change *was* — you see
 * which rung you left as well as the one you landed on.
 */
private const val HOLD_MS = 900L

@Composable
fun GridDensityHud(layout: ViewLayout, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(layout) }
    var change by remember { mutableStateOf<DensityChange?>(null) }

    // Keyed on the layout, so a second step *cancels* the running hold and starts a
    // fresh one — scrolling three rungs in a row must not leave the readout to
    // vanish on the first one's timer.
    LaunchedEffect(layout) {
        if (layout == previous) return@LaunchedEffect
        change = DensityChange(previous, layout)
        previous = layout
        delay(HOLD_MS)
        change = null
    }

    // Held past the null so the exit animation still has something to draw — the
    // same reason the dock's accessories keep their last value.
    var lastShown by remember { mutableStateOf<DensityChange?>(null) }
    change?.let { lastShown = it }

    AnimatedVisibility(
        visible = change != null,
        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.92f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f),
        modifier = modifier
    ) {
        lastShown?.let { DensityCard(it) }
    }
}

private data class DensityChange(val from: ViewLayout, val to: ViewLayout)

@Composable
private fun DensityCard(change: DensityChange) {
    Column(
        modifier = Modifier
            .shadow(28.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.4f))
            .background(Theme.colors.surfaceElevated, RoundedCornerShape(16.dp))
            .border(1.dp, Theme.colors.borderGlow, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The rung being left, still legible but plainly the past tense.
            BasicText(
                text = change.from.densityLabel,
                style = TextStyle(
                    color = Theme.colors.textTertiary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            BasicText(
                text = "→",
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 16.sp)
            )
            BasicText(
                text = change.to.densityLabel,
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            )
        }
        BasicText(
            text = change.to.densityName,
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
