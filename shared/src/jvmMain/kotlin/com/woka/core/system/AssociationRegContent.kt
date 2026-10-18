package com.woka.core.system

import com.woka.core.data.ALL_SUPPORTED_MEDIA_EXTENSIONS

/**
 * Builds the content of a `.reg` file registering [exePath] for all supported formats.
 * Imported by [WindowsFileAssociations.register]; every key it writes is under `HKCU`.
 */
internal fun associationRegContent(
    exePath: String,
    extensions: Set<String> = ALL_SUPPORTED_MEDIA_EXTENSIONS
): String {
    val escapedExe = exePath.replace("\\", "\\\\")
    val sb = StringBuilder()

    sb.appendLine("Windows Registry Editor Version 5.00")
    sb.appendLine()

    // 1. Applications\Woka.exe registration
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Applications\\Woka.exe]")
    sb.appendLine("\"FriendlyAppName\"=\"Woka\"")
    sb.appendLine()
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Applications\\Woka.exe\\shell\\open\\command]")
    sb.appendLine("@=\"\\\"$escapedExe\\\" \\\"%1\\\"\"")
    sb.appendLine()
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Applications\\Woka.exe\\SupportedTypes]")
    for (ext in extensions) {
        sb.appendLine("\".$ext\"=\"\"")
    }
    sb.appendLine()

    // 2. ProgID registration
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Woka.AssocFile]")
    sb.appendLine("@=\"Woka Media File\"")
    sb.appendLine("\"FriendlyTypeName\"=\"Woka Media File\"")
    sb.appendLine()
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Woka.AssocFile\\DefaultIcon]")
    sb.appendLine("@=\"\\\"$escapedExe\\\",0\"")
    sb.appendLine()
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Woka.AssocFile\\shell\\open\\command]")
    sb.appendLine("@=\"\\\"$escapedExe\\\" \\\"%1\\\"\"")
    sb.appendLine()

    // 3. OpenWithProgids for each extension
    for (ext in extensions) {
        sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\.$ext\\OpenWithProgids]")
        sb.appendLine("\"Woka.AssocFile\"=\"\"")
        sb.appendLine()
    }

    // 4. Windows Default Apps capabilities
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Woka\\Capabilities]")
    sb.appendLine("\"ApplicationName\"=\"Woka\"")
    sb.appendLine("\"ApplicationDescription\"=\"Woka Media Viewer & Editor\"")
    sb.appendLine("\"ApplicationIcon\"=\"\\\"$escapedExe\\\",0\"")
    sb.appendLine()
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Woka\\Capabilities\\FileAssociations]")
    for (ext in extensions) {
        sb.appendLine("\".$ext\"=\"Woka.AssocFile\"")
    }
    sb.appendLine()

    // 5. RegisteredApplications
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]")
    sb.appendLine("\"Woka\"=\"Software\\\\Woka\\\\Capabilities\"")
    sb.appendLine()

    // 6. MuiCache correction (ensures Windows Shell and Settings display "Woka" instead of any legacy descriptions)
    sb.appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\Shell\\MuiCache]")
    sb.appendLine("\"$escapedExe.FriendlyAppName\"=\"Woka\"")
    sb.appendLine("\"$escapedExe.ApplicationCompany\"=\"OldValencia\"")

    return sb.toString()
}
