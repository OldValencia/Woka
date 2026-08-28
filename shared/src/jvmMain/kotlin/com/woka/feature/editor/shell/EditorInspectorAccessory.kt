package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A card a row may open **beside** the inspector instead of over it (2026-08-28).
 *
 * The pad is the case that asked for it: as a `Popup` it landed on top of the very
 * strip it belongs to, because a popup with no anchor is placed at its parent and the
 * parent is the panel. Positioning it by hand would have meant computing coordinates
 * against a card whose width is not fixed — and it still could not have made the
 * inspector *narrow* to make room, which is the behaviour asked for.
 *
 * As a sibling in one `Row` both fall out for free: `weight(1f)` on the inspector is
 * the narrowing, and "to the right" is the order they are written in.
 *
 * **A slot rather than a parameter** because the row that fills it is four layers down
 * a deliberately generic API — `ParamRows` renders a schema and must not learn that
 * one of its specs has a panel. The colour picker is the obvious second customer.
 */
@Stable
internal class InspectorAccessory {
    /** What to draw beside the card, or null for the usual case of nothing. */
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

/**
 * The default holder is a real one rather than null, so a row composed outside an
 * inspector — a test driving `ParamRow` on its own — writes into something inert
 * instead of having to check.
 */
internal val LocalInspectorAccessory = compositionLocalOf { InspectorAccessory() }
