package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Immutable
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip

/** What a paste takes from the copied clip (ME-267): its picture, its sound, or both. */
internal enum class PasteKind(val label: String) {
    Look("Look"),
    Sound("Sound"),
    Both("Look and Sound");

    fun takes(op: ToolOp): Boolean = when (this) {
        Look -> op.toolId != SoundTool.manifest.id
        Sound -> op.toolId == SoundTool.manifest.id
        Both -> true
    }
}

/** The clip menu's `Copy look` and its pastes (ME-267, RD-301): what they do, and which have anything. */
@Immutable
internal class ClipLookActions(
    val onCopy: () -> Unit,
    val pastable: Set<PasteKind>,
    val onPaste: (PasteKind) -> Unit
)

/**
 * The selected clip's copy and paste. **A clipboard, not presets** (`backlog.md` ME-267):
 * nothing is named, stored or listed, and it lasts as long as the sitting. `Copy` takes the
 * clip's own work whole; `Paste` replaces, of the kinds picked, what the target has.
 */
internal fun StripState.lookActions(clip: TrimClip, edits: EditorTimelineScope): ClipLookActions {
    val copied = copiedLook
    return ClipLookActions(
        onCopy = { copiedLook = edits.ownOps(clip.key) },
        pastable = PasteKind.entries.filter { kind -> copied?.any(kind::takes) == true }.toSet(),
        onPaste = { kind -> copied?.let { edits.pasteLayer(clip.key, it, kind::takes) } }
    )
}
