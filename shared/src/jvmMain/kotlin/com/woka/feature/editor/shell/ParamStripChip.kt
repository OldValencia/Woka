package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import org.jetbrains.compose.resources.painterResource

/**
 * One adjustment: its name, and what it is currently set to.
 *
 * **The value's colour is the "which of these have I touched" affordance**, and it costs
 * nothing: a parameter still at its detent reads dim, one that has been moved reads
 * bright. Photos marks its adjusted dials the same way, and without it a chooser that
 * shows one control at a time hides every edit but the open one.
 *
 * **The readout has a fixed width, and that is not cosmetic** (reported by hand
 * 2026-09-01). `0`, `+12` and `−100` are three different widths, so a readout that sized
 * itself made every chip to the right of the one being dragged jump on each frame of the
 * drag — with a strip of fifteen, the whole row shuffles under the cursor. It is the same
 * defect `ParamValueStepper` avoids with `VALUE_WIDTH`, and the token for a signed
 * readout already exists.
 */
@Composable
internal fun StripChip(
    spec: FloatParam,
    value: Float,
    selected: Boolean,
    onReset: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(STEPPER_HEIGHT)
            .clip(RoundedCornerShape(STEPPER_RADIUS))
            .background(if (selected) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            // The rule the editor already has everywhere (ME-150), and here it reaches an
            // adjustment **without opening it**: a chip that is not the selected one can
            // be put back where it was, which is the whole reason a chooser needs it.
            .resetOnRightClick(onReset)
            .padding(horizontal = STRIP_CHIP_PADDING)
            .testTag("param-chip-${spec.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STRIP_CHIP_GAP)
    ) {
        val tint = if (selected) Theme.viewer.onScrim else Theme.viewer.onScrimSecondary
        // **The glyph joins the name, it does not replace it** (asked for by hand
        // 2026-09-01, reversing this ticket's own first reading). Icons alone were
        // refused on screen, which is the same answer ME-109 got from the rail: an
        // adjustment is told apart by its name, and a glyph beside it is what makes the
        // strip readable at a glance rather than what makes it shorter.
        if (spec.icon != null) {
            Image(
                painter = painterResource(spec.icon),
                contentDescription = null,
                modifier = Modifier.size(GLYPH_SIZE),
                colorFilter = ColorFilter.tint(tint)
            )
        }
        BasicText(
            text = spec.label,
            style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
        BasicText(
            text = formatFloat(value, spec),
            modifier = Modifier
                .width(EditorTokens.Inspector.readoutWidth)
                .testTag("param-chip-${spec.key}-readout"),
            style = TextStyle(
                color = if (value == spec.detent) Theme.viewer.onScrimDisabled
                else Theme.viewer.onScrim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
        )
    }
}

private val STRIP_CHIP_PADDING = 10.dp

/** The glyph sits inside a 26 dp chip, so it takes the rail's own 16. */
private val GLYPH_SIZE = 16.dp
