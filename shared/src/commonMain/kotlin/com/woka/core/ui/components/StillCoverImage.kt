package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
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
fun StillCoverImage(
    file: File,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    /**
     * What to draw when this cannot draw a picture (2026-08-30, found on a screenshot).
     *
     * **Three branches below draw nothing at all**: a Woka-road file whose decode
     * answered null, a Woka-road file whose road has not been decided yet, and a
     * decode still running. Each leaves the caller's surface bare, which on a folder
     * card is a black tile — and the folder glyph beside it is *not* drawn in that
     * case, because the card has a cover and simply cannot paint it.
     *
     * Defaulted to nothing, so every existing caller draws exactly what it drew: a
     * bare tile is right for a photograph in a grid of photographs, and wrong for the
     * one card that has something else to say.
     */
    placeholder: (@Composable () -> Unit)? = null,
    /** `Fit` for a whole picture, as Quick Look shows one (SIDE-005); a cover crops. */
    contentScale: ContentScale = ContentScale.Crop
) {
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
        if (drawn == null) {
            // The decode is running, or it answered null and there is no picture to
            // be had. Either way this tile has nothing of the file to show.
            CoverPlaceholder(placeholder)
            return
        }
        Image(
            bitmap = drawn,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
            colorFilter = colorFilter
        )
        return
    }
    if (edited == null && file.needsWokaCover) {
        // A format Coil cannot read at all: nothing is better than a broken tile.
        CoverPlaceholder(placeholder)
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
            contentScale = contentScale,
            colorFilter = colorFilter
        )
    }
}

/**
 * A stand-in laid out where the picture would have been: filling the tile, centred.
 *
 * Centred here rather than at the call site, because the picture it replaces filled
 * the tile and a glyph left at a parent `Box`'s default alignment lands in a corner
 * and reads as a mistake.
 */
@Composable
private fun CoverPlaceholder(content: (@Composable () -> Unit)?) {
    if (content == null) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
