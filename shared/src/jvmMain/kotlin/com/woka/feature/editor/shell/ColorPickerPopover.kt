package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.woka.core.ui.theme.Theme

private val PANEL_WIDTH = 208.dp

/**
 * A custom colour, picked by hand: saturation and brightness on a pad, hue on a strip,
 * and a readout of what is currently chosen (ME-114, asked for 2026-08-24).
 *
 * **The hue, saturation and brightness are this panel's own state, not derived from the
 * stored colour each frame.** That was the first version's bug and it was a bad one: black,
 * white and every grey have *no* hue — all three channels are equal, so the conversion has
 * nothing to return — so dragging into the pad's dark or pale corners threw the hue away,
 * `hsvOf` reported 0°, and the whole panel snapped back to red. A colour cannot carry the
 * three numbers a picker is arranged in, which is exactly why the picker has to hold them.
 *
 * Seeded from [argb] each time the popover opens — it is composed only while open — so
 * reopening it lands on whatever the swatch actually holds.
 *
 * `focusable = true`, for the reason `SizeControl` records: a non-focusable popup lets
 * scene pointer input through and the hover state underneath blinks.
 */
@Composable
internal fun ColorPickerPopover(
    argb: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val opened = remember { argb }
    var hsv by remember(opened) { mutableStateOf(hsvOf(opened)) }

    val write = { next: Hsv ->
        hsv = next
        onPick(packHsv(next.hue, next.saturation, next.value))
    }

    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .background(Theme.viewer.popoverScrim, RoundedCornerShape(12.dp))
                .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .testTag("color-picker"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PickedColorReadout(argb)
            SaturationValuePad(hsv) { s, v -> write(hsv.copy(saturation = s, value = v)) }
            HueStrip(hsv) { hue -> write(hsv.copy(hue = hue)) }
        }
    }
}

/**
 * What is chosen right now: the colour itself, and its hex.
 *
 * Asked for by hand — without it the panel showed a marker on a gradient and no statement
 * of the result, so a colour picked against a busy photograph could not be read back. The
 * hex is there because it is the one form of a colour that can be written down.
 */
@Composable
private fun PickedColorReadout(argb: Long) {
    Row(
        modifier = Modifier.testTag("color-picked"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorSwatch(fill = solidFill(argb), ringed = false)
        BasicText(
            text = hexOf(argb),
            style = TextStyle(
                color = Theme.viewer.onScrim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
