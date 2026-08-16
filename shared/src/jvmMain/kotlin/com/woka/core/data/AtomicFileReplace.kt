package com.woka.core.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Put [bytes] at [target] so that no reader ever sees a partial file.
 *
 * Write beside the target, then move over it: `Files.move` with `ATOMIC_MOVE`
 * replaces in one filesystem operation, so a crash or a concurrent reader sees
 * either the old file or the new one and never half of either. The temp file is
 * deleted on every failure path, including the ones that throw.
 *
 * The temp lives **beside** the target rather than in a system temp directory,
 * because `ATOMIC_MOVE` is only guaranteed within one filesystem and the two are
 * routinely on different drives here.
 *
 * Lifted out of `SettingsStore.writeToDisk` in ME-009 so the editor's save path
 * and the settings writer share one implementation. What is deliberately **not**
 * shared is the policy on failure: this throws, and each caller decides. Settings
 * fall back to a plain overwrite because a damaged `settings.json` is recoverable;
 * the editor must never do that to a photograph, so it retries and then gives up.
 */
internal fun replaceAtomically(target: File, bytes: ByteArray) {
    val temp = File(target.parentFile, "${target.name}.woka-tmp")
    try {
        temp.writeBytes(bytes)
        Files.move(
            temp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
}
