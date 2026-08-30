package com.woka.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaKind
import com.woka.core.data.WatchRecord
import com.woka.core.data.mediaKind
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File

/** Saturation and darkening a cover sits at until the cursor is on it. */
private const val REST_SATURATION = 0.62f
private const val REST_DIM = 0.18f

/** The stand-in a folder draws when it has no cover — see [FolderCardPreview]. */
const val FOLDER_GLYPH_TAG = "folder-glyph"

/**
 * What a card shows under its caption: **one** cover, calm until hovered.
 *
 * One, because the four-tile mosaic this used to draw for folders multiplied six
 * folders into twenty-four competing photographs — nothing was background, so
 * nothing was foreground either. "There is more inside" is now said by the stacked
 * edge under the tile and by the caption's count.
 *
 * Calm, because a grid of covers at full saturation has no quiet surface to rest
 * on. The rest state is one `ColorFilter` plus one black overlay; darkening with an
 * overlay rather than the image's own alpha is deliberate, since alpha over the
 * light theme's surface would *brighten* the picture instead.
 */
@Composable
fun FolderCardPreview(
    isDir: Boolean,
    file: File,
    previews: List<File>,
    modifier: Modifier = Modifier,
    isHovered: Boolean = false,
    // Null for anything that is not a started video, which is every folder, every
    // photograph, and every video not yet opened.
    watch: WatchRecord? = null,
    dimWatched: Boolean = false
) {
    val saturation by animateFloatAsState(
        targetValue = if (isHovered) 1f else REST_SATURATION,
        animationSpec = tween(220)
    )
    val restDim by animateFloatAsState(
        targetValue = if (isHovered) 0f else REST_DIM,
        animationSpec = tween(220)
    )
    val calm = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    val isVideo = remember(file) { !isDir && file.mediaKind() == MediaKind.Video }

    val cover = previews.firstOrNull()

    Box(modifier = modifier.fillMaxSize().background(Theme.colors.surfaceElevated)) {
        when {
            // The glyph is this cover's placeholder as well as its absence: a borrowed
            // picture that will not decode used to leave the tile black, because a card
            // *with* a cover never reached the branch below (found on a screenshot,
            // 2026-08-30). Drawn under the dimming here and again above it there —
            // whichever fires, exactly one of them does.
            isDir -> cover?.let {
                StillCoverImage(it, colorFilter = calm, placeholder = { FolderGlyph() })
            }
            isVideo -> VideoCover(file, dim = dimWatched && watch?.isStarted == true, calm = calm)
            else -> StillCoverImage(file, colorFilter = calm)
        }

        if (restDim > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = restDim)))
        }

        // Above the rest-state dimming on purpose: the glyph and the progress bar
        // are chrome, and a card the cursor is not on still has to be readable.
        //
        // **The folder glyph belongs here too**, and used to be drawn with the cover
        // instead — so an empty folder's only mark sat at 0.5 alpha *under* another
        // 18 % of black, on a dark elevated surface. Reported by hand 2026-08-30 as
        // a linked folder drawing "a black tile with nothing on it" (§ folder cover).
        if (isDir && cover == null) FolderGlyph(modifier = Modifier.align(Alignment.Center))
        if (isVideo) {
            PlayBadge(modifier = Modifier.align(Alignment.Center))
            FolderCardProgress(watch)
        }
    }
}

/** What a folder shows when it has no picture to show — its own outline. */
@Composable
private fun FolderGlyph(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Icons.Static.Folder),
        contentDescription = null,
        modifier = modifier.size(64.dp).testTag(FOLDER_GLYPH_TAG),
        alpha = 0.5f,
        colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
    )
}
