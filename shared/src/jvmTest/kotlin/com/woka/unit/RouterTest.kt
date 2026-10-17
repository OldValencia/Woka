package com.woka.unit

import com.woka.app.navigation.Router
import com.woka.app.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Back and Forward (SIDE-002): the router's two lists, cleared the way a browser
 * clears them. A Forward that replayed an arrival command would reopen a viewer
 * the user had already closed, so every screen it hands back is disarmed.
 */
class RouterTest {

    private val films = Screen.Folder("D:/Films")
    private val series = Screen.Folder("D:/Films/Series")

    @Test
    fun `back then forward returns to the same folder`() {
        val router = Router().apply { start(listOf(films)) }
        router.navigateTo(series)

        router.pop()
        assertEquals(films, router.stack.value.last())
        assertEquals(listOf<Screen>(series), router.forward.value)

        router.goForward()
        assertEquals(listOf<Screen>(films, series), router.stack.value)
        assertTrue(router.forward.value.isEmpty())
    }

    @Test
    fun `forward hands back a folder with its arrival command spent`() {
        val router = Router().apply { start(listOf(films)) }
        router.navigateTo(series.copy(openViewerForId = "D:/Films/Series/e01.mkv", nonce = 7L))

        router.pop()
        router.goForward()

        assertEquals(series, router.stack.value.last())
    }

    @Test
    fun `a new navigation clears forward`() {
        val router = Router().apply { start(listOf(films)) }
        router.navigateTo(series)
        router.pop()

        router.navigateTo(Screen.Folder("D:/Pictures"))

        assertTrue(router.forward.value.isEmpty())
    }

    @Test
    fun `start clears both lists`() {
        val router = Router().apply { start(listOf(films)) }
        router.navigateTo(series)
        router.pop()

        router.start(listOf(Screen.Welcome))

        assertEquals(listOf<Screen>(Screen.Welcome), router.stack.value)
        assertTrue(router.forward.value.isEmpty())
    }

    @Test
    fun `back never empties the stack`() {
        val router = Router().apply { start(listOf(films)) }

        router.pop()

        assertEquals(listOf<Screen>(films), router.stack.value)
        assertTrue(router.forward.value.isEmpty())
    }
}
