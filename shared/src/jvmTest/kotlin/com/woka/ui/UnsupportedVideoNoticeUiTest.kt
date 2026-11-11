package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.rememberViewerMedia
import com.woka.feature.viewer.video.PlaybackHealthNotice
import com.woka.feature.viewer.video.UnsupportedVideoNotice
import com.woka.feature.viewer.video.engine.probe.UNSUPPORTED_DOVI_P5
import com.woka.support.onUi
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class UnsupportedVideoNoticeUiTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the specific unsupported notice appears when the player publishes its failure`() = onUi {
        var message by mutableStateOf<String?>(null)
        setContent { UnsupportedVideoNotice(message) }
        onNodeWithText(UNSUPPORTED_DOVI_P5).assertDoesNotExist()
        runOnIdle { message = UNSUPPORTED_DOVI_P5 }
        onNodeWithText(UNSUPPORTED_DOVI_P5).assertIsDisplayed()
    }

    @Test
    fun `the viewer shows its P5 failure even when no poster could be produced`() {
        val sample = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", sample.isFile)
        startKoinOnce()
        onPlayer(sample) { player ->
            awaitTrue("the P5 rejection") { player.videoFailure.message != null }
            onUi {
                setContent {
                    val media = assertIs<ViewerMedia.Video>(rememberViewerMedia(null, player))
                    PlaybackHealthNotice(media.player, 0.dp)
                }
                onNodeWithText(UNSUPPORTED_DOVI_P5).assertIsDisplayed()
            }
        }
    }
}
