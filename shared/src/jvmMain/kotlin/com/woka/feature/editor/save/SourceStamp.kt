package com.woka.feature.editor.save

import java.io.File

/**
 * What the source file looked like when the editor opened it (§5, stale-source
 * guard).
 *
 * Checked immediately before a save: if the file changed on disk meanwhile —
 * another application wrote it, a sync client pulled a new version, the user
 * edited it in a second window — the save stops and says so rather than throwing
 * away a change it never saw. This matters most for `Overwrite`, where the lost
 * change would be unrecoverable, but a `Copy` made from a stale decode is also
 * wrong, just less expensively.
 *
 * Length **and** modification time, because either alone is weak: an edit that
 * preserves the byte count is common in image formats with fixed-size headers,
 * and a timestamp can be restored by a copying tool. A content hash would be
 * correct and would also mean re-reading the whole file before every save.
 *
 * A file that has disappeared does not match — deleting the source between open
 * and save is exactly the case a "still there?" check must catch.
 */
internal data class SourceStamp(
    val lengthBytes: Long,
    val lastModifiedMs: Long
) {
    fun matches(file: File): Boolean =
        file.isFile && file.length() == lengthBytes && file.lastModified() == lastModifiedMs

    companion object {
        fun of(file: File): SourceStamp = SourceStamp(file.length(), file.lastModified())
    }
}
