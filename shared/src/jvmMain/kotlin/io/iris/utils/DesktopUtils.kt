package io.iris.utils

import javax.swing.JFileChooser

actual fun openDirectoryPicker(title: String, initialDirectory: String?): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    if (initialDirectory != null) {
        val dir = java.io.File(initialDirectory)
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
