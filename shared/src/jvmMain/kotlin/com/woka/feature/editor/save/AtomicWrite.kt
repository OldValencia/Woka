package com.woka.feature.editor.save

import com.woka.core.data.replaceAtomically
import java.io.File

private const val RETRIES = 3

private const val RETRY_DELAY_MS = 120L

/**
 * Writes [bytes] to [target] or leaves the filesystem exactly as it was.
 *
 * The mechanism — temp beside the target, one `ATOMIC_MOVE` over it, temp deleted
 * on every failure path — is `core/data/replaceAtomically`, shared with the
 * settings writer. What is **not** shared is what happens when it fails.
 *
 * `SettingsStore` falls back to a plain overwrite, because a damaged
 * `settings.json` is read once and replaced. Here there is no fallback and there
 * must never be one: a plain overwrite of a photograph that then fails halfway is
 * precisely the destroyed original §5 exists to prevent. This retries and then
 * reports.
 *
 * Three attempts, 120 ms apart, because on Windows the viewer underneath may still
 * hold the file open and the handle usually clears within a frame or two. The
 * delay is a plain sleep rather than a coroutine `delay`: this runs on an IO
 * thread inside a save job that has nothing else to do meanwhile.
 */
internal fun writeAtomically(target: File, bytes: ByteArray): Result<Unit> {
    var last: Exception? = null
    repeat(RETRIES) { attempt ->
        try {
            replaceAtomically(target, bytes)
            return Result.success(Unit)
        } catch (e: Exception) {
            last = e
            if (attempt < RETRIES - 1) {
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
    }
    return Result.failure(last ?: IllegalStateException("write to ${target.name} failed"))
}
