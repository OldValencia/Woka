package com.woka.core.utils

import androidx.compose.ui.graphics.toAwtImage
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.copiesAsFileOnly
import com.woka.feature.viewer.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/** Decode and bake on IO; publishing failure leaves the previous clipboard intact. */
actual suspend fun copyImageToClipboard(path: String, frameIndex: Int): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val contents = drawnImageOf(path, frameIndex)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
            val copied = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
            ClipboardCopies.keep(copied.single() as File)
            true
        }.getOrElse { false }
    }

/**
 * Everything the copy does **except** touch the system clipboard, so a test can assert
 * that the picture handed over is the edited one without replacing what the person at
 * the keyboard had copied.
 */
internal fun drawnImageOf(path: String, frameIndex: Int): Transferable {
    val file = File(path)
    val directory = ClipboardCopies.newDirectory()
    try {
        val copy = AppliedStillEdits.copyOf(file, frameIndex, directory)
        // The bake's own pixels when a bake ran (one decode, and none of the copy's JPEG
        // generation); the source through `ImageLoader` when the file goes over as it is.
        val image = when {
            file.copiesAsFileOnly -> null
            else -> (copy.picture ?: ImageLoader.load(file, frameIndex)).toAwtImage()
        }
        if (copy.file == file) directory.delete()
        return ImageOnClipboard(image, copy.file)
    } catch (failure: Throwable) {
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
        throw failure
    }
}

/**
 * AWT maps the image to DIB on Windows; Explorer consumes the accompanying file list.
 * No [image] — a GIF — offers the file list alone, so a chat pastes the animation.
 */
private class ImageOnClipboard(private val image: Image?, private val file: File) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        if (image == null) arrayOf(DataFlavor.javaFileListFlavor)
        else arrayOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
        flavor in getTransferDataFlavors()

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor && image != null) return image
        if (flavor == DataFlavor.javaFileListFlavor) return listOf(file)
        throw UnsupportedFlavorException(flavor)
    }
}
