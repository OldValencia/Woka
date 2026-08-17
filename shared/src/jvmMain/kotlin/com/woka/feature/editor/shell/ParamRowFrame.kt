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
import androidx.compose.ui.text.style.TextAlign
import com.woka.core.ui.theme.Theme

/**
 * The furniture every row shares: a label column of one width, the control, and an
 * optional readout of one width.
 *
 * Shared so the columns line up across rows *by construction*. Each renderer laying
 * out its own label is how an inspector ends up with six labels at five different
 * widths, and the drift is invisible until two tools are compared side by side.
 */

/** The right-hand column, the same width on every row so the numbers line up. */
@Composable
internal fun Readout(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.width(EditorTokens.Inspector.readoutWidth),
        style = TextStyle(
            color = Theme.viewer.onScrim,
            fontSize = EditorTokens.TopBar.subtitleStyle,
            textAlign = TextAlign.End
        )
    )
}

/** Label column, control, and whatever the row puts on the right. */
@Composable
internal fun ParamRowFrame(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = EditorTokens.Inspector.rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            modifier = Modifier.width(EditorTokens.Inspector.labelWidth),
            style = TextStyle(
                color = Theme.viewer.onScrimSecondary,
                fontSize = EditorTokens.TopBar.subtitleStyle
            )
        )
        content()
    }
}
