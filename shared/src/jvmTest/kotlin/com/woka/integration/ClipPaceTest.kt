package com.woka.integration

import com.woka.feature.viewer.video.engine.releaseClipGain
import com.woka.feature.viewer.video.engine.releaseClipPace
import com.woka.feature.viewer.video.engine.setClipGain
import com.woka.feature.viewer.video.engine.setClipPace
import com.woka.feature.viewer.video.engine.setPlaybackSpeed
import com.woka.support.episodeTwoFile
import com.woka.support.onPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The player's speed is the user's times the edited clip's (ME-216). Reported 2026-11-04:
 * with two setters writing one value, a 4x film played at 1x after `Done` and at 4x after
 * Revert — whichever effect happened to run last. Asserted here in both orders.
 */
class ClipPaceTest {

    @Test
    fun `the clip's pace survives the user's speed being applied after it`() = onPlayer(episodeTwoFile()) { player ->
        player.setClipPace(4f)
        player.setPlaybackSpeed(1f)
        assertEquals(4f, player.playbackSpeed)
    }

    @Test
    fun `and the user's survives the clip's, which multiplies it`() = onPlayer(episodeTwoFile()) { player ->
        player.setPlaybackSpeed(1.5f)
        player.setClipPace(2f)
        assertEquals(3f, player.playbackSpeed)
    }

    @Test
    fun `a revert gives the user's own speed back`() = onPlayer(episodeTwoFile()) { player ->
        val owner = Any()
        player.setPlaybackSpeed(1.25f)
        player.setClipPace(4f, owner)
        player.releaseClipPace(owner)
        assertEquals(1.25f, player.playbackSpeed)
    }

    /**
     * The log the author sent 2026-11-04: two copies of the viewer's frame on one player, the
     * new one setting 4x, then the old one leaving — which used to reset it to 1x.
     */
    @Test
    fun `a leaving owner cannot reset the pace a newer one set`() = onPlayer(episodeTwoFile()) { player ->
        val leaving = Any()
        val staying = Any()
        player.setClipPace(4f, leaving)
        player.setClipPace(4f, staying)
        player.releaseClipPace(leaving)
        assertEquals(4f, player.playbackSpeed)
        player.releaseClipPace(staying)
        assertEquals(1f, player.playbackSpeed)
    }

    /** ME-240: the edit's gain has an owner too, for the same two-copies reason. */
    @Test
    fun `a leaving owner cannot reset the gain a newer one set`() = onPlayer(episodeTwoFile()) { player ->
        val leaving = Any()
        val staying = Any()
        player.setClipGain(0f, leaving)
        player.setClipGain(0.5f, staying)
        player.releaseClipGain(leaving)
        assertEquals(0.5f, player.edit.gain)
        player.releaseClipGain(staying)
        assertEquals(1f, player.edit.gain)
    }
}

