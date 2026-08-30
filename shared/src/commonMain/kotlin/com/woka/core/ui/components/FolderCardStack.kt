package com.woka.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/** How long the sheets take to fan out. The card's own lift runs at the same 200 ms. */
private const val FAN_MS = 200

/** How far each sheet reaches below the tile, at rest and under the cursor. */
private val NEAR_REST = 4.dp
private val NEAR_FANNED = 6.dp
private val FAR_REST = 8.dp
private val FAR_FANNED = 12.dp

/**
 * The mark that says "folder", for a test to find. "Reads as a folder" is not
 * something the semantics tree can see; that the stack is drawn on a folder and on
 * nothing else is.
 */
const val FOLDER_STACK_TAG = "folder-stack"

/**
 * The two sheets of paper under a folder card — what the four-tile mosaic used to say,
 * said by the shape of the card instead. Split out of [FolderCardBody] under the §2
 * file-size mandate when the outline and the fan arrived.
 *
 * Both sheets used to be filled with `surfaceElevated`, the tile's **own** colour, and
 * left unoutlined: in the light theme that was white on `#F2F2F7`, in the dark theme it
 * repeated the tile, and a folder carrying a borrowed cover ended up indistinguishable
 * from a photograph. Their own tokens and a hairline are what the mockup asked for from
 * the start — `ai/mockups/design/folder-card-stack.html`, variant C.
 *
 * Hovering slides both sheets further out while their insets narrow, so the stack opens
 * like a hand of cards. That is "there is more inside" said as a movement, which is why
 * a Home screen made entirely of folders pays nothing for it at rest.
 *
 * **The box reserves [FAR_FANNED] whatever the sheets are doing.** Two sheets in a
 * `Column`, each sized to its own protrusion, is the obvious way to build this and it
 * makes the card taller on hover — which pushes every row below it down, so pointing at
 * one folder visibly shifts the grid. The mockup never showed this because CSS drew the
 * sheets as absolutely positioned pseudo-elements, taking no space in the flow at all.
 * Here the layout is fixed and only the painting moves.
 */
@Composable
internal fun FolderCardStack(isHovered: Boolean) {
    val near by animateDpAsState(if (isHovered) NEAR_FANNED else NEAR_REST, tween(FAN_MS))
    val far by animateDpAsState(if (isHovered) FAR_FANNED else FAR_REST, tween(FAN_MS))
    val nearInset by animateDpAsState(if (isHovered) 6.dp else 8.dp, tween(FAN_MS))
    val farInset by animateDpAsState(if (isHovered) 12.dp else 15.dp, tween(FAN_MS))

    val line = Theme.colors.stackEdgeLine
    val nearFill = Theme.colors.stackEdge
    val farFill = Theme.colors.stackEdgeFar

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FAR_FANNED)
            .testTag(FOLDER_STACK_TAG)
            .drawBehind {
                // Far first, near over it. Near is the wider of the two and opaque, so
                // it hides the far sheet's shoulders and leaves only its bottom band
                // showing — the same two steps the sheets used to draw as two boxes.
                sheet(farInset, far, farFill, line.copy(alpha = line.alpha * 0.7f))
                sheet(nearInset, near, nearFill, line)
            }
    )
}

/**
 * One sheet: a slab with rounded bottom corners, outlined on three sides.
 *
 * The top side is open on purpose. `Modifier.border` strokes all four, and on a sheet
 * 4.dp tall a hairline top *and* bottom leaves 2.dp of fill — the sheet turns into a
 * contour. Filling and stroking the same open [Path] gives both at once: a fill closes
 * across the top implicitly, a stroke does not.
 */
private fun DrawScope.sheet(inset: Dp, protrusion: Dp, fill: Color, line: Color) {
    val stroke = 1.dp.toPx()
    val path = sheetPath(
        left = inset.toPx(),
        right = size.width - inset.toPx(),
        bottom = protrusion.toPx(),
        radius = 9.dp.toPx(),
        half = stroke / 2f
    )
    drawPath(path, fill)
    drawPath(path, line, style = Stroke(stroke))
}

/**
 * Left, bottom and right of the slab, inset by half the stroke so the hairline lands
 * inside the shape rather than straddling its edge. The radius is clamped the way a
 * `RoundedCornerShape` clamps its own — a 9.dp corner cannot exceed a 4.dp sheet.
 */
private fun sheetPath(left: Float, right: Float, bottom: Float, radius: Float, half: Float): Path {
    val l = left + half
    val r = right - half
    val b = bottom - half
    val corner = radius.coerceAtMost(b)
    return Path().apply {
        moveTo(l, 0f)
        lineTo(l, b - corner)
        quadraticTo(l, b, l + corner, b)
        lineTo(r - corner, b)
        quadraticTo(r, b, r, b - corner)
        lineTo(r, 0f)
    }
}
