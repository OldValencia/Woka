package com.woka.feature.editor.shell

import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool

/**
 * Which canvas tool is mounted when **no** tool is open (ME-149).
 *
 * A drawn mark was only touchable while Markup was the open tool, so nudging an arrow
 * after cropping meant reopening Markup — whose panel then covers the bottom of the
 * picture the arrow is on. With nothing open, the shell mounts the tool that owns
 * something on the picture instead, in select-only mode: select, move, resize, delete,
 * and create nothing.
 *
 * **One, not all.** Two overlays would contest the pointer — a crop handle sits on the
 * frame and an arrow's body may lie under it — which is an objection to mounting a
 * second one, not to the state itself. So the rule is "the first tool in rail order that
 * claims the document", and it lives in this one function so that the second
 * object-carrying tool changes this and nothing else.
 *
 * [tools] arrives already filtered by the registry and already in rail order, which is
 * what makes "first in rail order" cost nothing to say.
 */
internal fun idleCanvasTool(tools: List<EditorTool>, document: EditDocument): CanvasTool? =
    tools.filterIsInstance<CanvasTool>().firstOrNull { it.selectableIn(document) }
