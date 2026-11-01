package com.woka.app.shell.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import kotlin.math.roundToInt

/** `.ghost`'s 300 ms (`now-playing.html` `fly()`). */
private const val FLIGHT_MS = 300

/** [from] at 0, [to] at 1: the ghost's rect at [t] of the way. */
internal fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
    lerp(from.left, to.left, t),
    lerp(from.top, to.top, t),
    lerp(from.right, to.right, t),
    lerp(from.bottom, to.bottom, t)
)

/**
 * The cover in transit between the sidebar row and the bubble (AP-008). Rects are in
 * root coordinates; [toBubble] says which way, and so which end is round.
 */
@Stable
internal class CoverFlight {
    val progress = Animatable(0f)
    var active by mutableStateOf(false)
    var from: Rect = Rect.Zero
    var to: Rect = Rect.Zero
    var toBubble = true

    /** True from a flight into the bubble landing until the bubble is shown: it arrives without a fade. */
    var landed by mutableStateOf(false)
}

/**
 * Runs a flight each time the sidebar is toggled — never on first composition — while
 * [eligible]. The row end is the last row position seen fully on screen: the sidebar
 * hides by sliding its content left under a clip, so a cover left of zero is a row on
 * its way out, and the one kept is where it rests when the sidebar comes back.
 */
@Composable
internal fun rememberCoverFlight(
    sidebarVisible: Boolean,
    eligible: Boolean,
    disc: () -> Rect
): CoverFlight {
    val flight = remember { CoverFlight() }
    val nowPlaying = remember { AppDependencies.nowPlaying }
    var rested by remember { mutableStateOf<Rect?>(null) }
    nowPlaying.rowCover?.let { if (it.left >= 0f) rested = it }
    var previous by remember { mutableStateOf(sidebarVisible) }
    LaunchedEffect(sidebarVisible) {
        val toggled = previous != sidebarVisible
        previous = sidebarVisible
        val row = rested
        if (!toggled || !eligible || row == null) return@LaunchedEffect
        flight.toBubble = !sidebarVisible
        flight.from = if (flight.toBubble) row else disc()
        flight.to = if (flight.toBubble) disc() else row
        flight.active = true
        // `finally`: a second toggle cancels this flight, and an `active` left true
        // would keep the bubble hidden for good.
        try {
            flight.progress.snapTo(0f)
            flight.progress.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
            flight.landed = flight.toBubble
        } finally {
            flight.active = false
        }
    }
    return flight
}

/** The ghost itself, drawn in the bubble's layer: root rects moved by [layerOrigin]. */
@Composable
internal fun FlightGhost(flight: CoverFlight, source: NowPlayingSource, layerOrigin: Offset) {
    if (!flight.active) return
    val t = flight.progress.value
    val rect = lerpRect(flight.from, flight.to, t)
    val density = LocalDensity.current
    val side = with(density) { rect.width.toDp() }
    val round = if (flight.toBubble) t else 1f - t
    val radius = lerp(NowPlayingTokens.CoverRadius, NowPlayingTokens.BubbleCover / 2, round)
    val at = IntOffset(
        (rect.left - layerOrigin.x).roundToInt(),
        (rect.top - layerOrigin.y).roundToInt()
    )
    NowPlayingCover(source, side.coerceAtLeast(1.dp), RoundedCornerShape(radius), Modifier.offset { at })
}
