package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.components.resetOnRightClick
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.CLIP_SPEED_DETENTS
import com.woka.feature.editor.tools.trim.CLIP_SPEED_MAX
import com.woka.feature.editor.tools.trim.CLIP_SPEED_MIN
import com.woka.feature.editor.tools.trim.snapClipSpeed
import kotlin.math.log2
import kotlin.math.pow

/** Octaves either side of 1×: ¼× is two below, 4× two above. */
private val OCTAVES = log2(CLIP_SPEED_MAX / CLIP_SPEED_MIN)

/** Logarithmic, so 1× sits in the middle and ½× and 2× are the same distance from it. */
private fun fractionOf(speed: Float): Float = log2(speed / CLIP_SPEED_MIN) / OCTAVES

private fun speedAt(fraction: Float): Float = snapClipSpeed(CLIP_SPEED_MIN * 2f.pow(fraction * OCTAVES))

/**
 * The clip-speed slider (ME-216): the app's `DetentSlider`, the viewer's speed slider's own
 * palette, a dot at every sticky speed, and right-click back to 1× — the editor's reset rule.
 */
@Composable
internal fun ClipSpeedSlider(speed: Float, onDrag: (Float) -> Unit, onPick: (Float) -> Unit) {
    var last = speed
    DetentSlider(
        fraction = fractionOf(speed),
        colors = DetentSliderColors(
            track = Theme.viewer.onScrimHover,
            detent = Theme.viewer.onScrimBorder,
            knob = Theme.viewer.onScrim
        ),
        onTap = { onPick(speedAt(it)) },
        modifier = Modifier.resetOnRightClick { onPick(1f) }.testTag("editor-clip-speed-slider"),
        detents = CLIP_SPEED_DETENTS.map(::fractionOf),
        onDragStart = {
            last = speedAt(it)
            onDrag(last)
        },
        onDrag = {
            last = speedAt(it)
            onDrag(last)
        },
        onDragEnd = { onPick(last) }
    )
}
