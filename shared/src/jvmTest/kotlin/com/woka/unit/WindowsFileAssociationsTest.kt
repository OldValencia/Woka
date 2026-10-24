package com.woka.unit

import com.woka.core.data.ALL_SUPPORTED_MEDIA_EXTENSIONS
import com.woka.core.system.associationRegContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsFileAssociationsTest {

    @Test
    fun `all supported extensions total 108 formats`() {
        assertEquals(108, ALL_SUPPORTED_MEDIA_EXTENSIONS.size)
        // Verify key formats are present
        val expected = listOf(
            "jpg", "png", "webp", "gif", "psd", "psdt", "pdd",
            "mp4", "mkv", "avi", "mov", "webm", "flv"
        )
        for (ext in expected) {
            assertTrue(ALL_SUPPORTED_MEDIA_EXTENSIONS.contains(ext), "Missing extension: $ext")
        }
    }

    @Test
    fun `associationRegContent generates valid registry structure`() {
        val testExe = "C:\\Users\\TestUser\\AppData\\Local\\Woka\\Woka.exe"
        val reg = associationRegContent(testExe)

        // Registry version header
        assertTrue(reg.startsWith("Windows Registry Editor Version 5.00"))

        // Escaped executable path
        val escapedPath = "C:\\\\Users\\\\TestUser\\\\AppData\\\\Local\\\\Woka\\\\Woka.exe"
        assertTrue(reg.contains(escapedPath))

        // Applications\Woka.exe registration
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\Applications\\Woka.exe]"))
        assertTrue(reg.contains("\"FriendlyAppName\"=\"Woka\""))
        assertTrue(reg.contains("@=\"\\\"$escapedPath\\\" \\\"%1\\\"\""))

        // ProgID registration
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\Woka.AssocFile]"))
        assertTrue(reg.contains("\"FriendlyTypeName\"=\"Woka Media File\""))

        // Capabilities and RegisteredApplications
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Woka\\Capabilities]"))
        assertTrue(reg.contains("\"ApplicationName\"=\"Woka\""))
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]"))
        assertTrue(reg.contains("\"Woka\"=\"Software\\\\Woka\\\\Capabilities\""))

        // MuiCache correction
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\Shell\\MuiCache]"))
        assertTrue(reg.contains("\"$escapedPath.FriendlyAppName\"=\"Woka\""))

        // Every extension registered in SupportedTypes, OpenWithProgids, and FileAssociations
        for (ext in ALL_SUPPORTED_MEDIA_EXTENSIONS) {
            assertTrue(reg.contains("\".$ext\"=\"\""), "Missing SupportedType for $ext")
            assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.$ext\\OpenWithProgids]"), "Missing OpenWithProgids for $ext")
            assertTrue(reg.contains("\".$ext\"=\"Woka.AssocFile\""), "Missing FileAssociation for $ext")
        }
    }
}
