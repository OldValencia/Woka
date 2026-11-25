package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToggleSetParam

/**
 * One spec, one renderer.
 *
 * The `when` is **exhaustive with no `else`**, so a tenth `ParamSpec` subtype stops
 * this file compiling until it has a row. That is the same guard `EditorFixtureTest`
 * applies from the other side, and between them a spec type cannot reach a document
 * without a way to edit it.
 */
@Composable
internal fun ParamRow(
    spec: ParamSpec<*>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String = "",
    thumbnail: @Composable (EditDocument, Modifier) -> Unit = { _, _ -> }
) {
    when (spec) {
        is FloatParam -> FloatParamRow(spec, params, writer)
        is IntParam -> IntParamRow(spec, params, writer)
        is BoolParam -> BoolParamRow(spec, params, writer)
        is ChoiceParam<*> -> ChoiceParamRow(spec, params, writer)
        is ColorParam -> ColorParamRow(spec, params, writer)
        is ActionParam -> ActionParamRow(spec, writer)

        // The same chips as a choice row above four options, lighting any number of them
        // (ME-147) — a set is what a choice cannot be, not a wider kind of one.
        is ToggleSetParam<*> -> ToggleSetParamRow(spec, params, writer)

        // Two numbers from one gesture (ME-141) — which is why it is one spec.
        is PadParam -> PadParamRow(spec, params, writer)

        // A strip of the picture through each option's own `stage()` (ME-112). The row
        // draws no bitmap itself: it asks the engine, through `thumbnail`, for a picture
        // of a document — which is what keeps the shell free of raster types (§3.3).
        //
        // A run of one: `ParamRun` sends neighbouring preview specs here together, and
        // this branch is what a caller reaching for a single spec gets (ME-141).
        is PreviewChoiceParam<*> ->
            PreviewChoiceStrip(listOf(spec), params, writer, toolId, thumbnail)

        // Draws nothing, by design (§3.4). The value belongs to a `CanvasTool`'s
        // overlay, and a spec that cannot be drawn by a generic row says so in its
        // type rather than arriving at a renderer that then has to guess.
        is ObjectParam<*> -> Unit
    }
}
