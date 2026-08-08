package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File
import com.woka.core.utils.generateVideoThumbnail

/**
 * What a card shows above its title. The two branches share nothing but the
 * surface behind them, so the folder side lives in [FolderCoverMosaic] (§11).
 */
@Composable
fun FolderCardPreview(
    isDir: Boolean,
    file: File,
    previews: List<File>,
    modifier: Modifier = Modifier
) {
    if (isDir) {
        Box(
            modifier = modifier.fillMaxSize().background(Theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            FolderCoverMosaic(previews)
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(Theme.colors.surfaceElevated)) {
            val isVideo = file.mediaKind() == MediaKind.Video

            if (isVideo) {
                VideoCover(file)
                PlayBadge(modifier = Modifier.align(Alignment.Center))
            } else {
                StillCoverImage(file)
            }
        }
    }
}

/** Poster frame for a video, with the video glyph standing in until it decodes. */
@Composable
private fun BoxScope.VideoCover(file: File) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        bitmap = generateVideoThumbnail(file)
    }

    val frame = bitmap
    if (frame != null) {
        Image(
            bitmap = frame,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(Icons.Static.Video),
            contentDescription = null,
            modifier = Modifier.size(64.dp).align(Alignment.Center),
            alpha = 0.5f,
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
        )
    }
}

@Composable
private fun PlayBadge(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.PlayFill),
            contentDescription = "Video",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(24.dp)
        )
    }
}
