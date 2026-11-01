package com.woka.app.shell.nowplaying

import androidx.compose.runtime.Composable

/**
 * Hands the Now Playing slot to the OS media session (AP-007): the media keys and the
 * system's media panel follow whatever plays, the viewer open or not. Mounted once, beside
 * the bubble.
 */
@Composable
expect fun NowPlayingSystemSession()
