package com.woka.core.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A menu row that opens a second menu beside it on hover.
 *
 * Hover on the row **or** on the flyout keeps it up, and closing waits
 * [CLOSE_GRACE] — the same coordination the dock's Library popup uses, and for the
 * same reason: the cursor has to cross a gap of empty window between the two, and
 * during that crossing neither is hovered.
 *
 * The flyout is placed from the row's measured width rather than from the menu
 * constant, so it still lands correctly beside a row the surface decided to draw
 * narrower.
 */
private val CLOSE_GRACE = 150.milliseconds

@Composable
fun DropdownSubmenu(
    text: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val rowSource = remember { MutableInteractionSource() }
    val flyoutSource = remember { MutableInteractionSource() }
    val rowHovered by rowSource.collectIsHoveredAsState()
    val flyoutHovered by flyoutSource.collectIsHoveredAsState()

    var rowWidthPx by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    val wanted = rowHovered || flyoutHovered
    LaunchedEffect(wanted) {
        if (wanted) {
            visible = true
        } else {
            delay(CLOSE_GRACE)
            visible = false
        }
    }

    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    // Back out the surface's own padding so the flyout's first item sits on the
    // same line as the row that opened it.
    val liftPx = with(LocalDensity.current) { 6.dp.roundToPx() }

    Box(modifier = Modifier.onSizeChanged { rowWidthPx = it.width }) {
        DropdownItem(
            text = text,
            interactionSource = rowSource,
            trailing = {
                BasicText(
                    text = "›",
                    style = TextStyle(color = Theme.colors.textTertiary, fontSize = 14.sp)
                )
            },
            onClick = { }
        )

        if (visible) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(rowWidthPx + gapPx, -liftPx)
            ) {
                Box(modifier = Modifier.hoverable(flyoutSource)) {
                    MenuSurface(content = content)
                }
            }
        }
    }
}
