package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlDefaults
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames

/** Wide enough for two labels and the longest consequence line under them. */
private val STRIP_WIDTH = 232.dp

/** Clear of the shell's top bar, which the editor draws into rather than under. */
private val STRIP_TOP = 16.dp

private val LABEL_GAP = 6.dp

/** A footnote, not a label — the same 11.5 sp the inspector's secondary text takes. */
private val FOOTNOTE_SIZE = 11.5.sp

internal const val FRAME_SCOPE_TAG = "editor-frame-scope"

private const val ALL_FRAMES = "All Frames"
private const val SINGLE_FRAME = "Single Frame"

/**
 * Who the edit is for, for a file holding more than one picture (ME-156).
 *
 * Its own strip, centred under the top bar (decided 2026-08-30). The two placements
 * that were not chosen: **in the top bar**, which already carries Cancel, the title,
 * Revert, HDR, undo, redo and Done and where this would be the seventh thing in a row
 * of six; and **at the head of a frame strip**, which reads well but would have made
 * this ticket depend on ME-158.
 *
 * It computes nothing and reads nothing global: `EditorHost` owns the choice, the
 * counts come from the payload the engine already read when it opened, and this draws
 * them.
 */
@Composable
internal fun EditorFrameScopeStrip(
    frames: EditorFrames,
    scope: EditScope,
    frameIndex: Int,
    onScopeChange: (EditScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(top = STRIP_TOP)
            .width(STRIP_WIDTH)
            .testTag(FRAME_SCOPE_TAG),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SegmentedControl(
            options = listOf(ALL_FRAMES, SINGLE_FRAME),
            selected = if (scope == EditScope.SingleFrame) SINGLE_FRAME else ALL_FRAMES,
            onSelect = { chosen ->
                onScopeChange(
                    if (chosen == SINGLE_FRAME) EditScope.SingleFrame else EditScope.AllFrames
                )
            },
            colors = SegmentedControlDefaults.onScrim
        )
        Spacer(Modifier.height(LABEL_GAP))
        BasicText(
            text = consequenceOf(frames, scope, frameIndex),
            style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = FOOTNOTE_SIZE)
        )
    }
}

/**
 * What the click is about to do, said before it happens.
 *
 * `All Frames` **clears every per-frame override** (ME-126) — it has to, or "every
 * frame draws this" would be false wherever a key wins — and that is destructive of
 * work the user may have done frames ago. A control that throws work away silently is
 * the thing this line exists to stop.
 *
 * The open frame is left out of the count: its own edit is what is being replaced by
 * this very save, so naming it would report the user's own document as a loss.
 */
private fun consequenceOf(frames: EditorFrames, scope: EditScope, frameIndex: Int): String {
    if (scope == EditScope.SingleFrame) return "Applies to frame ${frameIndex + 1} only"
    val replaced = frames.overridden.count { it != frameIndex }
    return when (replaced) {
        0 -> "Applies to all ${frames.count} frames"
        1 -> "Replaces the separate edit on 1 frame"
        else -> "Replaces the separate edits on $replaced frames"
    }
}
