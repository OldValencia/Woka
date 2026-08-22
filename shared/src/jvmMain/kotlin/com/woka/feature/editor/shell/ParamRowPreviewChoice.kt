package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolOp

/**
 * A strip of options, each drawn **through its own `stage()`** (ME-112).
 *
 * This is the row ME-102 deferred, with the reason written into `ParamRows`: before
 * filters existed it would have rendered a line of identical thumbnails. What makes it
 * possible without the shell learning what a bitmap is, is that it never draws one — it
 * asks [thumbnail] for a picture of a document, and the engine answers (§3.3).
 *
 * **One document per option, holding one operation.** A thumbnail must show *that look*
 * and not the edit as a whole, or every option in the strip would look the same as the
 * canvas behind it.
 */
@Composable
internal fun <E : Enum<E>> PreviewChoiceRow(
    spec: PreviewChoiceParam<E>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    val selected = params[spec]
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .testTag("param-${spec.key}"),
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        spec.options.forEach { option ->
            PreviewChoiceOption(
                label = spec.labelOf(option),
                isSelected = option == selected,
                onSelect = { writer.edit(spec, option) },
                tag = "param-${spec.key}-${option.name}"
            ) { modifier ->
                thumbnail(
                    EditDocument(ops = listOf(ToolOp(toolId, params.with(spec, option)))),
                    modifier
                )
            }
        }
    }
}

@Composable
private fun PreviewChoiceOption(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    tag: String,
    content: @Composable (Modifier) -> Unit
) {
    val outline = if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimBorder
    Column(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onSelect() }
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content(
            Modifier
                .size(THUMBNAIL)
                .clip(RoundedCornerShape(THUMBNAIL_RADIUS))
                .background(Theme.viewer.onScrimHover)
                .border(1.dp, outline, RoundedCornerShape(THUMBNAIL_RADIUS))
        )
        BasicText(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(
                color = if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** Large enough to read a look off, small enough that eight fit a panel. */
private val THUMBNAIL = 56.dp
private val THUMBNAIL_RADIUS = 8.dp
