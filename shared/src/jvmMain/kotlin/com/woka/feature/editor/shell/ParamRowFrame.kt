package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
internal fun Readout(text: String, tag: String? = null) {
    BasicText(
        text = text,
        modifier = Modifier
            .width(EditorTokens.Inspector.readoutWidth)
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
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
        RowLabel(label, Modifier.width(EditorTokens.Inspector.labelWidth))
        content()
    }
}

/**
 * Two controls on one line, each with its own name (ME-153).
 *
 * **The left label keeps the shared column** so a paired line still lines up with every
 * plain row above and below it; the right one is inline and only as wide as its word,
 * because a second full column would push the pair off the card it was meant to shorten.
 *
 * [left] takes the room that is going spare and [right] sizes to its content — which is
 * what puts the buttons first and the slider after them, the order the pair was asked for
 * (2026-08-29): chips measure what they measure, and a slider is the thing that can use
 * whatever is left.
 */
@Composable
internal fun PairedRowFrame(
    leftLabel: String,
    rightLabel: String,
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.heightIn(min = EditorTokens.Inspector.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.pairGap)
    ) {
        RowLabel(leftLabel, Modifier.width(EditorTokens.Inspector.labelWidth))
        left()
        RowLabel(rightLabel, Modifier)
        right()
    }
}

/** The label's own typography, shared by both frames so a pair cannot drift from a row. */
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
