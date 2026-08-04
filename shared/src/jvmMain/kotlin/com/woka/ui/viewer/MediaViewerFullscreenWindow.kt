package com.woka.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import com.woka.data.MediaItem
import com.woka.data.SettingsRepository
import com.woka.ui.viewer.pip.PipManager
import com.woka.ui.viewer.player.VideoPlayerState
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerFullscreenWindow(
    item: MediaItem,
    videoState: VideoPlayerState?,
    settingsRepo: SettingsRepository
) {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

    // Declared outside the window content: the key preview runs as a Window
    // parameter, so anything it touches has to exist before the content lambda.
    val fsSettings by settingsRepo.settings.collectAsState()
    var fsZoomHud by remember { mutableStateOf(0L) }
    val onFsSizeModeChange: (SizeMode) -> Unit = { mode ->
        PipManager.sizeMode = mode
        if (fsSettings.rememberSizeState) persistSizeState(settingsRepo, mode)
    }

    Window(
        onCloseRequest = { PipManager.closePip() },
        undecorated = true,
        transparent = true,
        state = windowState,
        title = item.file.name,
        // Preview phase so shortcuts fire before any focused button in the
        // window gets the key (and consume it), matching the main window.
        onPreviewKeyEvent = {
            handlePipKeyEvent(
                event = it,
                videoState = videoState,
                onSizeModeChange = { mode -> onFsSizeModeChange(mode) },
                onZoomHud = { fsZoomHud = System.currentTimeMillis() }
            )
        }
    ) {
        var appear by remember { mutableStateOf(false) }
        // Grab OS focus when the window opens; it is created while the main
        // window is active, so without this it never receives key events
        // (Space, arrows, Escape).
        LaunchedEffect(Unit) {
            appear = true
            window.toFront()
            window.requestFocus()
        }

        FullscreenSystemControlsEffect(item, videoState, fsSettings.systemMediaControls)

        var isUiVisible by remember { mutableStateOf(true) }
        var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(lastInteractionTime, videoState?.isPlaying) {
            if (videoState?.isPlaying == false) {
                isUiVisible = true
            } else {
                delay(2500.milliseconds)
                isUiVisible = false
            }
        }

        AnimatedVisibility(
            visible = appear,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.95f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.95f)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .onPointerEvent(PointerEventType.Move) {
                        lastInteractionTime = System.currentTimeMillis()
                        isUiVisible = true
                    }
            ) {

                val fsBottomOverlayPadding = if (isUiVisible) 90.dp else 0.dp
                val fsSubtitleBottomPadding = if (isUiVisible) 72.dp else 40.dp

                MediaViewerFullscreenSurface(
                    modifier = Modifier.fillMaxSize().pipWindowDraggable(window),
                    videoState = videoState,
                    subtitleBottomPadding = fsSubtitleBottomPadding,
                    onSizeModeChange = onFsSizeModeChange,
                    onZoomHud = { fsZoomHud = System.currentTimeMillis() }
                )

                PipCornerFlash()

                ZoomHudOverlay(
                    trigger = fsZoomHud,
                    label = "${PipManager.sizeMode.percentIn(
                        PipManager.containerSize,
                        PipManager.nativeSize
                    )}%"
                )

                val hover = remember { PopoverHoverState() }
                PopoverHoverEffect(hover)

                MediaViewerFullscreenOverlays(
                    videoState = videoState,
                    hover = hover,
                    isUiVisible = isUiVisible,
                    bottomPadding = fsBottomOverlayPadding,
                    settingsRepo = settingsRepo,
                    onExitFullscreen = { PipManager.closePip(returnToMain = true) }
                )

                MediaViewerFullscreenTopControls(
                    isUiVisible = isUiVisible,
                    onSizeModeChange = onFsSizeModeChange
                )
            }
        }
    }
}
