package com.woka.feature.editor.tools.hdr

import com.woka.core.data.MediaKind
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage

/** What a save should do with the source's high dynamic range. */
enum class HdrOutput(val label: String) {
    /**
     * Leave the file's colour alone.
     *
     * **This is what already happens**, and finding that out is most of what ME-118
     * turned out to be. Every format Woka lists resolves to `EditCapability.Recorded`,
     * which splices a recipe into the file and never touches the pixels, so an HDR
     * photograph keeps its transfer, its primaries and its PQ codes through an edit.
     */
    Keep("Keep HDR"),

    /**
     * Bake the tone map into the pixels and write an SDR file.
     *
     * **The only irreversible thing in this editor.** Everything else records a recipe
     * that `Revert` removes; this rewrites the photograph and the PQ codes are gone. It
     * exists for other applications — since ME-177 Woka draws these files correctly, so
     * baking buys nothing here and everything in a viewer that mishandles PQ.
     */
    ToSdr("Convert to SDR")
}

/**
 * HDR — the tool that decides what a save *writes*, and draws nothing (ME-118).
 *
 * **`stage()` returns null, always, and that is the design rather than a stub.** Since
 * ME-177 the tone map happens at the decode, so the canvas is already showing SDR
 * whichever option is chosen; there is nothing for a raster stage to add. What the choice
 * changes is the road `writeFor` takes, and it travels there in the document like any
 * other op.
 *
 * `ToolDomain.Output` for the reason Resize is: whatever order the user worked in, this
 * is about the encode and belongs after every geometry and colour stage.
 */
object HdrTool : EditorTool {

    val Output = ChoiceParam(
        key = "output",
        label = "Output",
        options = HdrOutput.entries.toList(),
        default = HdrOutput.Keep,
        labelOf = HdrOutput::label
    )

    override val manifest = ToolManifest(
        id = "hdr",
        title = "HDR",
        summary = "What a save writes",
        icon = Icons.Static.Export,
        domain = ToolDomain.Output,
        // After Resize, which is order 0: a resize is about the picture, this is about
        // the file it becomes.
        order = 1,
        // **The first `supports` override in the app** (ME-264). Every other tool takes
        // the default — both media kinds — and that default is what makes reusing the
        // rail on video free. This one cannot take it: what it decides is *what a save
        // writes*, and for a still that is a pixel operation this tool can perform,
        // while for a video it is a transfer characteristic on an encoded stream. That
        // question belongs to the renderer, in a vocabulary this tool does not speak —
        // and a control that cannot move what it names is worse than no control (the
        // same line ME-180 held for the pill's HDR switch).
        supports = setOf(MediaKind.Image),
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Instant
    )

    override val params: List<ParamSpec<*>> = listOf(Output)

    /** Nothing to draw — see the class KDoc. The choice is read at save, not at fold. */
    override fun stage(params: ParamSet): ToolStage? = null
}

/**
 * Whether [document] asks for the pixels to be baked.
 *
 * Read off the document rather than passed down, because the save is handed an
 * `EditLayers` and nothing else — the same road every other op takes to `writeFor`.
 */
internal fun bakesToSdr(document: EditDocument): Boolean =
    document.ops.any { it.toolId == HdrTool.manifest.id && it.params[HdrTool.Output] == HdrOutput.ToSdr }
