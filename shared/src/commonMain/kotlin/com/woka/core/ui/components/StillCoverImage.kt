package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.woka.core.data.needsWokaCover
import com.woka.core.utils.loadStillCover
import java.io.File

/**
 * A grid tile's picture, by whichever road the format needs.
 *
 * Coil for everything it can read, which is most things and gets the memory
 * cache and the fade for free; Woka's own decoders for HEIF, AVIF and TIFF,
 * which Coil is blind to and which used to draw a grey rectangle here.
 *
 * The second road returns an already-scaled, already-disk-cached 400 px
 * preview, so a 36-megapixel HEIC costs a tile the same as a JPEG does — and
 * costs it once ever, across restarts.
 */
@Composable
fun StillCoverImage(file: File, modifier: Modifier = Modifier) {
    if (!file.needsWokaCover) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        return
    }
    var bitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.absolutePath) { bitmap = loadStillCover(file) }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
