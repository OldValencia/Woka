package com.woka.feature.editor.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet

/**
 * The pad, beside the card.
 *
 * **It paints no plate of its own** since ME-155: `GooeyPair` draws both cards' plates
 * through one filter so they merge, and a second background here would sit on top of that
 * as a plain rectangle — exactly the seam the filter exists to remove.
 *
 * Split out of `ParamRowPad` when the footer gained its second button, which took that
 * file past its length.
 */
@Composable
internal fun PadAccessory(
    spec: PadParam,
    params: ParamSet,
    writer: ParamWriter,
    onClose: () -> Unit
) {
    ParamAccessory(
        key = "param-${spec.key}",
        tag = "param-${spec.key}-panel",
        width = PANEL_WIDTH
    ) {
        PadCanvas(spec, params, writer)
        PadFooter(spec, params, writer, onClose)
    }
}

/**
 * `Reset` and `Close`, on one line under the pad (asked for 2026-08-29).
 *
 * The written-out twin of the right-click the indicator answers — the same pairing every
 * value popover carries, so the pad is not the one control whose default can be reached
 * only by knowing about a gesture.
 */
@Composable
private fun ColumnScope.PadFooter(
    spec: PadParam,
    params: ParamSet,
    writer: ParamWriter,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.spacedBy(FOOTER_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResetButton(
            enabled = params[spec] != spec.default,
            tag = "param-${spec.key}-reset"
        ) {
            writer.resetTo(spec)
        }
        BasicText(
            text = "Close",
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    onClose()
                    // Settled once on close, so a drag across the pad — however many
                    // events it took — is one history entry. The same rule
                    // `ColorPickerPopover` applies to sliding through fifty colours.
                    writer.settle()
                }
                .testTag("param-${spec.key}-close"),
            style = TextStyle(
                color = Theme.viewer.onScrimSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** The pad is a 120 dp square, plus the card's own padding either side of it. */
private val PANEL_WIDTH = 148.dp
private val FOOTER_GAP = 4.dp
