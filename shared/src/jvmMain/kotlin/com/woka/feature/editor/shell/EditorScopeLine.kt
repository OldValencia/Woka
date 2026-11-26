package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames

internal const val FRAME_SCOPE_TAG = "editor-frame-scope"

internal const val SCOPE_NOTE_TAG = "editor-scope-note"

private const val ALL_FRAMES = "All frames"

/** `.inspector .scope`: 40 dp tall at least, its 12 dp top padding included. */
private val LINE_MIN_HEIGHT = 40.dp

/**
 * Who the edit is for, above the inspector's tabs (RD-204, `#editor/frames`, `#editor/video`).
 *
 * ME-156 drew this as a pill over the picture's top edge; the inspector is where the edit is
 * made, so the line that says whom it is for stands at its head. A frame set gets the switch,
 * a film the crumbs ([FilmScopeCrumbs]) — a film's selected clip is its scope (ME-221).
 *
 * It computes nothing: `EditorHost` owns the choice, `EditorScreen` fills the clip, this
 * draws them.
 */
@Composable
internal fun EditorScopeLine(controls: EditorFrameControls, onWholeVideo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LINE_MIN_HEIGHT)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp)
            .testTag(FRAME_SCOPE_TAG),
        verticalArrangement = Arrangement.Center
    ) {
        if (controls.frames.clips) {
            FilmScopeCrumbs(controls.clip, controls.shape, onWholeVideo)
            return@Column
        }
        val single = "Frame ${controls.frameIndex + 1}"
        RoomSegmented(
            options = listOf(ALL_FRAMES, single),
            selected = if (controls.scope == EditScope.SingleFrame) single else ALL_FRAMES,
            onSelect = { chosen ->
                controls.onScopeChange(if (chosen == ALL_FRAMES) EditScope.AllFrames else EditScope.SingleFrame)
            }
        )
        lossNoteOf(controls.frames, controls.scope, controls.frameIndex)?.let { note ->
            BasicText(
                text = note,
                modifier = Modifier.padding(start = 2.dp, top = 7.dp).testTag(SCOPE_NOTE_TAG),
                style = TextStyle(color = Theme.room.muted, fontSize = 11.sp)
            )
        }
    }
}

/**
 * What `All frames` is about to throw away, said only when it would (ME-126; the author,
 * 2026-11-26): it clears every per-frame override, or "every frame draws this" would be
 * false wherever a key wins. With nothing to lose the line is not drawn — the switch says
 * the rest.
 *
 * The open frame is left out of the count: its own edit is what this very save replaces, so
 * naming it would report the user's own document as a loss.
 */
internal fun lossNoteOf(frames: EditorFrames, scope: EditScope, frameIndex: Int): String? {
    if (scope == EditScope.SingleFrame) return null
    return when (val replaced = frames.overridden.count { it != frameIndex }) {
        0 -> null
        1 -> "Replaces the separate edit on 1 frame"
        else -> "Replaces the separate edits on $replaced frames"
    }
}
