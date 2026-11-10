package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * The one affirmative action, so the one filled control.
 *
 * Its label is **dark**. The accent is `#FF9F0A`: white on it measures 2.06 : 1 and
 * near-black 8.28 (ME-102), so a light label here would be the least readable text on
 * the screen rather than the most.
 */
@Composable
internal fun DoneButton(onDone: () -> Unit, choices: DoneChoices = DoneChoices(emptyList(), {})) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .height(EditorTokens.TopBar.controlHeight)
            .clip(RoundedCornerShape(EditorTokens.TopBar.doneRadius))
            .background(Theme.colors.accentEditor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // **A film that cannot keep the edit** (ME-289) gets the render in `Done`'s place —
        // mockup state 9's one-entry menu, as the button itself. [onDone] already renders.
        val label = if (choices.keeps) "Done" else choices.exports.firstOrNull()?.title ?: "Done"
        DoneLabel(label, "editor-done", Modifier.padding(EditorTokens.TopBar.donePadding), onDone)
        // **`Done ▾` only where there is something beside `Done`** (§4.5): a still has no
        // export, and a caret that opened a one-entry menu would be a second way to press
        // the same button.
        if (choices.menu.isNotEmpty()) {
            DoneCaret { menuOpen = true }
            if (menuOpen) DoneMenu(choices, onDone) { menuOpen = false }
        }
    }
}

@Composable
private fun DoneLabel(text: String, tag: String, padding: Modifier, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .then(padding)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color(0xFF1C1C1E),
                fontSize = EditorTokens.TopBar.subtitleStyle,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** The `▾` of `Done ▾`, as the app's own chevron rather than a typed glyph. */
@Composable
private fun DoneCaret(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 2.dp, end = 10.dp)
            .testTag("editor-done-caret"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.ChevronDown),
            contentDescription = "More ways to finish",
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(Color(0xFF1C1C1E))
        )
    }
}
