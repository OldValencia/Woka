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
 */
@Composable
fun TransientOverlay(
    event: ViewerEvent,
    durationMs: Long,
    enter: EnterTransition,
    exit: ExitTransition,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = rememberEventFlag(event, durationMs),
        enter = enter,
        exit = exit,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = alignment) {
            content()
        }
    }
}
