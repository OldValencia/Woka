package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.focusClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.wholeSource

/** Where a step of the help sheet leads: a tool's tab, the film's first clip, or `Create a copy`. */
internal sealed interface HelpStep {
    data class Tool(val toolId: String) : HelpStep
    data object Clip : HelpStep
    data object Copy : HelpStep
}

private class HelpCard(val title: String, val line: String, val step: HelpStep)

/** `experience.js` `helpDialog`, a film's — without the music step until 1.1.0 (the author, 2026-11-26). */
private val VIDEO_STEPS = listOf(
    HelpCard(
        "1. Keep the moments you want",
        "Point at the timeline to preview a moment. Click the scissors on the playhead or press S to split. " +
            "Select a part you don't need and press Delete. Drag clips to change their order.",
        HelpStep.Clip
    ),
    HelpCard(
        "2. Keep the edit or make a finished copy",
        "Done keeps your edit in Woka. Create a copy makes a file other apps can open.",
        HelpStep.Copy
    )
)

private val PHOTO_STEPS = listOf(
    HelpCard("1. Try a style", "Point at a style to preview it on your photo, click to keep it.", HelpStep.Tool("filter")),
    HelpCard(
        "2. Fine-tune light and colour",
        "Try Auto first. Sliders stick at zero, and a changed value can be reset with one click.",
        HelpStep.Tool("adjust")
    ),
    HelpCard("3. Frame the picture", "Choose a shape, drag the edges, level the horizon with Straighten.", HelpStep.Tool("crop")),
    HelpCard(
        "4. Compare, then finish",
        "Click Original, or hold it or \\, to see the photo before your edits. Done keeps your edit; Create a copy makes a separate file.",
        HelpStep.Copy
    )
)

/**
 * The `?` sheet (RD-401): a few numbered steps, each a card that opens what it names. The
 * film's last line says where the scope is shown — "the line above the tabs", since the app
 * has no "Editing" label (the author, 2026-11-27).
 */
@Composable
internal fun EditorHelpSheet(video: Boolean, onStep: (HelpStep) -> Unit, onDismiss: () -> Unit) {
    EditorDialogFrame(
        title = if (video) "A short edit, step by step" else "A quick photo edit",
        tag = "editor-help",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EditorDialogTokens.Help.gap)) {
            (if (video) VIDEO_STEPS else PHOTO_STEPS).forEachIndexed { index, card ->
                HelpOption(card, "editor-help-step-${index + 1}") { onStep(card.step) }
            }
            if (video) {
                EditorDialogText(
                    "Right-click a clip for more actions. The line above the tabs shows whether you're " +
                        "changing one clip or the whole video.",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** `.help-option`: a well card, its title, and a muted line under it. */
@Composable
private fun HelpOption(card: HelpCard, tag: String, onClick: () -> Unit) {
    val tokens = EditorDialogTokens.Help
    val room = Theme.room
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radius))
            .background(if (hovered) room.well2 else room.well)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(tokens.padding)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BasicText(card.title, style = TextStyle(color = room.text, fontSize = tokens.titleSize, fontWeight = FontWeight.SemiBold))
        BasicText(card.line, style = TextStyle(color = room.muted, fontSize = tokens.lineSize, lineHeight = tokens.lineHeight))
    }
}

/**
 * Where [step] leads (`experience.js` `help-*`): its tool's tab; for a film's first step the
 * first clip, selected as a click would, and its Clip tab; or `Create a copy`.
 */
internal fun EditorTabState.follow(step: HelpStep, session: EditorSession, onCopy: () -> Unit) {
    val toolId = when (step) {
        is HelpStep.Tool -> step.toolId
        HelpStep.Copy -> return onCopy()
        HelpStep.Clip -> {
            if (session.clipSelection.value == null) {
                val first = TrimTool.clipsIn(session.document.value)?.firstOrNull() ?: wholeSource(0L).first()
                session.focusClip(first.id, first.key)
            }
            TrimTool.manifest.id
        }
    }
    tabFor(tabs, tab, toolId, null)?.let { choose(it.id) }
}
