package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolOp
import kotlinx.coroutines.launch

/**
 * Every consecutive preview spec, laid out as **one scrolling line** (ME-141, reshaped
 * by hand 2026-08-28).
 *
 * Two strips stacked cost the inspector ~190 dp and covered 40 % of the photograph
 * being edited; in one line they cost ~95, and the panel a person edits *through* stops
 * being the thing they cannot see past. The price is a longer scroll — fourteen squares
 * instead of eight — and it is the trade this panel is worth making.
 *
 * **The shell never draws a picture.** It asks [thumbnail] for a picture of a document
 * holding only that option's operation, and the engine answers (§3.3). One document per
 * option, or every square would show the edit as a whole and look like the canvas.
 */
@Composable
internal fun PreviewChoiceStrip(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    val groups = run.filterIsInstance<PreviewChoiceParam<*>>()
    // The pad rides at the end of the line rather than on a row of its own (ME-141,
    // reshaped 2026-08-28) — see `ParamRun`, which is what puts it in this run.
    val pad = run.filterIsInstance<PadParam>().firstOrNull()

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { (THUMBNAIL + EditorTokens.Inspector.rowGap).toPx() }

    Row(
        modifier = Modifier.testTag("param-strip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StripScrollButton(back = true, enabled = scroll.canScrollBackward) {
            scope.launch { scroll.animateScrollBy(-step * NOTCHES_PER_PRESS) }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                .wheelScrollsStrip(scroll, scope, step),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
        ) {
            groups.forEachIndexed { index, spec ->
                if (index > 0) GroupDivider()
                PreviewChoiceGroup(spec, params, writer, toolId, thumbnail)
            }
        }
        StripScrollButton(back = false, enabled = scroll.canScrollForward) {
            scope.launch { scroll.animateScrollBy(step * NOTCHES_PER_PRESS) }
        }
        if (pad != null) PadParamRow(pad, params, writer)
    }
}

/** One press of a chevron travels two squares, which is about a third of the view. */
private const val NOTCHES_PER_PRESS = 2f
