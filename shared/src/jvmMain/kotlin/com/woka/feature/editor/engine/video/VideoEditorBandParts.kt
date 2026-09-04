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
import com.woka.feature.viewer.shell.slider.MediaSliderStyle
import com.woka.feature.viewer.video.formatTime

/** `DockItem`'s own target, which is what the transport reads as. */
private val TRANSPORT = 26.dp

/** Wide enough for `1:02:33 / 1:47:10` without the row re-flowing as it counts. */
private val READOUT_WIDTH = 92.dp

/**
 * The three small pieces [VideoEditorBand] is made of, apart because that file reached the
 * size mandate. The seam is the usual one: the band is a *layout and a seek*, and these
 * are what it lays out — the same cut `MediaSlider` and `MediaSliderTrack` already make.
 */

/**
 * White rather than the accent, matching the inspector's sliders: **the accent marks
 * selection, and a track marks level**. `Theme.viewer.onScrim*`, because this sits on a
 * scrim over the picture.
 *
 * **The unplayed track is `onScrimHover`, not `onScrimSecondary`** (2026-09-03). The
 * secondary is a *text* colour — #CDCDD2, near-white — and as a track it drew a solid
 * pale line that the played half was indistinguishable from: only the knob said where
 * the play head was. 8 % white is what the mockup's `--on-scrim-hov` is and what the
 * inspector's own `DetentSlider` track uses; `MediaSliderTrack` lightens it further under
 * the pointer, which is the affordance doing the work instead of the resting state.
 */
@Composable
internal fun bandSliderStyle() = MediaSliderStyle(
    activeColor = Theme.viewer.onScrim,
    inactiveColor = Theme.viewer.onScrimHover,
    thumbColor = Theme.viewer.onScrim
)

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

/** Zero for a file whose duration has not been read yet — never a division by it. */
internal fun fractionOf(position: Long, duration: Long): Float =
    if (duration <= 0L) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
