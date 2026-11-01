package com.woka.unit

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.app.shell.nowplaying.NowPlaying
import com.woka.app.shell.nowplaying.NowPlayingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A source that only counts how often it was told to stop. */
private class FakeSource(override val path: String) : NowPlayingSource {
    var stops = 0
    override val title = path
    override val subtitle = ""
    override val isPlaying = true
    override val progress = 0f
    override suspend fun cover(): ImageBitmap? = null
    override fun togglePlayPause() = Unit
    override fun stop() {
        stops++
    }
    override fun reveal() = Unit
}

/** The slot's two rules (AP-004): one source at a time, and only the holder can empty it. */
class NowPlayingTest {

    @Test
    fun `claiming another path stops the one held`() {
        val slot = NowPlaying()
        val first = FakeSource("a.mp4")
        val second = FakeSource("b.mp4")
        slot.claim(first)

        slot.claim(second)

        assertEquals(1, first.stops)
        assertEquals(0, second.stops)
        assertSame(second, slot.source)
    }

    @Test
    fun `claiming the same path again hands it on without stopping it`() {
        val slot = NowPlaying()
        val first = FakeSource("a.mp4")
        slot.claim(first)

        val again = FakeSource("a.mp4")
        slot.claim(again)

        assertEquals(0, first.stops)
        assertSame(again, slot.source)
    }

    @Test
    fun `releasing another path leaves the slot alone`() {
        val slot = NowPlaying()
        val held = FakeSource("a.mp4")
        slot.claim(held)

        slot.release("b.mp4")

        assertSame(held, slot.source)
    }

    @Test
    fun `releasing the held path empties the slot`() {
        val slot = NowPlaying()
        slot.claim(FakeSource("a.mp4"))

        slot.release("a.mp4")

        assertNull(slot.source)
    }
}
