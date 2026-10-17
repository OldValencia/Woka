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

    private val regExe: String by lazy {
        val sysRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val exe = File(sysRoot, "System32\\reg.exe")
        if (exe.exists()) exe.absolutePath else "reg"
    }

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
            val process = ProcessBuilder(regExe, "query", "HKCU\\Software\\Classes\\Directory\\shell\\Woka\\command").start()
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
        val r = regExe

        val ok1 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\shell\\Woka\" /ve /d \"Open with Woka\" /f")
        val ok2 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\shell\\Woka\" /v Icon /d \"$exe\" /f")
        val ok3 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\shell\\Woka\\command\" /ve /d \"\\\"$exe\\\" \\\"%1\\\"\" /f")

        val ok4 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka\" /ve /d \"Open with Woka\" /f")
        val ok5 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka\" /v Icon /d \"$exe\" /f")
        val ok6 = runCmd("$r add \"HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka\\command\" /ve /d \"\\\"$exe\\\" \\\"%V\\\"\" /f")

        return ok1 && ok2 && ok3 && ok4 && ok5 && ok6
    }

    fun unregister(): Boolean {
        if (!isWindows) return false
        val r = regExe
        val ok1 = runCmd("$r delete \"HKCU\\Software\\Classes\\Directory\\shell\\Woka\" /f")
        val ok2 = runCmd("$r delete \"HKCU\\Software\\Classes\\Directory\\Background\\shell\\Woka\" /f")
        return ok1 && ok2
    }

    private fun runCmd(command: String): Boolean {
        return try {
            val process = ProcessBuilder("cmd.exe", "/c", command).start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
