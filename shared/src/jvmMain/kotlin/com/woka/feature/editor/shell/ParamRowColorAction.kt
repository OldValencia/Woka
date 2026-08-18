package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.ParamSet

/**
 * The two rows that are neither a level nor a choice among named options: picking a
 * colour, and performing something. Split from `ParamRowChoice` at 150 lines, along
 * the line that already separated them — an enum picker reads a value, these two set
 * one directly.
 */

/**
 * Swatches, with the accent ring marking the chosen one.
 *
 * The stored value is deliberately **not** restricted to the offered set — the custom
 * popover exists precisely to leave it — so a value that matches no swatch shows no
 * ring rather than snapping to the nearest one.
 */
@Composable
internal fun ColorParamRow(spec: ColorParam, params: ParamSet, writer: ParamWriter) {
    val selected = params[spec]
    ParamRowFrame(spec.label) {
        Row(
            modifier = Modifier.weight(1f).testTag("param-${spec.key}"),
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.swatchGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            spec.swatches.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(EditorTokens.Inspector.swatchSize)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = 2.dp,
                            color = if (argb == selected) Theme.colors.accentEditor else Color.Transparent,
                            shape = CircleShape
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { writer.edit(spec, argb) }
                )
            }
        }
    }
}

/**
 * Icon buttons. Always `Instant` by the spec's own contract, so a press is a commit and
 * two presses are two operations — which is the manifest's `Repeating` cardinality
 * doing the work, not anything here.
 */
@Composable
internal fun ActionParamRow(spec: ActionParam, writer: ParamWriter) {
    // **No label column.** An action's label is written on the button that performs
    // it, so putting it in the row's label column too printed "Rotate left" twice on
    // the same line — and left the button stranded in a 420 dp card with a column of
    // white space between it and everything else.
    BasicText(
        text = spec.label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { writer.edit(spec, true) }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("param-${spec.key}"),
        style = TextStyle(
            color = Theme.viewer.onScrim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
