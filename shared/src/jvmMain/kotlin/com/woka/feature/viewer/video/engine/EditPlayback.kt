package com.woka.feature.viewer.video.engine

import com.woka.core.data.PlaybackSpeed

/**
 * What an **edit** does to playback, apart from what the user does (ME-216, ME-240): the
 * clip's pace multiplies the user's speed, and the edit's gain multiplies the user's volume.
 *
 * Each has an owner — the composition that set it last — and only that owner can give it
 * back. Measured 2026-11-04: after `Done` the viewer's frame is composed twice over one
 * player for a moment, and the copy leaving reset what the copy arriving had just set.
 */
internal class EditPlayback {
    var userSpeed = PlaybackSpeed.NORMAL
    var clipPace = 1f
    var paceOwner: Any? = null

    /** Read by the audio loop per buffer, written from the main thread per frame. */
    @Volatile
    var gain = 1f
    var gainOwner: Any? = null

    /** The kept ranges and where in them the player is (ME-232). */
    val joins = EditJoins()
}

/**
 * The edit's gain at this moment (ME-240) — mute, volume and the fades — multiplied into the
 * user's volume by the audio loop. Set per frame while an edited film plays.
 */
fun VideoPlayerState.setClipGain(gain: Float, owner: Any) {
    edit.gainOwner = owner
    edit.gain = gain
}

/** Back to the sound as recorded — only if [owner] set the gain last. */
fun VideoPlayerState.releaseClipGain(owner: Any) {
    if (edit.gainOwner !== owner) return
    edit.gainOwner = null
    edit.gain = 1f
}
