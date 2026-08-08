package com.woka.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.woka.core.data.CarouselVisibility
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo
import com.woka.core.data.SettingsRepository
import com.woka.feature.viewer.shell.MediaViewerAmbientEffects
import com.woka.feature.viewer.shell.MediaViewerPlayer
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.ViewerActions
import com.woka.feature.viewer.shell.ViewerChromeState
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.feature.viewer.shell.mediaViewerPlayerModifier
import java.nio.file.Files

/** The tag the viewer's focusable player box carries **in tests only**. */
const val VIEWER = "viewer-player"

/**
 * A viewer over stills, on files that exist but are never decoded.
 *
 * [MediaViewerPlayer] is the level the UI layer works at rather than
 * `MediaViewerScreen`: the screen resolves `AppDependencies`, `PipManager` and the
 * shell's `LocalGlobalShortcuts`, while the player takes every one of its inputs
 * as a parameter — including `thumbnails`, which is what keeps `ImageLoader`
 * and FFmpeg out of these tests entirely.
 */
@OptIn(ExperimentalTestApi::class)
fun onViewer(count: Int, block: ComposeUiTest.(List<MediaItem>) -> Unit) = withTempStorageRoot {
    val dir = Files.createTempDirectory("woka-viewer-ui").toFile()
    try {
        val items = (1..count).map { MediaItem.Image(dir.media("still$it.jpg")) }
        runComposeUiTest { block(items) }
    } finally {
        dir.deleteRecursively()
    }
}

/** A blank poster: the shell reads its dimensions and draws it, nothing more. */
fun poster(width: Int = 800, height: Int = 600): ImageBitmap = ImageBitmap(width, height)

/**
 * The player wired the way `MediaViewerScreen` wires it, minus the video half.
 *
 * The tag goes on through `MediaViewerPlayer`'s own `modifier` parameter, so it is
 * this file's tag and not a production one; it exists because the player box is
 * the element that holds focus, and key input needs a node to anchor to.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewerUnderTest(
    state: MediaViewerState,
    items: List<MediaItem>,
    media: ViewerMedia,
    carousel: CarouselVisibility = CarouselVisibility.HIDDEN,
    ambientRepo: SettingsRepository? = null,
    // Before `onClose`, which callers pass as a trailing lambda.
    strip: FrameStripModel = FrameStripModel(),
    onClose: (String) -> Unit = {}
) {
    val focus = remember { FocusRequester() }
    var showInfo by remember { mutableStateOf(false) }
    // Opt-in, and off by default: the ambient effects carry the 2.5 s auto-hide
    // timer, which would put every other scenario's chrome away mid-test.
    if (ambientRepo != null) {
        MediaViewerAmbientEffects(state, null, 0.5f, ambientRepo, focus)
    }
    val onSizeModeChange: (SizeMode) -> Unit = { state.sizeMode = it }
    val infoAction = { showInfo = true }
    val playerModifier = Modifier.mediaViewerPlayerModifier(
        state = state,
        videoState = null,
        mediaItems = items,
        mediaVolume = 0.5f,
        isFullscreen = false,
        focusRequester = focus,
        onClose = onClose,
        copyAction = {},
        infoAction = infoAction,
        onSizeModeChange = onSizeModeChange,
        onVolumeChange = {}
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
    MediaViewerPlayer(
        modifier = playerModifier.testTag(VIEWER),
        state = state,
        media = media,
        mediaItems = items,
        activeItem = items.find { it.file.absolutePath == state.currentId },
        mediaVolume = 0.5f,
        chrome = ViewerChromeState(carousel, isFullscreen = false),
        actions = ViewerActions(onEdit = {}, onCopy = {}, onInfo = infoAction),
        showInfoDialog = showInfo,
        onShowInfoDialogChange = { showInfo = it },
        onVolumeChange = {},
        onSizeModeChange = onSizeModeChange,
        onToggleFullscreen = {},
        thumbnails = FakeViewerThumbnails,
        strip = strip
    )
}

/** Both sizes are the same painted poster: no test here is about the difference. */
private object FakeViewerThumbnails : ViewerThumbnails {
    override suspend fun full(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun preview(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun sourceSize(item: MediaItem) = IntSize.Zero
    override suspend fun stillColor(item: MediaItem) = StillColorInfo.Sdr
    override fun cachedFull(item: MediaItem, frameIndex: Int) = null
}
