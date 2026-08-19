package com.woka.unit

import com.woka.core.ui.image.MediaInvalidation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The signal a save raises (ME-135).
 *
 * The generation is the whole of what is being asserted: a `StateFlow` drops a value
 * equal to the one it holds, so without it the **second** save of a photograph in one
 * sitting would publish nothing and the screen would keep the first save's picture.
 */
class MediaInvalidationTest {

    @Test
    fun `two saves of one file publish two distinct values`() {
        MediaInvalidation.invalidate("C:\\photos\\one.jpg")
        val first = MediaInvalidation.latest.value
        MediaInvalidation.invalidate("C:\\photos\\one.jpg")
        val second = MediaInvalidation.latest.value

        assertNotEquals(first, second, "the flow would drop an equal value")
        assertEquals("C:\\photos\\one.jpg", second?.path)
        assertTrue(second!!.generation > first!!.generation)
    }

    /** What the readers filter on: the value names the file it is about. */
    @Test
    fun `an unrelated save is a value about that other path`() {
        MediaInvalidation.invalidate("C:\\photos\\open.jpg")
        MediaInvalidation.invalidate("C:\\photos\\elsewhere.jpg")

        assertNotEquals("C:\\photos\\open.jpg", MediaInvalidation.latest.value?.path)
    }
}
