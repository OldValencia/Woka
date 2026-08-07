package com.woka.feature.viewer.shell

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
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepository
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.pip.PipCornerFlash
import com.woka.feature.viewer.pip.handlePipKeyEvent
import com.woka.feature.viewer.pip.pipWindowDraggable
import com.woka.feature.viewer.pip.rememberPipWindowMode
import com.woka.feature.viewer.video.FullscreenSystemControlsEffect

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
    val fsZoomHud = remember { ViewerEvent() }
    val onFsSizeModeChange: (SizeMode) -> Unit = { mode ->
        PipManager.sizeMode = mode
        if (fsSettings.rememberSizeState) persistSizeState(settingsRepo, mode)
    }

    Window(
        onCloseRequest = { PipManager.closePip() },
        undecorated = true,
        transparent = true,
        state = windowState,
        // Only while the window is PiP-sized: over one that fills the screen the
        // flag would pin the whole desktop.
        alwaysOnTop = fsSettings.pipAlwaysOnTop && PipManager.isPipWindow,
        title = item.file.name,
        // Preview phase so shortcuts fire before any focused button in the
        // window gets the key (and consume it), matching the main window.
        onPreviewKeyEvent = {
            handlePipKeyEvent(
                event = it,
                videoState = videoState,
                onSizeModeChange = { mode -> onFsSizeModeChange(mode) },
                onZoomHud = { fsZoomHud.raise() }
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

        val pipMode = rememberPipWindowMode(window, windowState, PipManager.nativeSize)

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
                    settingsRepo = settingsRepo,
                    onSizeModeChange = onFsSizeModeChange,
                    onZoomHud = { fsZoomHud.raise() }
                )

                PipCornerFlash()

                ZoomHudOverlay(
                    event = fsZoomHud,
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
                    isPipWindow = pipMode.isPip,
                    onTogglePipWindow = pipMode.toggle,
                    onSizeModeChange = onFsSizeModeChange
                )
            }
        }
    }
}
