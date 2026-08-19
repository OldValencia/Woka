package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.woka.core.data.needsWokaCover
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.rememberReloadKey
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
 *
 * **A file carrying an edit takes the second road too** (ME-133). Coil cannot know
 * about a recipe, and pushing every still off Coil to serve the few that are edited
 * would be a large behavioural change to the whole grid for a small population — so
 * the question is asked per file instead, in the coroutine that already exists here.
 * It costs nothing for a format with no carrier, which answers from its extension
 * without opening anything.
 */
@Composable
fun StillCoverImage(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    // Null while the answer is unknown. **Coil draws in the meantime rather than the
    // tile staying blank**: the check is a few kilobytes off the file's head and
    // resolves in a frame or two, and holding every tile in the grid empty until it
    // returns would make an unedited folder pay for an edited one. The cost is that an
    // edited tile can show its original for those frames.
    var edited by remember(file.absolutePath) { mutableStateOf<Boolean?>(null) }

    // A save moves neither the path nor the `File`, so without this the tile keeps
    // whatever it drew first (ME-135). It also re-asks *which road* the tile takes:
    // the first edit of a photograph moves it from Coil's to Woka's, and a revert
    // moves it back.
    val reloadKey = rememberReloadKey(file.absolutePath)

    LaunchedEffect(file.absolutePath, reloadKey) {
        val wokaRoad = file.needsWokaCover || AppliedStillEdits.carries(file)
        edited = wokaRoad
        if (wokaRoad) {
            bitmap = loadStillCover(file)
        }
    }

    val drawn = bitmap
    if (edited == true) {
        drawn?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        return
    }
    if (edited == null && file.needsWokaCover) {
        // A format Coil cannot read at all: nothing is better than a broken tile.
        return
    }
    // `key`, because `model = file` compares equal across a write: Coil holds the
    // painter it already resolved and never asks again, so evicting its memory entry
    // alone leaves the pre-save picture on the tile. A new key is a new request.
    key(reloadKey) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
