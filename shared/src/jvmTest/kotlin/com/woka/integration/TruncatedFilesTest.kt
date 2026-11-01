package com.woka.integration

import com.woka.fixtures.SampleMedia
import com.woka.support.awaitTrue
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * VF-103: files that stop mid-write. Each plays to its cut and stops as at an
 * ordinary end; the one that cannot open at all says so. The measurement over the
 * hand-check samples is `probe/TruncatedFileProbe`.
 */
class TruncatedFilesTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `a cut mkv plays to the cut and ends`() {
        // States 4 s in its header; 53 frames, 2.1 s, are actually there.
        onPlayer(SampleMedia.file("cut.mkv")) { player ->
            awaitTrue("the end", timeoutMs = 15_000L) { player.reachedEnd }
            assertFalse(player.isPlaying, "still playing after the cut")
            assertFalse(player.openFailed, "a playable file was reported as unopenable")
        }
    }

    @Test
    fun `a dav export cut mid-frame plays to the cut and ends`() {
        onPlayer(SampleMedia.file("dahua.dav")) { player ->
            awaitTrue("the end", timeoutMs = 15_000L) { player.reachedEnd }
            assertFalse(player.isPlaying, "still playing after the cut")
        }
    }

    @Test
    fun `an mp4 cut before its index was written says it cannot be played`() {
        onPlayer(SampleMedia.file("nomoov.mp4")) { player ->
            awaitTrue("the open to fail", timeoutMs = 15_000L) { player.openFailed }
            assertNull(player.currentFrame)
            assertFalse(player.isPlaying)
        }
    }
}
