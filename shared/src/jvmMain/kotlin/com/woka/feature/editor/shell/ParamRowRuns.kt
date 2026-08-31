package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import androidx.compose.ui.Modifier

/**
 * How a tool's specs are grouped into what the panel actually lays out, split from
 * [ParamRows] under the size mandate when preview strips learned to share a line.
 *
 * **Grouping is by adjacency, never by sorting.** A tool's declared order is the
 * order its inspector is meant to read in (§3.4), so a run is a stretch of
 * neighbours of the same shape and nothing is moved to join one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParamRun(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit,
    /** Whether this run is a compact line the tool declared (ME-155). */
    compact: Boolean = false,
    folded: (ParamSpec<*>) -> List<ParamSpec<*>> = { emptyList() }
) {
    when {
        // A bar of collapsed controls, no labels — asked for first, because a compact
        // line of *one* spec is legitimate (Markup's five shapes are exactly that) and
        // would otherwise fall through to the rules below and grow a label column.
        compact -> CompactRow(run, params, writer, folded)

        // One scrolling line for however many preview specs sit together — one for
        // every tool but Styles, which has an undertone and a look (ME-141).
        run.first() is PreviewChoiceParam<*> ->
            PreviewChoiceStrip(run, params, writer, toolId, thumbnail)

        // Long enough to be a wall: a chooser and one control instead (ME-161).
        isStrip(run) -> ParamStrip(run, params, writer)

        // Shorter than that, and they stack exactly as they did before ME-161 taught
        // `joins` to gather them. The run exists so the rule above can count it; two
        // sliders are two labelled rows and must not fall into the wrapping group below.
        run.first() is FloatParam ->
            Column(verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)) {
                run.forEach { spec ->
                    key(spec.key) { ParamRow(spec, params, writer, toolId, thumbnail) }
                }
            }

        // Consecutive actions become one wrapping group rather than one row each.
        // Four buttons stacked vertically, each with an empty label column beside it,
        // is what made the Transform panel a tall strip of white space.
        run.size > 1 || run.first() is ActionParam ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                run.forEach { spec ->
                    key(spec.key) { ParamRow(spec, params, writer, toolId, thumbnail) }
                }
            }

        else -> run.first().let { spec ->
            key(spec.key) { ParamRow(spec, params, writer, toolId, thumbnail) }
        }
    }
}
