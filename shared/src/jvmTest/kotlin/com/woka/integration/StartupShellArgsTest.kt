package com.woka.integration

import com.woka.app.navigation.Screen
import com.woka.app.navigation.startupStack
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The launch line a Windows file association hands over: the `%*` / `%1`
 * placeholders and surrounding quotes are stripped before [startupStack] routes it.
 * The plain routing table lives in [StartupRouteTest].
 */
class StartupShellArgsTest {

    private fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-startup").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.touch(name: String): File =
        File(this, name).also { it.writeText("x") }

    private fun folderOf(stack: List<Screen>): Screen.Folder = stack.last() as Screen.Folder

    @Test
    fun `windows file association with trailing percent-star is handled`() {
        inDir { dir ->
            val photo = dir.touch("a summer shot.heic")
            val split = photo.absolutePath.split(" ") + listOf("%*")
            assertEquals(photo.absolutePath, folderOf(startupStack(split)).openViewerForId)
        }
    }

    @Test
    fun `windows file association with joined string containing percent-star is handled`() {
        inDir { dir ->
            val photo = dir.touch("a summer shot.heic")
            assertEquals(photo.absolutePath, folderOf(startupStack(listOf(photo.absolutePath + " %*"))).openViewerForId)
        }
    }

    @Test
    fun `windows file association with unicode and spaces is handled`() {
        inDir { dir ->
            val photo = dir.touch("A Certain Magical Index とある魔術の禁書目録 new.heic")
            val split = photo.absolutePath.split(" ") + listOf("%*")
            assertEquals(photo.absolutePath, folderOf(startupStack(split)).openViewerForId)
        }
    }

    @Test
    fun `windows file association with quotes is handled`() {
        inDir { dir ->
            val photo = dir.touch("quoted shot.heic")
            val quoted = listOf("\"${photo.absolutePath}\"", "%*")
            assertEquals(photo.absolutePath, folderOf(startupStack(quoted)).openViewerForId)
        }
    }

    @Test
    fun `only percent-star placeholder opens Welcome`() {
        assertEquals(listOf(Screen.Welcome), startupStack(listOf("%*")))
    }
}
