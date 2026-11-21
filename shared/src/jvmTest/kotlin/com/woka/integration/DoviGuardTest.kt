package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.core.data.TransferKind
import com.woka.core.data.WokaStorage
import com.woka.feature.editor.engine.video.videoWriteFailure
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.preview.hoverExcerpts
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DoviGuardTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `P5 returns its first image and a seek image`() {
        val grabber = FreshFrameGrabber(p5()).apply {
            imageWidth = 400
            imageHeight = 225
            setVideoOption("threads", "2")
        }
        try {
            grabber.start()
            assertNotNull(grabber.grabImage())
            val sought = assertNotNull(grabber.grabImageAt(1_000_000L))
            assertEquals(400, sought.imageWidth)
            assertEquals(225, sought.imageHeight)
        } finally {
            runCatching { grabber.stop() }
            grabber.release()
        }
    }

    @Test
    fun `P5 produces a cached poster and a hover excerpt`() = withTempStorageRoot {
        val file = p5()
        assertNotNull(VideoThumbnailExtractor.load(MediaItem.Video(file)))
        assertTrue(WokaStorage.thumbnailFile(file).exists())
        runBlocking {
            val excerpts = withTimeout(15_000L) { hoverExcerpts(file).take(1).toList() }
            assertEquals(1, excerpts.size)
        }
    }

    @Test
    fun `the player publishes reconstructed P5 with its HDR peaks`() {
        onPlayer(p5()) { player ->
            awaitTrue("a reconstructed P5 frame", timeoutMs = 15_000L) { player.currentFrame != null }
            assertNull(player.videoFailure.message)
            assertFalse(player.openFailed)
            val hdr = assertNotNull(player.currentFrameHdrInfo)
            assertEquals(TransferKind.PQ, hdr.transfer)
            assertTrue(hdr.dolbyVisionProfile5)
            assertNotNull(hdr.whitePoint)
            assertNotNull(hdr.scenePeak)
        }
    }

    @Test
    fun `Done accepts P5 only after decoding its first RPU`() {
        assertNull(videoWriteFailure(p5()))
    }

    private fun p5(): File {
        val file = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", file.isFile)
        return file
    }
}
