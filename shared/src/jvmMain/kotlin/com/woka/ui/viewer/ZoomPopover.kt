package com.woka.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.data.ZoomPopoverView
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val CONTROL_AREA_WIDTH = 150.dp

/** Frosted popover that slides out below the indicator: `[<] [ Slider | Input ] [>]`. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoomPopover(
    percent: Int,
    view: ZoomPopoverView,
    inputText: String,
    actionLabel: String,
    onAction: () -> Unit,
    onPercent: (Int) -> Unit,
    onView: (ZoomPopoverView) -> Unit,
    onHoverChange: (Boolean) -> Unit
) {
    var dir by remember { mutableStateOf(1) }
    val other = if (view == ZoomPopoverView.Slider) ZoomPopoverView.Input else ZoomPopoverView.Slider

    Column(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.colors.surface.copy(alpha = 0.9f))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .onPointerEvent(PointerEventType.Enter) { onHoverChange(true) }
            .onPointerEvent(PointerEventType.Exit) { onHoverChange(false) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PopoverArrow(Icons.Static.ChevronLeft) { dir = -1; onView(other) }
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    (slideInHorizontally(tween(220)) { w -> dir * w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> -dir * w } + fadeOut(tween(220)))
                }
            ) { current ->
                // Fixed width so switching views never shrinks the popover out from
                // under the cursor (which would drop hover and hide it).
                Box(Modifier.width(CONTROL_AREA_WIDTH), contentAlignment = Alignment.Center) {
                    when (current) {
                        ZoomPopoverView.Slider -> ZoomSliderView(percent, onPercent)
                        ZoomPopoverView.Input -> ZoomInputView(inputText)
                    }
                }
            }
            PopoverArrow(Icons.Static.ChevronRightMac) { dir = 1; onView(other) }
        }

        // The mode action lives here (the "100%" label itself is not clickable):
        // clicking resets the zoom or switches to Preset Mode.
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable { onAction() }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                style = TextStyle(color = Theme.colors.accentFolders, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun PopoverArrow(icon: DrawableResource, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Theme.colors.textPrimary,
            modifier = Modifier.size(11.dp)
        )
    }
}
