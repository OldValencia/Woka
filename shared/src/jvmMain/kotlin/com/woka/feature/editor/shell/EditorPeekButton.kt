package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/** Held down, the canvas draws the picture as shot; released, it draws the edit again. */
private const val PEEK_TOOLTIP = "Hold to see the original"

/**
 * Whether the original is being shown, as one object rather than a flag and a setter
 * threaded separately.
 *
 * The same shape `EditorPlayback` takes and for the same reason: `EditorScreen` is at the
 * size mandate, and three parties read this — the button that sets it, the canvas
 * document that empties, and the overlay that hides.
 */
internal class PeekState {

    var active by mutableStateOf(false)
        private set

    fun set(value: Boolean) {
        active = value
    }
}

/**
 * `Original` — the one control in the editor that changes nothing (ME-173, RD-201).
 *
 * **A press, not a click.** A toggle would need a second press to leave a state the user
 * did not mean to enter, and the gesture people already make when comparing two things is
 * to hold. `tryAwaitRelease` answers a cancel as well as a release — a pointer dragged off
 * the button restores the edit rather than leaving the editor showing an original nobody
 * can get out of.
 *
 * **It is not a tool**, so it is not a tab: no inspector, no parameters, no stage (§3.4).
 * It sits in the header's right group with the other controls that only change *what you
 * see*, icon and word (`#compare` in the prototype); held, it lights in the room's accent.
 */
@Composable
internal fun EditorPeekButton(peek: PeekState) {
    val curPeek by rememberUpdatedState(peek)
    val peeking = peek.active
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    val tint = when {
        peeking -> room.accentText
        hovered -> room.text
        else -> room.muted
    }
    ViewerTooltip(PEEK_TOOLTIP) {
        Row(
            modifier = Modifier
                .height(EditorTokens.TopBar.controlHeight)
                .clip(RoundedCornerShape(EditorTokens.TopBar.controlRadius))
                .background(
                    when {
                        peeking -> room.accentFill
                        hovered -> room.hoverStrong
                        else -> Color.Transparent
                    }
                )
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            curPeek.set(true)
                            tryAwaitRelease()
                            curPeek.set(false)
                        }
                    )
                }
                .padding(horizontal = EditorTokens.TopBar.originalPadding)
                .testTag("editor-peek-original"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.originalGap)
        ) {
            Image(
                painter = painterResource(Icons.Static.PeekOriginal),
                contentDescription = null,
                modifier = Modifier.size(EditorTokens.TopBar.glyphSize),
                colorFilter = ColorFilter.tint(tint)
            )
            BasicText("Original", style = TextStyle(color = tint, fontSize = EditorTokens.TopBar.labelSize))
        }
    }
}
