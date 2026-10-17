package com.woka.integration

import com.woka.core.utils.directoryChanges
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The open folder's watch (SIDE-007), against a real temp folder.
 *
 * **Real time, on purpose and bounded.** The platform delivers file events on its
 * own schedule, so each row waits — 500 ms for the watch to be registered, at most
 * 5 s for an event that should come, 1.5 s of silence for one that should not.
 */
class DirectoryWatchTest {

    private inline fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-watch").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Collects the first emission after [act] runs on a registered watch, or null. */
    private suspend fun firstAfter(dir: File, waitMs: Long, act: () -> Unit): Unit? = coroutineScope {
        val got = async { withTimeoutOrNull(waitMs) { directoryChanges(dir.path).first() } }
        delay(500)
        act()
        got.await()
    }

    @Test
    fun `a created file is reported`() = runBlocking {
        inDir { dir ->
            assertNotNull(firstAfter(dir, 5_000) { File(dir, "shot.png").writeText("x") })
        }
    }

    @Test
    fun `a deleted file is reported`() = runBlocking {
        inDir { dir ->
            val file = File(dir, "old.png").also { it.writeText("x") }
            assertNotNull(firstAfter(dir, 5_000) { file.delete() })
        }
    }

    @Test
    fun `a file inside a sub-folder is not the folder's change`() = runBlocking {
        inDir { dir ->
            val sub = File(dir, "Season 1").also { it.mkdirs() }
            delay(300)
            assertNull(firstAfter(dir, 1_500) { File(sub, "e01.mkv").writeText("x") })
        }
    }

    @Test
    fun `cancelling the collector closes the watch quietly`() = runBlocking {
        inDir { dir ->
            withTimeout(5_000) {
                val job = launch { directoryChanges(dir.path).collect { } }
                delay(300)
                job.cancel()
                job.join()
            }
            File(dir, "after.png").writeText("x")
        }
    }
}
