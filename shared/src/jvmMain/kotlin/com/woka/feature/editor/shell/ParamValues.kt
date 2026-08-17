package com.woka.feature.editor.shell

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSet

/**
 * What a tool's controls should currently show.
 *
 * Three sources, in one order that is written down exactly once:
 *
 * 1. **the operation in flight**, if it belongs to this tool — a row that read the
 *    committed value while a drag was live would snap the knob back under the user's
 *    finger on every frame;
 * 2. **the tool's last committed operation** — for a `Single` tool that is its only
 *    one, and for a `Repeating` tool the most recent is what its controls describe;
 * 3. **the tool's declared defaults**, so a tool the document has never heard of still
 *    renders a full panel rather than an empty one.
 *
 * `pending` is checked for **ownership**, not merely for existence: while the crop
 * overlay is dragging, the adjustments panel must keep showing adjustment values.
 */
internal fun paramsOf(
    document: EditDocument,
    tool: EditorTool
): ParamSet {
    val id = tool.manifest.id
    document.pending?.takeIf { it.toolId == id }?.let { return it.params }
    return document.opsOf(id).lastOrNull()?.params ?: tool.defaults()
}
