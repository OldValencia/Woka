package com.woka.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/** Fixed so the toolbar never re-flows when the label changes length. */
private val INDICATOR_WIDTH = 148.dp

/**
 * The inline size control: `[−]  Fit · 42% ▾  [+]`. The centre is one target with
 * one meaning — open the menu — instead of the old state-dependent click that
 * either reset or switched modes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SizeIndicatorButton(
    label: String,
    menuOpen: Boolean,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    onToggleMenu: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(if (menuOpen) 180f else 0f, tween(200))

    Row(
        modifier = Modifier.width(INDICATOR_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StepButton("−", onStepDown)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (menuOpen || hovered) Theme.colors.textPrimary.copy(alpha = 0.07f)
                    else Color.Transparent
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .clickable { onToggleMenu() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.offset(y = (-1).dp)
            )
            Icon(
                painter = painterResource(Icons.Static.ChevronDown),
                contentDescription = null,
                tint = Theme.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp).size(9.dp).rotate(chevronAngle)
            )
        }
        StepButton("+", onStepUp)
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.offset(y = (-1.5).dp)
        )
    }
}
