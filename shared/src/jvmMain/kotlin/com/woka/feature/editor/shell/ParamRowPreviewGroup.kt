package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolOp

/**
 * One group inside a preview strip, and the hairline that separates two of them.
 * Split from [PreviewChoiceStrip] under the size mandate when the line grew its
 * chevrons — that file assembles the line, this one fills it.
 */

/**
 * One spec's options, headed by its own name.
 *
 * The name is **beside** the squares rather than above them: above, it would put the
 * two groups back on two lines, which is the whole thing this layout removes.
 */
@Composable
internal fun <E : Enum<E>> PreviewChoiceGroup(
    spec: PreviewChoiceParam<E>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    val selected = params[spec]
    Row(
        modifier = Modifier.testTag("param-${spec.key}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        if (spec.section != null) {
            BasicText(
                text = spec.section,
                modifier = Modifier
                    .padding(end = SECTION_GAP, top = SECTION_TOP)
                    .testTag("param-${spec.key}-section"),
                style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = SECTION_SIZE)
            )
        }
        spec.options.forEach { option ->
            PreviewChoiceOption(
                spec = spec,
                option = option,
                params = params,
                writer = writer,
                isSelected = option == selected
            ) { modifier ->
                thumbnail(
                    EditDocument(ops = listOf(ToolOp(toolId, params.with(spec, option)))),
                    modifier
                )
            }
        }
    }
}

/** A hairline between two groups, so the boundary survives the label scrolling away. */
@Composable
internal fun GroupDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = DIVIDER_GAP, vertical = DIVIDER_INSET)
            .width(1.dp)
            .height(THUMBNAIL)
            .background(Theme.viewer.onScrimBorder)
    )
}

/** `InspectorFootnote`'s size — the panel's one voice for text that is not a row. */
private val SECTION_SIZE = 12.sp
private val SECTION_GAP = 6.dp
private val SECTION_TOP = 20.dp
private val DIVIDER_GAP = 6.dp
private val DIVIDER_INSET = 4.dp

