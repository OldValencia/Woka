package com.woka.feature.editor.tools.transform

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/**
 * Rotate and flip — the first real tool, and the whole of §3.4's claim in one file.
 *
 * **There is no Compose here and no panel.** Four buttons, a group in the rail, a
 * tooltip, a Settings row and a place in the application order all come from the
 * manifest below; the inspector comes from `params`. If any of that had needed a line
 * of UI code, the architecture would have been wrong rather than the tool.
 *
 * **`Repeating`, not `Single`.** Rotating twice is two operations, which is what makes
 * undo take back one turn instead of all of them. `Single` would collapse them into
 * the last press and make three-quarters of a rotation unreachable.
 *
 * **`Instant`.** There is nothing to drag, so the press *is* the commit.
 */
object TransformTool : EditorTool {

    val RotateLeft = ActionParam("rotateLeft", "Rotate left")
    val RotateRight = ActionParam("rotateRight", "Rotate right")
    val FlipHorizontal = ActionParam("flipHorizontal", "Flip horizontal")
    val FlipVertical = ActionParam("flipVertical", "Flip vertical")

    override val manifest = ToolManifest(
        id = "transform",
        title = "Transform",
        summary = "Rotate and flip",
        // Until a rotate glyph exists; the manifest is the only place to change it.
        icon = Icons.Static.Editor,
        domain = ToolDomain.Geometry,
        order = 0,
        cardinality = ToolCardinality.Repeating,
        interaction = ToolInteraction.Instant
    )

    override val params: List<ParamSpec<*>> =
        listOf(RotateLeft, RotateRight, FlipHorizontal, FlipVertical)

    /**
     * No output size: a turn cannot know the frame, so the fold derives it
     * (`RasterStage.Geometry.outputSize`, ME-105).
     */
    override fun stage(params: ParamSet): ToolStage? =
        matrixFor(params)?.let { RasterStage.Geometry(it) }
}
