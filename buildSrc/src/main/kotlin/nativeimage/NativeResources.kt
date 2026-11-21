package nativeimage

import java.io.File

/**
 * The Windows resources linked into `Woka.exe` (ST-005): Woka's icon and a version block.
 * jpackage brands its own launcher with both; an installer built from the native image takes
 * the executable as it is, so without these the Start-menu shortcut and Explorer showed the
 * generic program icon and no version.
 *
 * Compiled by `rc.exe` inside the same `vcvars64` shell as the image (`runNativeImage`); the
 * `.res` it writes is what [resourceLinkerOption] hands the linker.
 */
fun writeResourceScript(work: File, icon: File, version: String): File {
    val numeric = (version.split('.').map { it.toIntOrNull() ?: 0 } + List(4) { 0 }).take(4).joinToString(",")
    val script = File(work, "woka.rc")
    script.writeText(
        """
        |1 ICON "${icon.absolutePath.replace("\\", "\\\\")}"
        |1 VERSIONINFO
        |FILEVERSION $numeric
        |PRODUCTVERSION $numeric
        |BEGIN
        |  BLOCK "StringFileInfo"
        |  BEGIN
        |    BLOCK "040904B0"
        |    BEGIN
        |      VALUE "CompanyName", "OldValencia"
        |      VALUE "FileDescription", "Woka"
        |      VALUE "FileVersion", "$version"
        |      VALUE "LegalCopyright", "Copyright 2026 OldValencia"
        |      VALUE "OriginalFilename", "Woka.exe"
        |      VALUE "ProductName", "Woka"
        |      VALUE "ProductVersion", "$version"
        |    END
        |  END
        |  BLOCK "VarFileInfo"
        |  BEGIN
        |    VALUE "Translation", 0x409, 1200
        |  END
        |END
        |""".trimMargin()
    )
    return script
}

/** Where `rc.exe` writes [script]'s compiled form. */
fun compiledResource(script: File): File = File(script.parentFile, script.nameWithoutExtension + ".res")

/** The linker switch that puts [script]'s compiled resources into the executable. */
fun resourceLinkerOption(script: File): String = "-H:NativeLinkerOption=" + compiledResource(script).path
