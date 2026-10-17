package com.woka.core.system

import java.io.File

/**
 * Windows Explorer folder context menu integration ("Open with Woka").
 *
 * Registers shell commands under `HKCU\Software\Classes\Directory\shell\Woka` and
 * `HKCU\Software\Classes\Directory\Background\shell\Woka`. Because this writes to
 * the current user's registry hive (`HKCU`), it requires no administrator elevation
 * or UAC prompts.
 */
object FolderContextMenu {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    fun isSupported(): Boolean = isWindows

    /**
     * Finds the path to the current executable.
     *
     * In an installed distribution, [ProcessHandle] reports `C:\...\Woka.exe`.
     */
    fun executablePath(): String? {
        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        val file = File(cmd)
        if (file.exists() && file.name.endsWith(".exe", ignoreCase = true)) {
            return file.absolutePath
        }
        return null
    }

    fun isRegistered(): Boolean {
        if (!isWindows) return false
        return try {
            val process = ProcessBuilder("reg", "query", "HKCU\\Software\\Classes\\Directory\\shell\\Woka").start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Registers the context menu on the first launch of an installed package.
     * Subsequent launches respect the user's manual toggle from Settings.
     */
    fun firstRunRegisterIfNeeded(storageRoot: File): Boolean {
        if (!isSupported() || executablePath() == null) return false
        val marker = File(storageRoot, ".context_menu_initialized")
        if (marker.exists()) return false
        try {
            storageRoot.mkdirs()
            marker.createNewFile()
        } catch (_: Exception) {
            // Marker could not be created
        }
        return register()
    }

    /**
     * Registers "Open with Woka" for folders and directory backgrounds in Windows Explorer.
     */
    fun register(customExePath: String? = null): Boolean {
        if (!isWindows) return false
        val exe = customExePath ?: executablePath() ?: return false
        val icon = "\"$exe\""
        val cmdFolder = "\"$exe\" \"%1\""
        val cmdBackground = "\"$exe\" \"%V\""

        return try {
            runReg("add", "HKCU\\Software\\Classes\\Directory\\shell\\Woka", "/ve", "/t", "REG_SZ", "/d", "Open with Woka", "/f")
            runReg("add", "HKCU\\Software\\Classes\\Directory\\shell\\Woka", "/v", "Icon", "/t", "REG_SZ", "/d", icon, "/f")
            runReg("add", "HKCU\\Software\\Classes\\Directory\\shell\\Woka\\command", "/ve", "/t", "REG_SZ", "/d", cmdFolder, "/f")

            runReg("add", "HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka", "/ve", "/t", "REG_SZ", "/d", "Open with Woka", "/f")
            runReg("add", "HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka", "/v", "Icon", "/t", "REG_SZ", "/d", icon, "/f")
            runReg("add", "HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka\\command", "/ve", "/t", "REG_SZ", "/d", cmdBackground, "/f")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unregister(): Boolean {
        if (!isWindows) return false
        return try {
            runReg("delete", "HKCU\\Software\\Classes\\Directory\\shell\\Woka", "/f")
            runReg("delete", "HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka", "/f")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun runReg(vararg args: String): Int {
        val process = ProcessBuilder("reg", *args).start()
        return process.waitFor()
    }
}
