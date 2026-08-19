package com.woka.core.utils

/**
 * The **picture** on the clipboard, not the file that holds it.
 *
 * `Copy` used to put the file there, which made it a second Duplicate: pasting into a
 * folder produced a copy of the photograph, and pasting into a message produced
 * nothing at all. Duplicate already covers the first, so Copy answers the other
 * question — "give me this picture to paste somewhere" — the way the Photos apps this
 * one is modelled on answer it.
 *
 * **Drawn, not raw.** The bitmap goes through the same fold every reader uses
 * (`AppliedStillEdits`, ME-133), so what lands on the clipboard is the edited
 * photograph at native resolution. This is also the one path by which an edit leaves
 * Woka at all — inside the file it is invisible to every other program (§5.3).
 *
 * Returns whether anything was put there, so a caller can stay quiet on a file that
 * could not be decoded rather than claiming a copy that did not happen.
 */
expect suspend fun copyImageToClipboard(path: String, frameIndex: Int = 0): Boolean
