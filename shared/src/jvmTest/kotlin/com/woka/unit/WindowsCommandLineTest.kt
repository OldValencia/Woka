package com.woka.unit

import com.woka.core.system.WindowsCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsCommandLineTest {

    private val original = "D:\\Anime\\A Certain Magical Index とある魔術の禁書目録 new.heic"
    private val damaged = "D:\\Anime\\A Certain Magical Index ?????????? new.heic"

    @Test
    fun `resolveArgs on empty input returns empty list`() {
        val result = WindowsCommandLine.resolveArgs(emptyArray())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `reconcile recovers a CJK path after the exe path`() {
        val result = WindowsCommandLine.reconcile(listOf(damaged), listOf("C:\\Woka\\Woka.exe", original))
        assertEquals(listOf(original), result)
    }

    @Test
    fun `reconcile recovers a CJK path after JVM flags and main class`() {
        val win32 = listOf("java.exe", "-Xmx2g", "-cp", "app.jar", "com.woka.MainKt", original, "--flag")
        val result = WindowsCommandLine.reconcile(listOf(damaged, "--flag"), win32)
        assertEquals(listOf(original, "--flag"), result)
    }

    @Test
    fun `reconcile recovers an emoji whose question mark count differs`() {
        val result = WindowsCommandLine.reconcile(listOf("D:\\?.png"), listOf("Woka.exe", "D:\\😀.png"))
        assertEquals(listOf("D:\\😀.png"), result)
    }

    @Test
    fun `reconcile keeps launcher args when the tail is misaligned`() {
        val raw = listOf("D:\\a.heic")
        val result = WindowsCommandLine.reconcile(raw, listOf("Woka.exe", "D:\\b.heic"))
        assertEquals(raw, result)
    }

    @Test
    fun `reconcile keeps launcher args when Win32 has fewer`() {
        val raw = listOf("D:\\a.heic", "x")
        val result = WindowsCommandLine.reconcile(raw, listOf("D:\\a.heic"))
        assertEquals(raw, result)
    }

    @Test
    fun `getRawCommandLineArgs on Windows returns non-null process args`() {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (!isWindows) return

        val raw = WindowsCommandLine.getRawCommandLineArgs()
        assertTrue(raw != null && raw.isNotEmpty(), "Expected non-empty Win32 command line arguments")
    }
}
