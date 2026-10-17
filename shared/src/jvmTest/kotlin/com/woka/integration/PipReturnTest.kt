package com.woka.integration

import com.woka.app.AppDependencies
import com.woka.app.navigation.Screen
import com.woka.app.shell.EditorPresence
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.support.startKoinOnce
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Never opened — the route reads its parent and its path, and nothing decodes it. */
private val FILM = File("woka-films/film.mp4").absoluteFile

/**
 * Returning to the main window, with and without an editor standing in it (ME-124).
 *
 * Integration because the return goes through the real `Router` out of Koin — that is
 * the half a UI test cannot see, and `replaceTop` with a fresh nonce is the exact thing
 * that used to destroy the editor.
 *
 * No decoding happens: `VideoPlayerState`'s constructor only builds state, and the file
 * is never opened because nothing here starts playback.
 */
class PipReturnTest {

    private val router get() = AppDependencies.router

    @BeforeTest
    fun before() {
        startKoinOnce()
        drainPresence()
    }

    @AfterTest
    fun after() {
        drainPresence()
        PipManager.closePip()
        router.start(listOf(Screen.Welcome))
    }

    private fun drainPresence() {
        while (EditorPresence.isOpen) EditorPresence.closed()
    }

    private fun openPip() {
        val item = MediaItem.Video(FILM)
        PipManager.openPip(item, VideoPlayerState(PipManager.pipScope, FILM, 1f))
    }

    @Test
    fun `with no editor open the return routes to the film's folder`() {
        router.navigateTo(Screen.Folder("D:/elsewhere"))
        openPip()

        PipManager.closePip(returnToMain = true)

        val top = router.stack.value.last() as Screen.Folder
        assertEquals(FILM.parent, top.path)
        assertEquals(FILM.absolutePath, top.openViewerForId)
        assertFalse(PipManager.isPipActive)
    }

    /**
     * The order is the ticket: the editor is asked to leave **before** `replaceTop`
     * rebuilds the subtree it lives in. Recorded from inside the action, because after
     * the call both halves have happened and the sequence is no longer observable.
     */
    @Test
    fun `an open editor is asked to leave before the route changes`() {
        val before = Screen.Folder("D:/elsewhere")
        router.navigateTo(before)
        openPip()
        var topWhenAsked: Screen? = null
        var asked = 0
        EditorPresence.opened {
            asked++
            topWhenAsked = router.stack.value.last()
        }

        PipManager.closePip(returnToMain = true)

        assertEquals(1, asked, "the editor was not asked to leave")
        assertEquals(before, topWhenAsked, "the route had already changed under the editor")
        assertTrue(router.stack.value.last() is Screen.Folder)
        assertEquals(FILM.parent, (router.stack.value.last() as Screen.Folder).path)
    }

    /** Closing without returning is untouched: no route, no editor, no question. */
    @Test
    fun `closing the window outright asks nothing and moves nowhere`() {
        val before = Screen.Folder("D:/elsewhere")
        router.navigateTo(before)
        openPip()
        var asked = 0
        EditorPresence.opened { asked++ }

        PipManager.closePip()

        assertEquals(0, asked)
        assertEquals(before, router.stack.value.last())
        assertFalse(PipManager.isPipActive)
    }
}
