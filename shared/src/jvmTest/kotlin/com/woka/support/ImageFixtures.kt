package com.woka.support

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Large enough that `StillPreview` has something to scale, small enough to be free. */
private const val SIDE = 64

/**
 * Real encoded pictures, written where a test wants one.
 *
 * Generated rather than committed, which is what `test.md` asks for where generation
 * is cheap — and a generated fixture states what it contains, so a failure blames the
 * right thing.
 *
 * They exist because junk bytes stopped being good enough: the folder cover now asks
 * what a picture *contains*, and a file that cannot be decoded is a different case
 * from one that decodes to nothing.
 */
fun imageFile(dir: File, name: String, pixel: (Int, Int) -> Int): File {
    dir.mkdirs()
    val target = File(dir, name)
    val format = name.substringAfterLast('.')
    // JPEG carries no alpha, so a transparent fixture has to be a PNG.
    val type = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    val image = BufferedImage(SIDE, SIDE, type)
    for (y in 0 until SIDE) {
        for (x in 0 until SIDE) image.setRGB(x, y, pixel(x, y))
    }
    ImageIO.write(image, format, target)
    return target
}

/** A gradient: any two samples of it disagree, which is what a photograph does. */
fun variedImage(dir: File, name: String): File = imageFile(dir, name) { x, y ->
    0xFF000000.toInt() or (x * 4 shl 16) or (y * 4 shl 8) or (x + y)
}

/** Decodes perfectly, paints nothing — the picture behind the 2026-08-30 report. */
fun transparentImage(dir: File, name: String): File = imageFile(dir, name) { _, _ -> 0 }

/** One colour everywhere: nothing to look at, without the transparency. */
fun flatImage(dir: File, name: String, argb: Int): File = imageFile(dir, name) { _, _ -> argb }
