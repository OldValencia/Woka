package com.woka.feature.editor.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ParamFace
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToggleSetParam
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * A set of faces as one button: `[Aa ▾]`, with the sample drawn in whatever is lit
 * (ME-155, asked for 2026-08-29).
 *
 * **This is the collapse that has to earn itself.** A swatch closed reports a colour
 * completely and a stepper closed reports a number completely; a button labelled "Style"
 * would report nothing, which is exactly why the two-drawer shape was dropped. Drawing
 * the sample in the faces it is setting makes the closed state complete in the same way:
 * bold and underlined *look* bold and underlined, and there is nothing left to open the
 * popover to find out.
 */
@Composable
internal fun <E : Enum<E>> FacesCompact(
    spec: ToggleSetParam<E>,
    params: ParamSet,
    writer: ParamWriter
) {
    val on = params[spec]
    val slot = rememberAccessorySlot("param-${spec.key}")
    val open = slot.open
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(EditorTokens.Motion.QUICK_MS))
    val faces = on.mapNotNull(spec.faceOf).toSet()

    ViewerTooltip(spec.label) {
        Row(
            modifier = Modifier
                .height(STEPPER_HEIGHT)
                .clip(RoundedCornerShape(STEPPER_RADIUS))
                .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(STEPPER_RADIUS))
                .background(if (open) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { slot.toggle() }
                .resetOnRightClick { writer.resetTo(spec) }
                .padding(horizontal = 7.dp)
                .testTag("param-${spec.key}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = spec.sample ?: "${on.size}",
                modifier = Modifier.testTag("param-${spec.key}-sample"),
                style = sampleStyle(faces)
            )
            Image(
                painter = painterResource(Icons.Static.ChevronDown),
                contentDescription = null,
                modifier = Modifier.padding(start = 5.dp).size(8.dp).rotate(chevron),
                colorFilter = ColorFilter.tint(Theme.viewer.onScrimSecondary)
            )
        }
    }

    if (open) {
        ParamAccessory(
            key = "param-${spec.key}",
            tag = "param-${spec.key}-popover",
            // **Two chips to a line** (asked for 2026-08-29): weight and slant on the
            // first, the two lines type can wear on the second. Four in a row made a card
            // wider than the button it hangs off.
            width = (PER_LINE * CHIP + (PER_LINE - 1) * CHIP_GAP + CARD_PADDING).dp
        ) {
            ToggleSetControl(spec, params, writer, Modifier)
        }
    }
}

/**
 * The four schema faces, mapped to the renderer's vocabulary **here and nowhere else**.
 *
 * `Underline` and `Strikethrough` collapse into one `TextDecoration` on the way out, the
 * same way `markupTextStyle` collapses them for the caption itself — two independent
 * chips to the person pressing them, one property to the text layer.
 */
@Composable
private fun sampleStyle(faces: Set<ParamFace>): TextStyle {
    val lines = listOfNotNull(
        TextDecoration.Underline.takeIf { ParamFace.Underline in faces },
        TextDecoration.LineThrough.takeIf { ParamFace.Strikethrough in faces }
    )
    return TextStyle(
        // The accent when anything is on, so the button says "set" from across the panel
        // before the sample itself is read.
        color = if (faces.isEmpty()) Theme.viewer.onScrim else Theme.colors.accentEditor,
        fontSize = 13.sp,
        fontWeight = if (ParamFace.Bold in faces) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (ParamFace.Italic in faces) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (lines.isEmpty()) null else TextDecoration.combine(lines)
    )
}

/** Weight and slant on one line, the two lines type can wear on the next. */
private const val PER_LINE = 2

/** `ParamChipRow`'s own square and the air between two of them. */
private const val CHIP = 26
private const val CHIP_GAP = 6

/** `EditorTokens.Inspector.padding` is 14 a side, and both sides are inside this width. */
private const val CARD_PADDING = 28
