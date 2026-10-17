package com.woka.unit

import com.woka.core.data.PinnedSession
import com.woka.core.data.WatchRecord
import com.woka.core.data.WokaSettings
import com.woka.core.data.withFolderRestored
import com.woka.core.data.withoutFolder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Undoing an unlink (SIDE-010): everything the sweep took comes back, at its old
 * place, and nothing written in the seconds since is lost.
 */
class UnlinkUndoTest {

    private val before = WokaSettings(
        linkedFolders = listOf("D:/A", "D:/Films", "D:/Z"),
        pinnedSessions = listOf(PinnedSession("D:/Films", 4, 2)),
        watchRecords = mapOf(
            "D:/Films/a.mkv" to WatchRecord(60_000L, 5_400_000L),
            "D:/A/b.mkv" to WatchRecord(1_000L, 2_000_000L)
        )
    )

    @Test
    fun `the link, its pin and its records come back where they were`() {
        val swept = before.withoutFolder("D:/Films", '/').copy(
            pinnedSessions = emptyList()
        )
        val restored = swept.withFolderRestored(before, "D:/Films", '/')
        assertEquals(listOf("D:/A", "D:/Films", "D:/Z"), restored.linkedFolders)
        assertEquals(before.pinnedSessions, restored.pinnedSessions)
        assertEquals(before.watchRecords, restored.watchRecords)
    }

    @Test
    fun `what changed since the unlink is kept`() {
        val swept = before.withoutFolder("D:/Films", '/')
        val since = swept.copy(
            mediaVolume = 0.9f,
            watchRecords = swept.watchRecords + ("D:/A/b.mkv" to WatchRecord(9_000L, 2_000_000L))
        )
        val restored = since.withFolderRestored(before, "D:/Films", '/')
        assertEquals(0.9f, restored.mediaVolume)
        assertEquals(9_000L, restored.watchRecords["D:/A/b.mkv"]?.positionMs)
        assertEquals(60_000L, restored.watchRecords["D:/Films/a.mkv"]?.positionMs)
    }

    @Test
    fun `a folder relinked by hand meanwhile is not doubled`() {
        val relinked = before.withoutFolder("D:/Films", '/').let { it.copy(linkedFolders = it.linkedFolders + "D:/Films") }
        val restored = relinked.withFolderRestored(before, "D:/Films", '/')
        assertEquals(1, restored.linkedFolders.count { it == "D:/Films" })
    }
}
