package com.woka.unit

import com.woka.app.navigation.Router
import com.woka.app.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One screen, no history (SIDE-003): every navigation replaces what is shown, so
 * there is never anything underneath to go back to, and a bad launch route cannot
 * leave the window empty.
 */
class RouterTest {

    private val films = Screen.Folder("D:/Films")
    private val series = Screen.Folder("D:/Films/Series")

    @Test
    fun `a fresh router shows Welcome`() {
        assertEquals(listOf<Screen>(Screen.Welcome), Router().stack.value)
    }

    @Test
    fun `a navigation replaces the screen rather than stacking it`() {
        val router = Router().apply { start(listOf(films)) }

        router.navigateTo(series)

        assertEquals(listOf<Screen>(series), router.stack.value)
    }

    @Test
    fun `start shows the last screen it is given`() {
        val router = Router()

        router.start(listOf(films, series))

        assertEquals(listOf<Screen>(series), router.stack.value)
    }

    @Test
    fun `an empty start leaves the screen alone`() {
        val router = Router().apply { start(listOf(films)) }

        router.start(emptyList())

        assertEquals(listOf<Screen>(films), router.stack.value)
    }
}
