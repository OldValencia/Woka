package com.woka.feature.editor.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolOp

/** How many tiles a row shows at once (the author, 2026-11-25). */
internal const val PAGE = 4

/**
 * Every consecutive preview spec as its own row of tiles (RD-203, `#editor/photo` → Styles):
 * the group's name, then four tiles between ← and →.
 *
 * It replaces the one scrolling line of 56 dp squares (ME-141), which in the 294 dp docked
 * inspector showed two previews at a time. Four per row, a page per arrow press, and the
 * row the selection is on is the one shown when the panel opens.
 *
 * **The shell never draws a picture.** Each tile asks [thumbnail] for a picture of a document
 * holding only that option's operation, and the engine answers (§3.3).
 */
@Composable
internal fun StylePagers(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    Column(
        modifier = Modifier.testTag("param-strip"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        run.filterIsInstance<PreviewChoiceParam<*>>().forEach { spec ->
            key(spec.key) { StylePager(spec, params, writer, toolId, thumbnail) }
        }
    }
}

@Composable
private fun <E : Enum<E>> StylePager(
    spec: PreviewChoiceParam<E>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    val options = spec.options
    val last = (options.size - PAGE).coerceAtLeast(0)
    var first by remember(spec.key) {
        mutableStateOf((options.indexOf(params[spec]) / PAGE * PAGE).coerceAtMost(last))
    }
    Column(modifier = Modifier.testTag("param-${spec.key}")) {
        spec.section?.let { name ->
            BasicText(
                text = name,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).testTag("param-${spec.key}-section"),
                style = TextStyle(color = Theme.room.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PagerArrow(back = true, enabled = first > 0, tag = "param-${spec.key}-back") {
                first = (first - PAGE).coerceAtLeast(0)
            }
            // The page slides over in the direction of the arrow pressed (the author, 2026-11-25).
            AnimatedContent(
                targetState = first,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val way = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * way } togetherWith slideOutHorizontally { -it * way })
                        .using(SizeTransform(clip = true))
                }
            ) { page ->
                Row {
                    options.drop(page).take(PAGE).forEach { option ->
                        key(option.name) {
                            StyleTile(spec, option, params, writer, Modifier.weight(1f)) { modifier ->
                                thumbnail(EditDocument(ops = listOf(ToolOp(toolId, params.with(spec, option)))), modifier)
                            }
                        }
                    }
                    // A short last page keeps its tiles the width of a full one.
                    repeat(PAGE - options.drop(page).take(PAGE).size) { Spacer(Modifier.weight(1f)) }
                }
            }
            PagerArrow(back = false, enabled = first < last, tag = "param-${spec.key}-next") {
                first = (first + PAGE).coerceAtMost(last)
            }
        }
    }
}
