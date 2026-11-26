package com.woka.feature.editor.engine.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.editor.shell.TextAction
import com.woka.feature.editor.shell.Well
import com.woka.feature.editor.shell.WellButton
import com.woka.feature.editor.shell.WellToggle
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.viewer.shell.ViewerTooltip

internal const val CLIP_PANEL_TAG = "editor-clip-panel"
internal const val CLIP_MUTE_TAG = "editor-clip-mute"
internal const val CLIP_SPLIT_TAG = "editor-clip-split"
internal const val CLIP_DELETE_TAG = "editor-clip-delete"
internal const val CLIP_MORE_TAG = "editor-clip-more"

/**
 * The Clip tab (RD-302, `panels.js` `clipPanel`): the selected clip's Speed and `Mute sound` in a
 * well, Split | Delete, and `More actions` — the timeline's clip menu, opened under the button.
 * A still frame has no speed and no sound, so only the buttons.
 *
 * **Drawn by the engine, not the schema**: every row acts on the timeline's clips and play head,
 * which the holder lends while the timeline is on screen (`VideoPlayerHolder.strip`). They are
 * read when a row is used, never kept, so a row cannot write through a list a frame old.
 * Each action is one history step and pushes nothing when it changes nothing.
 */
@Composable
internal fun ClipPanel(holder: VideoPlayerHolder, document: EditDocument, selectedClip: String?) {
    val clips = TrimTool.clipsIn(document) ?: wholeSource(holder.state?.durationMs ?: 0L)
    val clip = clips.firstOrNull { it.id == selectedClip } ?: return
    // The clip's own sound, which `Mute sound` flips — read here, where the document keys it.
    val sound = holder.strip?.edits?.ownOps(clip.key)?.lastOrNull { it.toolId == SoundTool.manifest.id }?.params ?: ParamSet()
    Column(
        modifier = Modifier.fillMaxWidth().testTag(CLIP_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Panel.groupGap)
    ) {
        if (!clip.isFreeze) {
            Well(
                listOf(
                    { ClipSpeedRow(holder, clip) },
                    { WellToggle("Mute sound", Icons.Static.Mute, sound[SoundTool.Mute], CLIP_MUTE_TAG) { holder.muteClip(clip, it) } }
                )
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Always offered, as the prototype's: on a join it cuts nothing and pushes nothing.
            // Asking the play head here would recompose the panel every frame a film plays.
            ViewerTooltip("Split at the playhead (S)", Modifier.weight(1f)) {
                WellButton("Split", Icons.Static.Trim, CLIP_SPLIT_TAG) { holder.strip?.splitAtPlayHead() }
            }
            ViewerTooltip("Delete this clip (Del)", Modifier.weight(1f)) {
                WellButton("Delete", Icons.Static.Delete, CLIP_DELETE_TAG, danger = true, enabled = clips.size > 1) {
                    holder.strip?.deleteSelected(clip.id)
                }
            }
        }
        MoreActions(holder, clip.id, clips, document)
    }
}

/** `More actions` (`.text-action`): the clip menu, its card 4 dp under the line. */
@Composable
private fun MoreActions(holder: VideoPlayerHolder, id: String, clips: List<TrimClip>, document: EditDocument) {
    val menu = remember { TimelineMenuState() }
    var height by remember { mutableStateOf(0) }
    val lift = with(LocalDensity.current) { MENU_LIFT.toPx() }
    Box(Modifier.onSizeChanged { height = it.height }) {
        TextAction("More actions", Icons.Static.More, CLIP_MORE_TAG) {
            menu.at = Offset(0f, height + lift)
            menu.open(MenuTarget.Clip(id))
        }
        val strip = holder.strip
        if (menu.target != null && strip != null) TimelineMenu(menu, strip, clips, strip.edits, document)
    }
}

/**
 * Flips clip [clip]'s own mute as one step, its other sound settings kept — the same op the
 * Sound tab writes with the clip open (ME-240), through the paste the clip menu already uses.
 */
private fun VideoPlayerHolder.muteClip(clip: TrimClip, muted: Boolean) {
    val edits = strip?.edits ?: return
    val own = edits.ownOps(clip.key).lastOrNull { it.toolId == SoundTool.manifest.id }?.params ?: ParamSet()
    val op = ToolOp(SoundTool.manifest.id, own.with(SoundTool.Mute, muted))
    edits.pasteLayer(clip.key, listOf(op)) { it.toolId == SoundTool.manifest.id }
}

private val MENU_LIFT = 4.dp
