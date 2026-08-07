package com.woka.core.utils

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser

actual fun openDirectoryPicker(title: String, initialDirectory: String?): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    if (initialDirectory != null) {
        val dir = File(initialDirectory)
        if (dir.exists()) {
            chooser.currentDirectory = dir
        }
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

/**
 * Native AWT [FileDialog] per the desktop-dialog mandate — it is the OS picker,
 * unlike the Swing chooser above. The filter is applied in Kotlin as well as
 * handed to the dialog, because Windows ignores [FilenameFilter].
 */
actual fun openFilePicker(
    title: String,
    extensions: List<String>,
    initialDirectory: String?
): String? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isMultipleMode = false
    if (initialDirectory != null && File(initialDirectory).isDirectory) {
        dialog.directory = initialDirectory
    }
    if (extensions.isNotEmpty()) {
        dialog.file = extensions.joinToString(";") { "*.$it" }
        dialog.filenameFilter = FilenameFilter { _, name ->
            extensions.any { name.endsWith(".$it", ignoreCase = true) }
        }
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val directory = dialog.directory ?: return null
    val picked = File(directory, name)
    val matches = extensions.isEmpty() ||
        extensions.any { name.endsWith(".$it", ignoreCase = true) }
    return if (picked.isFile && matches) picked.absolutePath else null
}

/**
 * `javaFileListFlavor` is the flavour Explorer reads for a paste-as-file; a plain
 * string would paste the path as text instead.
 */
actual fun copyFilesToClipboard(paths: List<String>) {
    val files = paths.map { File(it) }
    if (files.isEmpty()) return
    try {
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> =
                arrayOf(DataFlavor.javaFileListFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
                flavor == DataFlavor.javaFileListFlavor

            override fun getTransferData(flavor: DataFlavor?): Any {
                if (flavor == DataFlavor.javaFileListFlavor) return files
                throw UnsupportedFlavorException(flavor)
            }
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
