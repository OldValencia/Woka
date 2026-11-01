package com.woka.core.system

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Windows file associations and Default Apps registration.
 *
 * Registers Woka as a media handler for all supported image and video formats under `HKCU`.
 * Because this writes exclusively to `HKEY_CURRENT_USER`, it requires no administrator elevation (UAC).
 *
 * Registration covers:
 * - `HKCU\Software\Classes\Applications\Woka.exe` (FriendlyAppName="Woka", SupportedTypes)
 * - `HKCU\Software\Classes\Woka.AssocFile` (ProgID for open command and icon)
 * - `HKCU\Software\Classes\.<ext>\OpenWithProgids` (adds Woka to Explorer's "Open with" menu)
 * - `HKCU\Software\Woka\Capabilities\FileAssociations` (registered app capabilities)
 * - `HKCU\Software\RegisteredApplications` (exposes Woka in Windows "Default Apps")
 * - `HKCU\Software\Classes\Local Settings\Software\Microsoft\Windows\Shell\MuiCache` (fixes display name cache to "Woka")
 */
object WindowsFileAssociations {

    private val isWindows: Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    private val regExe: String by lazy {
        val sysRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val exe = File(sysRoot, "System32\\reg.exe")
        if (exe.exists()) exe.absolutePath else "reg"
    }

    /**
     * Resolves the executable path.
     * When running as an installed app, returns `Woka.exe`.
     * If running in development (e.g. from Gradle / java.exe), falls back to the installed Woka.exe.
     */
    fun executablePath(customPath: String? = null): String? {
        if (customPath != null) {
            val f = File(customPath)
            if (f.exists()) return f.absolutePath
        }

        val cmd = ProcessHandle.current().info().command().orElse(null)
        if (cmd != null) {
            val file = File(cmd)
            if (file.exists() && file.name.endsWith(".exe", ignoreCase = true) && !file.name.contains("java", ignoreCase = true)) {
                return file.absolutePath
            }
        }

        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            val localApp = File(localAppData, "Woka\\Woka.exe")
            if (localApp.exists()) return localApp.absolutePath
        }

        val userHomeLocal = File(System.getProperty("user.home"), "AppData\\Local\\Woka\\Woka.exe")
        if (userHomeLocal.exists()) return userHomeLocal.absolutePath

        val progFiles = File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Woka\\Woka.exe")
        if (progFiles.exists()) return progFiles.absolutePath

        val devBuild = File(System.getProperty("user.dir"), "desktopApp\\build\\compose\\binaries\\main\\app\\Woka\\Woka.exe")
        if (devBuild.exists()) return devBuild.absolutePath

        return null
    }

    /**
     * Registers all supported file associations in the Windows Registry under `HKCU`.
     */
    fun register(customExePath: String? = null): Boolean {
        if (!isWindows) return false
        val exe = executablePath(customExePath) ?: return false

        val regContent = associationRegContent(exe)
        val tempFile = File.createTempFile("woka-assoc-", ".reg")
        try {
            // Write with UTF-16LE BOM as expected by reg.exe import
            tempFile.writeText("\uFEFF$regContent", StandardCharsets.UTF_16LE)
            val process = ProcessBuilder(regExe, "import", tempFile.absolutePath).start()
            val success = process.waitFor() == 0
            return success
        } catch (_: Exception) {
            return false
        } finally {
            tempFile.delete()
        }
    }
}
