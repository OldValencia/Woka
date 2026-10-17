package com.woka.app.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * `.ib`: the toolbar's square icon button — 30×28, 7.dp radius, a hover fill and no
 * other chrome (`sidebar-redesign.html`). One shape for every icon in the bar, so
 * the sidebar toggle, sort, pin, search and settings read as one row.
 *
 * [active] tints the glyph and fills the button with the accent's soft tone, as the
 * mockup's `.ib.on` does for a toggle that is on.
 */
@Composable
fun ToolbarIconButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    ToolbarButtonFrame(description, onClick, modifier, active) { tint ->
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

/**
 * The sidebar toggle, drawn rather than loaded. The `sidebar_left` SVG is a 30×23
 * frame of hairlines; fitted into 15.dp its strokes land under a pixel and read as
 * unfinished (2026-10-17, reported from the running app). Drawn here at 1.5.dp, the
 * weight `WinControls` already uses for the glyphs beside it.
 */
@Composable
fun ToolbarSidebarButton(description: String, onClick: () -> Unit) {
    ToolbarButtonFrame(description, onClick, Modifier, active = false) { tint ->
        Canvas(Modifier.size(width = 16.dp, height = 13.dp)) {
            val stroke = 1.5.dp.toPx()
            val inset = stroke / 2
            drawRoundRect(
                color = tint,
                topLeft = Offset(inset, inset),
                size = size.copy(width = size.width - stroke, height = size.height - stroke),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(stroke)
            )
            val divider = size.width * 0.36f
            drawLine(tint, Offset(divider, inset), Offset(divider, size.height - inset), stroke)
        }
    }
}

/**
 * Info, drawn for the same reason as the sidebar toggle: the `info_circle` SVG's
 * hairlines broke up at 15.dp and, filled for the active state, read as a ring with
 * a smudge in it (2026-10-17). A 1.5.dp circle, a stem and a dot.
 */
@Composable
fun ToolbarInfoButton(active: Boolean, onClick: () -> Unit) {
    ToolbarButtonFrame("Info  Ctrl+I", onClick, Modifier, active) { tint ->
        Canvas(Modifier.size(15.dp)) {
            val stroke = 1.5.dp.toPx()
            val r = size.minDimension / 2 - stroke / 2
            drawCircle(tint, r, center, style = Stroke(stroke))
            val x = center.x
            drawLine(tint, Offset(x, size.height * 0.45f), Offset(x, size.height * 0.72f), stroke)
            drawCircle(tint, stroke * 0.7f, Offset(x, size.height * 0.3f))
        }
    }
}

@Composable
private fun ToolbarButtonFrame(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    active: Boolean,
    glyph: @Composable (Color) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            active -> Theme.colors.accentSoft
            hovered -> Theme.colors.surfaceHover
            else -> Color.Transparent
        },
        tween(150)
    )
    val tint = when {
        active -> Theme.colors.accent
        hovered -> Theme.colors.textPrimary
        else -> Theme.colors.textSecondary
    }
    Box(
        modifier = modifier
            .size(width = 30.dp, height = 28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        glyph(tint)
    }
}
