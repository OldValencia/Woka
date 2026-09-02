package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * The card the schema draws into.
 *
 * [content] is ME-102's — this owns the header and the frame and knows nothing about
 * which controls appear inside it. That is the split §3.4 depends on: a tool
 * contributes rows, never a panel.
 *
 * The skipped-operations notice is deliberately **not** here; see [EditorSkippedNotice].
 */
@Composable
internal fun EditorInspector(
    title: String,
    /**
     * One line under the rows, or null for the tools that have nothing to add — which
     * is all of them but Resize (ME-110). Supplied by the screen, because the screen is
     * what may name `engine.drawnSize(document)`; a tool never learns the frame.
     */
    footnote: String? = null,
    /**
     * **The ceiling is the caller's** since 2026-08-28: the panel measures the rail and
     * lets a card grow to the dock's width. It is still a *range* for the reason it
     * always was — a fixed 420 dp left Transform's four buttons floating in empty
     * space — so a card sizes to its content and only stops here.
     */
    maxWidth: Dp = EditorTokens.Inspector.maxWidth,
    /**
     * Drawn beside [title], for the actions that are about **this card** rather than
     * about one of its controls — Adjust's Auto and Reset All (ME-174).
     *
     * **Not the Reset that was removed from here on 2026-08-19.** That one cleared the
     * working document — every tool's operations — from a header naming one tool. These
     * write this tool's own parameters and nothing else, which is the difference that
     * makes the position right this time rather than wrong again.
     *
     * Beside the title and not at the card's far edge, because this row is deliberately
     * **not** `fillMaxWidth` (see [InspectorHeader]): pushing them right would need that
     * back, and it is what made every card measure the ceiling.
     */
    actions: @Composable () -> Unit = {},
    /**
     * How much of its own plate this card paints (ME-155).
     *
     * 1 when it stands alone. While a control has a panel open beside it, `GooeyPair`
     * draws **both** plates through one filter so the two merge, and this fades to 0 over
     * the same spring — the same colour and the same corner, so the only thing the
     * crossfade shows is the border going, which is what "these are one object now" looks
     * like.
     */
    plateAlpha: Float = 1f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(min = EditorTokens.Inspector.minWidth, max = maxWidth)
            .clip(RoundedCornerShape(Theme.viewer.cornerRadius))
            .background(Theme.viewer.popoverScrim.copy(alpha = Theme.viewer.popoverScrim.alpha * plateAlpha))
            .border(
                1.dp,
                Theme.viewer.onScrimBorder.copy(alpha = Theme.viewer.onScrimBorder.alpha * plateAlpha),
                RoundedCornerShape(Theme.viewer.cornerRadius)
            )
            .padding(EditorTokens.Inspector.padding)
            .testTag("editor-inspector"),
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        InspectorHeader(title, actions)
        content()
        if (footnote != null) InspectorFootnote(footnote)
    }
}

/** The result of what the rows above are set to, in the picture's own numbers. */
@Composable
private fun InspectorFootnote(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.testTag("editor-inspector-footnote"),
        style = TextStyle(
            color = Theme.viewer.onScrimSecondary,
            fontSize = 12.sp
        )
    )
}

/**
 * The tool's name, and nothing else.
 *
 * **`Reset` was removed here on 2026-08-19.** It cleared the working document — every
 * tool's operations, not the open panel's — from a header that named one tool, and
 * beside an Undo that already takes work back a step at a time. What it was reached
 * for was "put this photograph back as it was", and that is Revert, which now sits
 * beside Back where a destructive control belongs (ME-107).
 */
@Composable
private fun InspectorHeader(title: String, actions: @Composable () -> Unit) {
    Row(
        // **Not `fillMaxWidth`** (measured 2026-08-29). Inside a `widthIn` column that
        // fills the *ceiling*, so every card in the editor measured 420 dp whatever was
        // in it — which is why "the card sizes to its content and only stops at the
        // maximum" was true of the code and false on screen. With the controls collapsed
        // to buttons (ME-155) a 420 dp card around 210 dp of content is most of the
        // panel, and it was the first thing reported.
        modifier = Modifier.padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Spaced, not `SpaceBetween`: with the row sized to its content there is no
        // space to put between them, and the two would sit flush against the name.
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(
            text = title,
            modifier = Modifier.testTag("editor-inspector-title"),
            style = TextStyle(
                color = Theme.viewer.onScrim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        actions()
    }
}
