package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A card a control opens **beside** the inspector instead of over it (2026-08-28,
 * generalised to every compact control at ME-155).
 *
 * The pad is the case that asked for it: as a `Popup` it landed on top of the very strip
 * it belongs to, because a popup with no anchor is placed at its parent and the parent is
 * the panel. Positioning it by hand would have meant computing coordinates against a card
 * whose width is not fixed — and it still could not have made the inspector *narrow* to
 * make room, which is the behaviour asked for.
 *
 * ME-155's compact controls opened as anchored popups instead, and that reproduced the
 * same complaint from the other direction: a menu hanging over the bar it was opened from
 * covers the button that opened it (reported by hand 2026-08-29). They come through here
 * now, so a value slides out of the card's side rather than landing on it.
 *
 * **A slot rather than a parameter** because the control that fills it is four layers down
 * a deliberately generic API — `ParamRows` renders a schema and must not learn that one of
 * its specs has a panel.
 */
@Stable
internal class InspectorAccessory {

    /**
     * Which control the slot currently belongs to, or null for nobody.
     *
     * **Ownership lives here rather than in each control's own `open` flag.** There is one
     * slot, so two controls holding their own booleans could both believe they were open —
     * the last one composed would take the card while the other's button went on looking
     * pressed. Asking the slot who owns it makes "one at a time" a fact rather than a
     * convention every new control has to remember.
     */
    var owner: String? by mutableStateOf(null)

    /** What to draw beside the card, or null for the usual case of nothing. */
    var content: (@Composable () -> Unit)? by mutableStateOf(null)

    fun close() {
        owner = null
        content = null
    }
}

/**
 * The default holder is a real one rather than null, so a row composed outside an
 * inspector — a test driving `ParamRow` on its own — writes into something inert
 * instead of having to check.
 */
internal val LocalInspectorAccessory = compositionLocalOf { InspectorAccessory() }
