package com.woka.core.utils

import androidx.compose.ui.graphics.toAwtImage
import com.woka.feature.viewer.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * `ImageLoader`, so the clipboard gets exactly what the viewer draws: the recipe is
 * folded in on that path and the native bitmap is cached, which usually makes this a
 * cache hit for the picture already on screen.
 *
 * **On the IO dispatcher and knowingly expensive.** `toAwtImage` copies the picture
 * onto the JVM heap — 138 MB for a 36-megapixel photograph — which is why every
 * drawing path in the app avoids it and why this one, an explicit action on one file,
 * can afford it.
 *
 * **Never throws.** A file that cannot be decoded answers `false` and leaves whatever
 * was on the clipboard alone, because a `Copy` that empties the clipboard is worse
 * than one that does nothing.
 */
actual suspend fun copyImageToClipboard(path: String, frameIndex: Int): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val contents = drawnImageOf(path, frameIndex)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
            true
        }.getOrElse { false }
    }

/**
 * Everything the copy does **except** touch the system clipboard, so a test can assert
 * that the picture handed over is the edited one without replacing what the person at
 * the keyboard had copied.
 */
internal fun drawnImageOf(path: String, frameIndex: Int): Transferable =
    ImageOnClipboard(ImageLoader.load(File(path), frameIndex).toAwtImage())

/**
 * One flavour, `imageFlavor`, which is what AWT turns into the platform's own picture
 * format — DIB on Windows — for every program that pastes.
 */
private class ImageOnClipboard(private val image: Image) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
        flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor) return image
        throw UnsupportedFlavorException(flavor)
    }
}
