package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.MediaViewerControlsContainer
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/** Held down, the canvas draws the picture as shot; released, it draws the edit again. */
private const val PEEK_TOOLTIP = "Hold to see the original"

/** The sizes `InfoButton` uses, so the two read as the same kind of control. */
private val PEEK_TARGET = 24.dp
private val PEEK_GLYPH = 16.dp

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
 * The one control in the editor that changes nothing (ME-173).
 *
 * **A press, not a click.** A toggle would need a second press to leave a state the user
 * did not mean to enter, and the gesture people already make when comparing two things is
 * to hold. `tryAwaitRelease` answers a cancel as well as a release — a pointer dragged
 * off the button restores the edit rather than leaving the editor showing an original
 * nobody can get out of.
 *
 * **It is not a tool, and does not sit with them.** No inspector, no parameters, no stage
 * — by §3.4 it is not an `EditorTool`, and putting it in the rail would mean either
 * inventing a fake one or special-casing a list that is derived entirely from the
 * registry. The rail is for things that edit; this one is next to the zoom and the file
 * info, where the other controls that only change *what you see* live.
 *
 * Icon-only, so a tooltip is not decoration here — it is the only place the button says
 * what it does. Its glyph is `paintbrush.slash`, chosen on screen: away from Revert there
 * is nothing destructive nearby for a struck-through brush to be confused with.
 *
 * **It is its own pill, of the same material as the one beside it** (2026-09-02). It was
 * a 34.dp rounded square painted `surface`, which took the *pill's* colour with a
 * *button's* geometry and read as bolted on. `MediaViewerControlsContainer` is the shape
 * every control group in this bar already wears — shadow, 30.dp radius, hairline border —
 * so wearing it too makes this a sibling of the zoom group rather than a stray. Inside, a
 * 24.dp target with a 16.dp glyph, which is what `InfoButton` next door is.
 */
@Composable
internal fun EditorPeekButton(peek: PeekState) {
    val curPeek by rememberUpdatedState(peek)
    val peeking = peek.active
    MediaViewerControlsContainer {
        ViewerTooltip(PEEK_TOOLTIP) {
            Box(
                modifier = Modifier
                    .size(PEEK_TARGET)
                    .clip(CircleShape)
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
                    .testTag("editor-peek-original"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Icons.Static.PeekOriginal),
                    contentDescription = null,
                    modifier = Modifier.size(PEEK_GLYPH),
                    // Held, it lights the editor's accent — the one moment this control
                    // has any state at all worth showing.
                    colorFilter = ColorFilter.tint(
                        if (peeking) Theme.colors.accentEditor else Theme.colors.textPrimary
                    )
                )
            }
        }
    }
}
