package com.woka.feature.editor.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ParamSwatchShape
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * A colour as one button: `[● ▾]` for ink, `[▢ ▾]` for a plate (ME-155).
 *
 * **A swatch closed is an honest swatch.** This is the one collapse the two-drawer shape
 * got right and the reason the rest of the bar follows it — a shape in the value's own
 * colour reports that value completely, so nothing is hidden by the press, only reached
 * by it. `Fill` draws `swatchFill`'s diagonal for "no plate", which is why `[▢▾]` needs
 * no second component.
 *
 * **The two are not the same shape**, because Markup's bar carries both side by side and
 * two identical circles is a panel that has to be tried rather than read (reported by
 * hand 2026-08-29). `ColorParam.swatchShape` is the tool's own word for which it is.
 *
 * **And a plate carries a letter.** "It is not clear what you are pressing" was the second
 * report of the same day: a slashed square with a chevron says nothing, and the bar it sits
 * in has no labels to say it for it. An `A` on the plate is what the plate is *for* — the
 * button now draws the thing it configures, which is the rule the rest of the bar already
 * follows. The name is a tooltip on top of that, because a labelless bar has to answer a
 * hover.
 *
 * [folded] is whatever the tool said belongs in this popover rather than beside it —
 * `Corner` inside `Fill`, and nothing else today (`EditorTool.folded`).
 */
@Composable
internal fun ColorCompact(
    spec: ColorParam,
    params: ParamSet,
    writer: ParamWriter,
    folded: List<ParamSpec<*>> = emptyList()
) {
    val selected = params[spec]
    val slot = rememberAccessorySlot("param-${spec.key}")
    val open = slot.open
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(EditorTokens.Motion.QUICK_MS))

    val shape: Shape = when (spec.swatchShape) {
        ParamSwatchShape.Dot -> CircleShape
        ParamSwatchShape.Plate -> RoundedCornerShape(4.dp)
    }

    ViewerTooltip(spec.label) {
        Row(
            modifier = Modifier
                .height(STEPPER_HEIGHT)
                .clip(RoundedCornerShape(STEPPER_RADIUS))
                .background(if (open) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover)
                .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(STEPPER_RADIUS))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    if (open) writer.settle()
                    slot.toggle()
                }
                .resetOnRightClick { writer.resetTo(spec) }
                .padding(horizontal = 6.dp)
                .testTag("param-${spec.key}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                // A hairline, so white on a pale scrim and the "none" slash both have an
                // edge; without it the button reads as an empty hole.
                modifier = Modifier
                    .size(BUTTON_SWATCH)
                    .clip(shape)
                    .background(swatchFill(selected))
                    .border(1.dp, Theme.viewer.onScrimBorder, shape),
                contentAlignment = Alignment.Center
            ) {
                if (spec.swatchShape == ParamSwatchShape.Plate) {
                    BasicText(
                        text = PLATE_SAMPLE,
                        style = TextStyle(
                            color = inkOn(selected),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Image(
                painter = painterResource(Icons.Static.ChevronDown),
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(8.dp).rotate(chevron),
                colorFilter = ColorFilter.tint(Theme.viewer.onScrimSecondary)
            )
        }
    }

    if (open) {
        ColorPalettePanel(spec, params, writer, folded)
    }
}

private val BUTTON_SWATCH = 16.dp
