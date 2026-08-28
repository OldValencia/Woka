package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The pad's place in the panel: a small square that **shows** where tone and colour
 * sit, and opens the real one when pressed (asked for 2026-08-28).
 *
 * **`ColorParam` settled this shape once already.** Its swatches sit in the row and
 * its full picker — too large for a row — lives in a popover. A 120 dp pad is the
 * same problem: it was over half the panel's height, on a card drawn across the
 * photograph being edited. Inline it costs 40 dp beside the strip; the pad itself
 * covers the picture only while a hand is on it.
 *
 * The square is an indicator as much as a button. A caption saying "Tone & Colour"
 * would say nothing about whether tone had been moved; the handle says it at a
 * glance, which is what makes one press to reach the control acceptable.
 */
@Composable
internal fun PadParamRow(spec: PadParam, params: ParamSet, writer: ParamWriter) {
    var open by remember { mutableStateOf(false) }
    val point = params[spec]
    val crosshair = Theme.viewer.onScrimBorder
    val outline = if (point == spec.default) crosshair else Theme.colors.accentEditor

    Column(
        modifier = Modifier
            .padding(start = INDICATOR_GAP)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                if (open) writer.settle()
                open = !open
            }
            .testTag("param-${spec.key}-open"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(INDICATOR)
                .clip(RoundedCornerShape(INDICATOR_RADIUS))
                .background(Theme.viewer.onScrimHover)
                .border(1.dp, outline, RoundedCornerShape(INDICATOR_RADIUS))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPadFace(crosshair, spec.handleAt(point, size.minDimension), INDICATOR_HANDLE.toPx())
            }
        }
        BasicText(
            text = spec.label,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(
                color = Theme.viewer.onScrimSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }

    // **Registered into the slot, not drawn here.** Drawn here it would be inside the
    // card, and the card is what has to make room for it (`InspectorAccessory`).
    //
    // Keyed on the value rather than on the writer: a `ParamWriter` is rebuilt every
    // composition, so keying on it would re-register on every frame. What the pad
    // reads that can change is the point, and that is `params`.
    val accessory = LocalInspectorAccessory.current
    if (open) {
        LaunchedEffect(spec, point) {
            accessory.content = { PadAccessory(spec, params, writer) { open = false } }
        }
        DisposableEffect(Unit) {
            onDispose { accessory.content = null }
        }
    }
}

/** The pad as its own card, matching the inspector's frame so the pair reads as one. */
@Composable
private fun PadAccessory(
    spec: PadParam,
    params: ParamSet,
    writer: ParamWriter,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Theme.viewer.cornerRadius))
            .background(Theme.viewer.popoverScrim)
            .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(Theme.viewer.cornerRadius))
            .padding(EditorTokens.Inspector.padding)
            .testTag("param-${spec.key}-panel"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PadCanvas(spec, params, writer)
        BasicText(
            text = "Close",
            modifier = Modifier
                .padding(top = 8.dp)
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

/** Two-thirds of a thumbnail: read at a glance, and never the widest thing in the line. */
private val INDICATOR = 40.dp
private val INDICATOR_RADIUS = 8.dp
private val INDICATOR_HANDLE = 3.dp
private val INDICATOR_GAP = 6.dp
