package com.woka.feature.viewer.shell

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

fun copyFileToClipboard(file: File) {
    try {
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: DataFlavor?): Any {
                if (flavor == DataFlavor.javaFileListFlavor) {
                    return listOf(file)
                }
                throw UnsupportedFlavorException(flavor)
            }
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
