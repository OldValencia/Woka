package com.woka.feature.viewer.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A HUD that appears when [event] is raised and fades itself out after
 * [durationMs]. Play/pause, skip and the zoom readout were three copies of this
 * preamble, differing only in duration, animation and what they drew.
 *
 * [enabled] swallows the raise instead of drawing it, for the one HUD that answers a
 * request the current file cannot serve — Space and a click both mean play/pause and
 * both reach a photograph, which has nothing to play. Gated **here**, not by skipping
 * the call: a conditional composable is one Compose cannot key, and the flag inside
 * has to keep counting either way.
 */
@Composable
fun TransientOverlay(
    event: ViewerEvent,
    durationMs: Long,
    enter: EnterTransition,
    exit: ExitTransition,
    alignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = rememberEventFlag(event, durationMs) && enabled,
        enter = enter,
        exit = exit,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = alignment) {
            content()
        }
    }
}
