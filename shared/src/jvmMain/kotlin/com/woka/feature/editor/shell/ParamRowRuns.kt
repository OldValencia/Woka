package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.PadParam
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
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    when {
        run.first() is PreviewChoiceParam<*> ->
            StylePagers(run, params, writer, toolId, thumbnail)

        // Straighten's angle is a dial, not a scrub row (RD-205); its writes are a level's.
        (run.first() as? FloatParam)?.dial == true ->
            writer.levelsOf(run.first(), params).forEach { ParamDial(it) }

        // Levels side by side are one well of scrub rows, split by section where the tool
        // declares sections (RD-202; Adjust's Light, Colour and Detail).
        run.first() is FloatParam || run.first() is PadParam -> ParamStrip(run, params, writer)

        // Consecutive actions are well buttons two to a row (RD-203) — Transform's rotate
        // and flip pairs; a lone action is one full-width button.
        run.first() is ActionParam -> ActionGrid(run.filterIsInstance<ActionParam>(), writer)

        run.size > 1 ->
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
