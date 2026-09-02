package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.feature.viewer.shell.SimpleTooltip

/**
 * A dark reset row for the ⚙ popover — dimmed and inert when there is nothing to
 * reset, so the row still says what it would do without pretending it can.
 * [disabledHint] says *why* on hover, since a greyed row explains nothing by itself.
 *
 * (`SyncResetRow` in `PlayerSettingsSync.kt` is the same shape hard-coded to one
 * label; it is left alone because that file sits on the §2 line and folding it in
 * is not part of this change.)
 */
@Composable
internal fun PopoverResetRow(
    label: String,
    enabled: Boolean,
    onReset: () -> Unit,
    disabledHint: String? = null
) {
    if (!enabled && disabledHint != null) {
        SimpleTooltip(disabledHint) { ResetRowBody(label, enabled = false, onReset = onReset) }
    } else {
        ResetRowBody(label, enabled, onReset)
    }
}

@Composable
private fun ResetRowBody(label: String, enabled: Boolean, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) Theme.viewer.onScrimHover else Theme.viewer.onScrimHover.copy(alpha = FADED_PLATE))
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onReset)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) Theme.viewer.onScrimSecondary else Theme.viewer.onScrimDisabled,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * A plate that is present but switched off.
 *
 * **No token describes this**, and inventing one for two callers is what `ViewerChrome`'s
 * own KDoc warns against — `onScrimHover` is the live weight and there is no faded
 * sibling. The alpha is the value the row already drew, kept so a disabled control looks
 * exactly as it did (ME-163).
 */
internal const val FADED_PLATE = 0.03f
