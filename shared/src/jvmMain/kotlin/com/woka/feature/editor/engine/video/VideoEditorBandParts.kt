package com.woka.feature.editor.engine.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.viewer.video.formatTime

/** `DockItem`'s own target, which is what the transport reads as. */
private val TRANSPORT = 26.dp

/** Wide enough for `1:02:33 / 1:47:10` without the row re-flowing as it counts. */
private val READOUT_WIDTH = 92.dp

/**
 * The two small pieces [VideoEditorBand] is made of besides the strip, apart because that
 * file reached the size mandate. The seam is the usual one: the band is a *layout*, and
 * these are what it lays out. The third piece, the slider's style, left with the slider
 * when the strip replaced it (ME-210).
 */

/**
 * **A `Box` that centres, not a sized `BasicText`** (2026-09-03). Given only a `size`, the
 * text lays itself out at the box's top-left and is clipped by it — on screen the glyph
 * sat against the left edge of the band, half of it gone. Every other round control in
 * the app is a `Box` with `Alignment.Center` and its glyph inside; this is that.
 */
@Composable
internal fun Transport(playing: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TRANSPORT)
            .clip(RoundedCornerShape(EditorTokens.Rail.itemRadius))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onToggle)
            .testTag("editor-band-transport"),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = if (playing) "❙❙" else "▶",
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 12.sp)
        )
    }
}

/**
 * [position] stays a lambda for the reason the slider's value does: this text is the one
 * thing in the row that changes every frame, and reading it here keeps the recomposition
 * to itself.
 */
@Composable
internal fun Readout(position: () -> Long, duration: Long) {
    BasicText(
        text = "${formatTime(position())} / ${formatTime(duration)}",
        modifier = Modifier.width(READOUT_WIDTH).testTag("editor-band-readout"),
        style = TextStyle(
            color = Theme.viewer.onScrimSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
