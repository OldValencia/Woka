package com.woka.feature.viewer.shell

import com.woka.core.data.MediaItem
import com.woka.core.data.isPlayable

/**
 * Whether opening [item] collides with what the PiP window is already playing.
 *
 * **Only a second video collides — or a track, which would silence the film (AP-003).**
 * The conflict is over one thing — `PipManager` owns the single `VideoPlayerState`, so
 * a second video has to decide which file that player is on, and the dialog is what asks.
 * A photograph uses none of it: it decodes through `ImageBitmapCache` and never touches
 * the player. It was being refused anyway, because the check asked only whether *a*
 * file was being opened while PiP was up — so opening a picture while a film played in
 * the corner raised "Video Already Playing", a question about a conflict a picture
 * cannot have (found by hand, 2026-08-30).
 *
 * The same file is not a conflict either: that is the return from fullscreen, which the
 * effect below this one carries the size state through.
 *
 * Takes the two `PipManager` facts as parameters rather than reading the object, so the
 * rule is a pure function with a unit test instead of a condition only a running window
 * can exercise.
 */
internal fun pipConflicts(item: MediaItem?, pipActive: Boolean, pipPath: String?): Boolean =
    pipActive && item?.isPlayable == true && item.file.absolutePath != pipPath

/**
 * Whether opening [item] would silence the track playing (AP-003): the same question
 * as above, asked of the music. "One sound source" holds either way; the dialog only
 * makes the stop a choice instead of a surprise.
 */
internal fun musicConflicts(item: MediaItem?, musicPath: String?): Boolean =
    musicPath != null && item is MediaItem.Video
