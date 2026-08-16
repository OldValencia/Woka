package com.woka.support

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/** The source the fold tests measure against: landscape, so a quarter turn shows. */
val FOLD_SOURCE = IntSize(640, 480)

/**
 * A tool whose stage is fixed at construction, so a fold test varies only the
 * document and the registry — never what a tool decided to produce.
 *
 * `Repeating`, because these tools are stacked in ordering tests and `Single`
 * would silently collapse two operations of the same id into one.
 */
class StageTool(
    id: String,
    domain: ToolDomain,
    private val produced: RasterStage?
) : EditorTool {
    override val manifest = ToolManifest(
        id = id,
        title = id,
        summary = id,
        icon = Icons.Static.Close,
        domain = domain,
        order = 0,
        cardinality = ToolCardinality.Repeating
    )
    override val params: List<ParamSpec<*>> = emptyList()
    override fun stage(params: ParamSet): ToolStage? = produced
}

fun translation(x: Float): Matrix = Matrix().apply { translate(x, 0f) }

/** A colour matrix that is not the identity, in the cheapest possible way. */
fun scaleRed(factor: Float): ColorMatrix = ColorMatrix().apply { values[0] = factor }

fun toolOp(toolId: String) = ToolOp(toolId, ParamSet())

/**
 * A registry over [tools] backed by a real repository on a throwaway storage root,
 * so enablement can be toggled through the same path the app uses.
 *
 * The repository is built inside the block for the reason `onEditorSession`
 * documents: it captures `Dispatchers.Main` when it builds its scope.
 */
fun withFoldRegistry(
    tools: List<EditorTool>,
    block: (EditorToolRegistry, SettingsRepositoryImpl) -> Unit
) = withTempStorageRoot {
    val settings = SettingsRepositoryImpl()
    block(EditorToolRegistry(tools, settings), settings)
}
