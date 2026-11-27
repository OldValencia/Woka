package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay

/** `toast(message)` without an action (`app.js`): 4.5 s. */
const val TOAST_PLAIN_MS = 4_500L

/** `toast(message, true)`, the one with `Undo`: 7 s. */
const val TOAST_UNDO_MS = 7_000L

/** A finished job's "Saved next to the original." (`startJob`): 6 s. */
const val TOAST_DONE_MS = 6_000L

/** How long after the pointer leaves a paused toast it goes (`#toast` `pointerleave`). */
private const val AFTER_HOVER_MS = 3_000L

/** `.toast`: radius 22, padding 10 × 18, a 16 gap between the text and what follows. */
private val SHAPE = RoundedCornerShape(22.dp)

/** `.toast-progress`: a 120 × 4 track, radius 2, on `--white-14`. */
private val BAR_WIDTH = 120.dp
private val BAR_TRACK = Color(0x24FFFFFF)

/**
 * The prototype's toast (RD-401, `base.css` `.toast`): a dark pill at the bottom centre, a
 * sentence and at most one quiet action — or a bar and `Cancel` while a job runs.
 *
 * **The tip's colours in both themes** (`--e-tip`, `--e-tip-text`): a toast is read over the
 * picture, the room or the library alike, so it carries its own contrast rather than the
 * surface's. Placement is `StatusBanners`', like every other status.
 *
 * [onHover] reports the pointer for [ToastTimer]: hover pauses a toast, as in the prototype.
 */
@Composable
fun Toast(
    tag: String,
    modifier: Modifier = Modifier,
    onHover: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHover(hovered) }
    Row(
        modifier = modifier
            .shadow(12.dp, SHAPE)
            .clip(SHAPE)
            .background(Theme.room.tip)
            .hoverable(interaction)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
fun ToastText(text: String) {
    BasicText(text, style = TextStyle(color = Theme.room.tipText, fontSize = 13.sp))
}

/** `.toast button`: the toast's own colour, underlined — quiet, never a second pill. */
@Composable
fun ToastAction(label: String, tag: String, onClick: () -> Unit) {
    BasicText(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(tag),
        style = TextStyle(color = Theme.room.tipText, fontSize = 13.sp, textDecoration = TextDecoration.Underline)
    )
}

/** `.toast-progress`: the job's fraction in the editor's orange. */
@Composable
fun ToastProgress(fraction: Float) {
    Box(Modifier.width(BAR_WIDTH).height(4.dp).clip(RoundedCornerShape(2.dp)).background(BAR_TRACK)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Theme.room.accent))
    }
}

/**
 * When a toast takes itself away: [lifetimeMs] after it appears, paused while the pointer is
 * on it, and [AFTER_HOVER_MS] once the pointer leaves (`experience.js`).
 *
 * Keyed on [key], so a new message starts a new clock. Returns the hover callback for [Toast].
 */
@Composable
fun rememberToastTimer(key: Any, lifetimeMs: Long, onTimeout: () -> Unit): (Boolean) -> Unit {
    var hovered by remember(key) { mutableStateOf(false) }
    var paused by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, hovered) {
        if (hovered) {
            paused = true
            return@LaunchedEffect
        }
        delay(if (paused) AFTER_HOVER_MS else lifetimeMs)
        onTimeout()
    }
    return { hovered = it }
}
