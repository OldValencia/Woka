package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp

/**
 * What a compact control opens: a card that **slides out of the inspector's side**
 * (ME-155, reshaped by hand 2026-08-29).
 *
 * It was an anchored `Popup` first, and that put the card over the very bar it belongs
 * to — the button pressed to open it ended up underneath it. Through
 * `LocalInspectorAccessory` it is a sibling of the inspector instead, so the panel grows
 * sideways and nothing it came from is covered.
 *
 * **It paints no plate of its own.** The two cards' backgrounds are drawn together, and
 * through one filter, by `GooeyPair` — which is what lets them merge and separate like
 * liquid rather than appear as a second rectangle.
 */
@Composable
internal fun ParamAccessory(
    /** Whose it is: the slot holds one at a time — see `InspectorAccessory.owner`. */
    key: String,
    tag: String,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val accessory = LocalInspectorAccessory.current
    // **The registered lambda calls the latest content, never the one it was built with.**
    // The card is drawn in the panel's composition rather than in this one, so a lambda
    // captured at registration would go on drawing the values the control held when it
    // opened — a swatch chosen inside it would never light. Held this way, the
    // registration happens once and the content still recomposes with its control.
    val current by rememberUpdatedState(content)
    // Registered from an effect, not from composition. Assigning composition state that an
    // ancestor reads is a recomposition loop, and a fresh lambda on every pass never
    // settles — the rule `ParamRowPad` found the hard way when the pad first arrived.
    LaunchedEffect(key, width) {
        accessory.content = {
            Column(
                modifier = Modifier
                    .width(width)
                    .padding(EditorTokens.Inspector.padding)
                    .testTag(tag),
                verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.accessoryGap),
                content = current
            )
        }
    }
    // Ownership released when the control leaves the composition — a tool change, or a row
    // the pen stopped showing — so a panel cannot outlive the button that opened it.
    DisposableEffect(key) {
        onDispose { if (accessory.owner == key) accessory.close() }
    }
}

/**
 * Whether [key] currently holds the slot, and the press that takes or releases it.
 *
 * Every compact control opens the same way, so the toggle is written once: taking the slot
 * from another control closes that one by construction, which is what makes "one open at a
 * time" true without any control knowing about the others.
 */
@Composable
internal fun rememberAccessorySlot(key: String): AccessorySlot {
    val accessory = LocalInspectorAccessory.current
    return remember(accessory, key) { AccessorySlot(accessory, key) }
}

internal class AccessorySlot(
    private val accessory: InspectorAccessory,
    private val key: String
) {
    val open: Boolean get() = accessory.owner == key

    fun toggle() {
        if (open) accessory.close() else accessory.owner = key
    }
}
