package com.woka.feature.editor.save

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.stillDecoderFor
import org.jetbrains.skia.Image
import java.io.File

/** Native pixels through the same recipe as the canvas; shared by save, render and copy. */
internal fun bakeStill(
    file: File,
    frameIndex: Int,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    format: SaveFormat?
): ByteArray = withBakedStill(file, frameIndex, sourceSize, recipe) { encodeStill(it, file, format) }

internal fun <T> withBakedStill(
    file: File,
    frameIndex: Int,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    consume: (Image) -> T
): T {
    val native = displayable(file, stillDecoderFor(file).frame(file, frameIndex))
    return renderAtNativeScale(native, sourceSize, recipe).use(consume)
}

internal fun encodeStill(image: Image, source: File, format: SaveFormat?): ByteArray {
    if (format == null) return encodeSdrAvif(image.toComposeImageBitmap())
    val encoded = image.encodeToData(format.skia, format.quality)?.use { it.bytes }
        ?: error("the ${format.name} encoder produced nothing")
    return if (format.carriesExif) spliceExif(encoded, source) else encoded
}
