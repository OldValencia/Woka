package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import com.woka.feature.editor.EditorUndoToast
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.LocalDoneChoices
import com.woka.feature.editor.tools.cover.CoverTool
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.removedNotice
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.join
import com.woka.feature.editor.tools.trim.move
import com.woka.feature.editor.tools.trim.split

/*
 * The three menus' rows (RD-301, `refine.js` `clipMenu` and `menuFor`), apart from the menu's
 * frame in `TimelineMenu`. Every row acts through `StripState` or the timeline scope, so each is
 * one history step and does nothing — pushes nothing — when it would change nothing.
 */

@Composable
internal fun ClipItems(
    items: MenuItems,
    id: String,
    state: StripState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    document: EditDocument
) {
    val index = clips.indexOfFirst { it.id == id }
    val clip = clips.getOrNull(index) ?: return
    val look = state.lookActions(clip, edits)
    items.Item("Split at playhead", enabled = splits(state, clips), key = "S") { state.splitAtPlayHead() }
    items.Separator()
    items.Item("Copy look") { look.onCopy() }
    items.Item("Paste look", enabled = PasteKind.Look in look.pastable) { look.onPaste(PasteKind.Look) }
    items.Item("Paste sound settings", enabled = PasteKind.Sound in look.pastable) { look.onPaste(PasteKind.Sound) }
    items.Separator()
    items.Item("Move earlier", enabled = index > 0) { state.apply(clips.move(id, index - 1)) }
    items.Item("Move later", enabled = index < clips.lastIndex) { state.apply(clips.move(id, index + 1)) }
    items.Item("Duplicate") { state.duplicateSelected(id) }
    items.Item("Join", enabled = clips.join(id) != clips) { state.joinSelected(id) }
    items.Item("Hold this frame for 2 s", enabled = !clip.isFreeze) { state.freezeAtPlayHead() }
    items.Separator()
    SaveFrameItem(items)
    items.Item("Use frame as cover") { useFrameAsCover(state, clips, edits, document) }
    items.Separator()
    items.Item("Delete clip", enabled = clips.size > 1, key = "Del", danger = true) { state.deleteSelected(id) }
}

@Composable
internal fun ShapeItems(items: MenuItems, id: String, edits: EditorTimelineScope, document: EditDocument) {
    items.Item("Remove", key = "Del", danger = true) {
        val op = markupOpIn(document) ?: return@Item
        val gone = op.params[MarkupTool.Shapes].firstOrNull { it.id == id }
        edits.preview(withoutShape(op, id))
        edits.commit()
        gone?.let { EditorUndoToast.offer(it.removedNotice, edits::undo) }
    }
}

@Composable
internal fun EmptyItems(
    items: MenuItems,
    state: StripState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    document: EditDocument
) {
    val onFreeze = clips.getOrNull(state.walk.index)?.isFreeze == true
    items.Item("Split at playhead", enabled = splits(state, clips), key = "S") { state.splitAtPlayHead() }
    items.Item("Hold this frame for 2 s", enabled = !onFreeze) { state.freezeAtPlayHead() }
    items.Separator()
    SaveFrameItem(items)
    items.Item("Use frame as cover") { useFrameAsCover(state, clips, edits, document) }
    items.Separator()
    items.Item("Undo all cuts", enabled = TrimTool.clipsIn(document) != null) { state.resetCut() }
}

/** `Save frame as photo…` (RD-401): the frame under the play head, through its dialog. */
@Composable
private fun SaveFrameItem(items: MenuItems) {
    val save = LocalDoneChoices.current.onSaveFrame ?: return
    items.Item("Save frame as photo…") { save() }
}

/** Whether a split at the play head would cut anything — not on a join, not in a still. */
private fun splits(state: StripState, clips: List<TrimClip>): Boolean =
    clips.split(state.walk.timelineMs(clips, state.player.currentPositionMs), "") != clips

/**
 * `Cover`'s `Use this frame` (ME-287), from the menu — where Cover went when it left the tabs in
 * RD-201. The tool pins the frame to the clip under the play head; the write is the whole film's.
 */
private fun useFrameAsCover(state: StripState, clips: List<TrimClip>, edits: EditorTimelineScope, document: EditDocument) {
    val held = document.opsOf(CoverTool.manifest.id).lastOrNull()?.params ?: ParamSet()
    val playHead = state.walk.timelineMs(clips, state.player.currentPositionMs)
    val next = CoverTool.atPlayHead(CoverTool.Use, held, playHead, document) ?: return
    edits.preview(ToolOp(CoverTool.manifest.id, next))
    edits.commit()
}
