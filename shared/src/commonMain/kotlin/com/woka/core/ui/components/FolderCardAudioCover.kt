package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.loadAudioCover
import org.jetbrains.compose.resources.painterResource
import java.io.File

/**
 * A track's tile (AP-003, `audio-player.html`): its cover cropped square, or a note
 * glyph where it has none — the caption under the card already carries the name.
 * No hover reel: there is nothing to play silently.
 */
@Composable
internal fun BoxScope.AudioTrackCover(file: File, calm: ColorFilter) {
    val cover by produceState<ImageBitmap?>(null, file.absolutePath) { value = loadAudioCover(file) }
    val picture = cover
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = calm
        )
    } else {
        Image(
            painter = painterResource(Icons.Static.MusicNote),
            contentDescription = null,
            modifier = Modifier.size(56.dp).align(Alignment.Center),
            alpha = 0.5f,
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
        )
    }
}
