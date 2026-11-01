package com.woka.app.shell.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File

/**
 * `.cover`: the source's own picture, cropped square — a PiP video's poster crops its
 * middle — with a 1 dp ring. Until the picture arrives, or when there is none, the
 * video glyph — a note for a track — on a raised surface, as `VideoCover` falls back.
 */
@Composable
internal fun NowPlayingCover(
    source: NowPlayingSource,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val cover by produceState<ImageBitmap?>(null, source.path) { value = source.cover() }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Theme.colors.surfaceElevated)
            .border(1.dp, Theme.colors.border, shape),
        contentAlignment = Alignment.Center
    ) {
        val picture = cover
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val audio = File(source.path).mediaKind() == MediaKind.Audio
            Image(
                painter = painterResource(if (audio) Icons.Static.MusicNote else Icons.Static.Video),
                contentDescription = null,
                modifier = Modifier.size(size * 0.46f),
                colorFilter = ColorFilter.tint(Theme.colors.textTertiary)
            )
        }
    }
}
