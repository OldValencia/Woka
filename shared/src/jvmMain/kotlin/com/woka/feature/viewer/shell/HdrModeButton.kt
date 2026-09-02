package com.woka.feature.viewer.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/** Fixed, so the pill does not re-flow as the label changes length. */
private val ANCHOR_WIDTH = 96.dp

/**
 * The anchor, built to match [SizeIndicatorButton]'s centre exactly — same height,
 * radius, hover tint, type weight and chevron.
 *
 * Copied deliberately rather than extracted: the size control's centre is wrapped by two
 * step buttons and owns a fixed outer width, so the shared part would be a parameter bag
 * around six lines of styling. If a third pill control appears, that is when it earns a
 * component.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HdrModeButton(label: String, menuOpen: Boolean, onToggleMenu: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(if (menuOpen) 180f else 0f, tween(200))

    Row(
        modifier = Modifier
            .width(ANCHOR_WIDTH)
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
            .padding(horizontal = 6.dp)
            .testTag("hdr-mode-anchor"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(bottom = 1.dp)
        )
        Image(
            painter = painterResource(Icons.Static.ChevronDown),
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(9.dp).rotate(chevronAngle),
            colorFilter = ColorFilter.tint(Theme.colors.textSecondary)
        )
    }
}
