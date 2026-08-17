package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam

/**
 * A tool's whole panel, from its schema — the composable form of §3.4's claim that a
 * tool writes no UI code.
 *
 * Every row for every tool in the app comes through here, which is why the tools
 * cannot drift apart in spacing, typography or commit behaviour: there is one
 * implementation and it is this one.
 */
@Composable
internal fun ParamRows(
    tool: EditorTool,
    session: EditorSession,
    document: EditDocument
) {
    val params = paramsOf(document, tool)
    val writer = ParamWriter(session, tool, document)
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        tool.params.forEach { spec -> ParamRow(spec, params, writer) }
    }
}

/**
 * One spec, one renderer.
 *
 * The `when` is **exhaustive with no `else`**, so a ninth `ParamSpec` subtype stops
 * this file compiling until it has a row. That is the same guard `EditorFixtureTest`
 * applies from the other side, and between them a spec type cannot reach a document
 * without a way to edit it.
 */
@Composable
internal fun ParamRow(spec: ParamSpec<*>, params: ParamSet, writer: ParamWriter) {
    when (spec) {
        is FloatParam -> FloatParamRow(spec, params, writer)
        is IntParam -> IntParamRow(spec, params, writer)
        is BoolParam -> BoolParamRow(spec, params, writer)
        is ChoiceParam<*> -> ChoiceParamRow(spec, params, writer)
        is ColorParam -> ColorParamRow(spec, params, writer)
        is ActionParam -> ActionParamRow(spec, writer)

        // Deferred to ME-112, which is the only ticket that has anything to preview:
        // this row draws each option through that option's own `stage()`, so built
        // now it would render a strip of identical thumbnails and prove nothing.
        is PreviewChoiceParam<*> -> Unit

        // Draws nothing, by design (§3.4). The value belongs to a `CanvasTool`'s
        // overlay, and a spec that cannot be drawn by a generic row says so in its
        // type rather than arriving at a renderer that then has to guess.
        is ObjectParam<*> -> Unit
    }
}
