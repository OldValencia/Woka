package com.woka.integration

import com.woka.app.AppDependencies
import com.woka.app.navigation.Screen
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.support.startKoinOnce
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Never opened, as in `PipReturnTest`: nothing here starts playback. */
private val FILM = File("woka-films/film.mp4").absoluteFile
private val OTHER = File("woka-films/other.mp4").absoluteFile

/**
 * PiP fills and empties Now Playing's slot (AP-004). Integration because the slot is
 * the real one out of Koin, the one the sidebar reads.
 */
class PipNowPlayingTest {

    private val slot get() = AppDependencies.nowPlaying

    @BeforeTest
    fun before() {
        startKoinOnce()
        PipManager.closePip()
    }

    @AfterTest
    fun after() {
        PipManager.closePip()
        AppDependencies.router.start(listOf(Screen.Welcome))
    }

    private fun openPip(file: File) {
        PipManager.openPip(MediaItem.Video(file), VideoPlayerState(PipManager.pipScope, file, 1f))
    }

    @Test
    fun `a video sent to PiP is what is playing`() {
        openPip(FILM)

        assertEquals(FILM.absolutePath, slot.source?.path)
        assertEquals("Picture in Picture", slot.source?.subtitle)
    }

    @Test
    fun `closing PiP empties the slot`() {
        openPip(FILM)

        PipManager.closePip()

        assertNull(slot.source)
    }

    @Test
    fun `returning the video to the viewer empties the slot too`() {
        openPip(FILM)

        PipManager.closePip(returnToMain = true)

        assertNull(slot.source)
    }

    @Test
    fun `stop from the row closes PiP`() {
        openPip(FILM)

        slot.source?.stop()

        assertNull(slot.source)
        assertNull(PipManager.videoState)
    }

    /** The old source's stop must not close the film that replaced it. */
    @Test
    fun `a second film in PiP takes the slot and stays open`() {
        openPip(FILM)

        openPip(OTHER)

        assertEquals(OTHER.absolutePath, slot.source?.path)
        assertEquals(OTHER.absolutePath, PipManager.activeItem?.file?.absolutePath)
    }

    @Test
    fun `the row's click asks the PiP window to come forward`() {
        val before = PipManager.frontRequests
        openPip(FILM)

        slot.source?.reveal()

        assertEquals(before + 1, PipManager.frontRequests)
    }
}
