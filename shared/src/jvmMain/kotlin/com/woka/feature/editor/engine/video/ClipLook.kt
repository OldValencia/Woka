package com.woka.feature.editor.engine.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.components.MenuSurface
import com.woka.core.ui.theme.Theme
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

/** The clip bar's `Copy` and `Paste ▾` (ME-267): what they do, and which pastes have anything. */
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

/** `Paste ▾` and its menu, opening under the button over the strip. */
@Composable
internal fun PasteAction(look: ClipLookActions) {
    var open by remember { mutableStateOf(false) }
    val drop = with(LocalDensity.current) { PASTE_MENU_DROP.roundToPx() }
    Box {
        BarAction("Paste ▾", Theme.viewer.onScrim, enabled = look.pastable.isNotEmpty(), tag = "editor-clip-paste") {
            open = true
        }
        if (open) {
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, drop), onDismissRequest = { open = false }) {
                MenuSurface(Modifier.testTag("editor-clip-paste-menu")) {
                    PasteKind.entries.forEach { kind ->
                        DropdownItem(kind.label, enabled = kind in look.pastable) {
                            open = false
                            look.onPaste(kind)
                        }
                    }
                }
            }
        }
    }
}

/** The bar's own button height and a little air: the menu opens under `Paste ▾`. */
private val PASTE_MENU_DROP = 30.dp
