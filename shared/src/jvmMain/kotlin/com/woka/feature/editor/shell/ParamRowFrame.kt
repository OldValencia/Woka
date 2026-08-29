package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.woka.core.ui.theme.Theme

/**
 * The furniture every labelled row shares: a label column of one width, then the control.
 *
 * Shared so the columns line up across rows *by construction*. Each renderer laying
 * out its own label is how an inspector ends up with six labels at five different
 * widths, and the drift is invisible until two tools are compared side by side.
 *
 * **The readout column is gone** (ME-155). It existed for the inline slider, and every
 * level now reports its own value inside `ParamValueStepper`'s frame — a second copy in
 * a column beside it would be the same number twice.
 *
 * **`PairedRowFrame` is gone with it.** Two controls on one line was ME-153's answer to a
 * tall panel and was refused on screen the same week: buttons beside a slider does not
 * work. What replaced it is a line with no labels at all — `ParamRowCompact`.
 */

/** Label column, control, and whatever the row puts on the right. */
@Composable
internal fun ParamRowFrame(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = EditorTokens.Inspector.rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(label, Modifier.width(EditorTokens.Inspector.labelWidth))
        content()
    }
}

/** The label's own typography, in one place so no row can drift from another. */
@Composable
private fun RowLabel(text: String, modifier: Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = Theme.viewer.onScrimSecondary,
            fontSize = EditorTokens.TopBar.subtitleStyle
        )
    )
}
