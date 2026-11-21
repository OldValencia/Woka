package com.woka.core.utils

import androidx.compose.ui.graphics.toAwtImage
import com.woka.core.ui.image.AppliedStillEdits
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
        val copied = AppliedStillEdits.fileForCopy(file, frameIndex, directory)
        // The picture from the source with its recipe — the same pixels as the baked file,
        // without its JPEG generation, and cached under the source's key rather than a
        // temporary copy's that nothing will read again.
        val image = ImageLoader.load(file, frameIndex).toAwtImage()
        if (copied == file) directory.delete()
        return ImageOnClipboard(image, copied)
    } catch (failure: Throwable) {
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
        throw failure
    }
}

/** AWT maps the image to DIB on Windows; Explorer consumes the accompanying file list. */
private class ImageOnClipboard(private val image: Image, private val file: File) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
        flavor == DataFlavor.imageFlavor || flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor) return image
        if (flavor == DataFlavor.javaFileListFlavor) return listOf(file)
        throw UnsupportedFlavorException(flavor)
    }
}
