package com.woka.unit

import com.woka.app.shell.EditorPresence
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fact the video window asks about before it changes the route (ME-124).
 *
 * A count rather than a flag: it was written for ME-128's several live editors, and when
 * that was dropped (2026-09-01) what the count still earns is that no sequence of calls
 * can wedge it — asserted below in both directions. The object is drained between tests
 * because it is a singleton, and a leaked `opened` would make the next test's video
 * window believe an editor is up, and `ExitWhenQuiet` wait for an answer nobody gives.
 */
class EditorPresenceTest {

    private fun drain() {
        while (EditorPresence.isOpen) EditorPresence.closed()
    }

    @BeforeTest
    fun before() = drain()

    @AfterTest
    fun after() = drain()

    @Test
    fun `nothing is open at rest, and there is nothing to invoke`() {
        assertFalse(EditorPresence.isOpen)
        assertNull(EditorPresence.saveAndClose)
    }

    @Test
    fun `an editor publishes the action it wants invoked`() {
        var left = 0
        EditorPresence.opened { left++ }

        assertTrue(EditorPresence.isOpen)
        EditorPresence.saveAndClose?.invoke()

        assertEquals(1, left)
    }

    /**
     * The count's other half: two opens need two closes. Not reachable through the UI
     * with one editor, and kept as the assertion that says the arithmetic is arithmetic
     * rather than a boolean wearing an `Int`.
     */
    @Test
    fun `two editors need two closes`() {
        EditorPresence.opened {}
        EditorPresence.opened {}

        EditorPresence.closed()
        assertTrue(EditorPresence.isOpen, "one close ended a presence two editors held")

        EditorPresence.closed()
        assertFalse(EditorPresence.isOpen)
        assertNull(EditorPresence.saveAndClose, "a closed editor left its action behind")
    }

    /** An unbalanced close must not make the count negative and swallow the next open. */
    @Test
    fun `closing more than was opened cannot go below nothing`() {
        EditorPresence.closed()
        EditorPresence.opened {}
        assertTrue(EditorPresence.isOpen)
    }
}
