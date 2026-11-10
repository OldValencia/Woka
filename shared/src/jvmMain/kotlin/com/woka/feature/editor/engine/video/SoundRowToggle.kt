package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

const val SOUND_ROW_TOGGLE_TAG = "editor-sound-row-toggle"

private val GLYPH = 14.dp

private val TOGGLE_WIDTH = 20.dp

private val TOGGLE_HEIGHT = 26.dp

/** The sound row's switch, right of the readout: the speaker, lit while the row is out. */
@Composable
internal fun SoundRowToggle() {
    val shown = SoundRows.shown
    ViewerTooltip(if (shown) "Hide the sound" else "Show the sound") {
        Box(
            modifier = Modifier
                .size(TOGGLE_WIDTH, TOGGLE_HEIGHT)
                .clip(RoundedCornerShape(EditorTokens.Rail.itemRadius))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { SoundRows.shown = !shown }
                .testTag(SOUND_ROW_TOGGLE_TAG),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Icons.Static.SpeakerWave2Fill),
                contentDescription = if (shown) "Hide the sound" else "Show the sound",
                modifier = Modifier.size(GLYPH),
                colorFilter = ColorFilter.tint(if (shown) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary)
            )
        }
    }
}
