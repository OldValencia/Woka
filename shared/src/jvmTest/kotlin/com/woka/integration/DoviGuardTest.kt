package com.woka.integration

import androidx.compose.ui.unit.IntSize
import com.woka.app.AppDependencies
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.video.render.renderVideo
import com.woka.feature.editor.engine.video.render.saveFramePhoto
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.preview.hoverExcerpts
import com.woka.feature.viewer.video.engine.probe.UnsupportedVideoException
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DoviGuardTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `P5 never returns its first image or a seek image`() {
        val grabber = FreshFrameGrabber(p5()).apply {
            imageWidth = 400
            imageHeight = 225
            setVideoOption("threads", "2")
        }
        try {
            grabber.start()
            val first = assertFailsWith<UnsupportedVideoException> { grabber.grabImage() }
            assertTrue(first.message.orEmpty().contains("Profile 5"))
            assertFailsWith<UnsupportedVideoException> { grabber.grabImageAt(1_000_000L) }
        } finally {
            runCatching { grabber.stop() }
            grabber.release()
        }
    }

    @Test
    fun `P5 produces neither a cached poster nor a hover excerpt`() = withTempStorageRoot {
        val file = p5()
        assertNull(VideoThumbnailExtractor.load(MediaItem.Video(file)))
        assertFalse(WokaStorage.thumbnailFile(file).exists())
        runBlocking {
            val excerpts = withTimeout(10_000L) { hoverExcerpts(file).toList() }
            assertTrue(excerpts.isEmpty())
        }
    }

    @Test
    fun `the player publishes an unsupported failure rather than damaged file or false colours`() {
        onPlayer(p5()) { player ->
            awaitTrue("the specific P5 failure", timeoutMs = 15_000L) { player.videoFailure.message != null }
            assertTrue(player.videoFailure.message.orEmpty().contains("Profile 5"))
            assertFalse(player.openFailed)
            assertFalse(player.isPlaying)
            assertNull(player.currentFrame)
            awaitTrue("the rejected decoder to finish") { player.grabberJob?.isCompleted == true }
            awaitTrue("the rejected audio decoder to finish") { player.audioDecodeJob?.isActive != true }
        }
    }

    @Test
    fun `P5 video and frame photo exports fail before writing a result`() = withTempStorageRoot { root ->
        val file = p5().copyTo(File(root, "p5.mp4"))
        val registry = EditorToolRegistry(emptyList(), AppDependencies.settingsRepository)
        val size = IntSize(1920, 1080)
        val video = renderVideo(file, EditDocument(), { emptyList() }, registry, size, { true }, {})
        assertTrue(assertIs<EditSaveResult.Failed>(video).reason.contains("Profile 5"))
        val photo = saveFramePhoto(file, EditDocument(), { emptyList() }, registry, size, 0L)
        assertTrue(assertIs<EditSaveResult.Failed>(photo).reason.contains("Profile 5"))
        assertTrue(root.listFiles().orEmpty().none { it.name != "p5.mp4" && it.extension in listOf("mp4", "jpg") })
    }

    private fun p5(): File {
        val file = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", file.isFile)
        return file
    }
}
