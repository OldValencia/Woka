package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable

/**
 * Everything the shell needs to know about the frames inside one file (ME-156).
 *
 * One value rather than four members on [EditorEngine], because they come from the
 * same two reads the engine already makes — the decoder's frame set and the stored
 * payload — and because the strip's marks (ME-157) want [overridden] as much as the
 * scope switch wants [storedScope].
 *
 * The defaults describe a single picture, which is the overwhelmingly common case and
 * must cost nothing: no strip, no measurement, no extra decode.
 */
@Immutable
data class EditorFrames(
    val count: Int = 1,
    /**
     * Whether this file is *watched* rather than stepped through — a GIF or an
     * animated WebP, where the clock advances the frame index and every frame goes
     * through the same fold (ME-138, ME-140).
     */
    val playsAsAnimation: Boolean = false,
    /** Which scope the open frame was last written in, so a reopen continues it. */
    val storedScope: EditScope = EditScope.AllFrames,
    /** Frame indices the file carries an override for. */
    val overridden: Set<Int> = emptySet()
) {

    /**
     * Whether "this frame only" means anything for this file.
     *
     * **An animation is excluded on purpose.** An edit applied to one frame of eight
     * is a flicker rather than an edit, so what is left — a burst, a Live Photo's
     * still, a TIFF page chain — is every file that is a *set of pictures* rather
     * than a moving one. The rule lives here, in the one place a later medium
     * changes it.
     */
    val allowsPerFrame: Boolean get() = count > 1 && !playsAsAnimation
}
