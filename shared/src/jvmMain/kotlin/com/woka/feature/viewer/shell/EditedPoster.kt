package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.AppliedVideoEdits

/**
 * A film's poster through its edit (ME-284), so a graded or marked-up film looks edited on
 * its tile and in the carousel. **Applied after the poster cache, not inside it**: the
 * cached JPEG stays the source's frame, and a `Done` — which changes the film's mtime —
 * re-keys the fold rather than the poster. Anything that is not a film passes through.
 */
internal fun editedPoster(item: MediaItem, poster: ImageBitmap): ImageBitmap {
    if (item !is MediaItem.Video) return poster
    return AppliedVideoEdits.editOf(item.file)?.poster(poster) ?: poster
}
