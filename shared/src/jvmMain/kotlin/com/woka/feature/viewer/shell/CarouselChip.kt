package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.MediaItem
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.engine.probe.containerLengthMs
import com.woka.feature.viewer.video.formatTime
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.util.concurrent.ConcurrentHashMap

internal const val CAROUSEL_CHIP_TAG = "carousel-chip"

/** What a carousel tile says about its file, bottom-left (`carouselBadge`, RD-502). */
internal sealed interface CarouselChip {
    /** A single still: nothing to say. */
    data object None : CarouselChip
    data object Music : CarouselChip
    data class Film(val lengthMs: Long) : CarouselChip
    /** An animation, named by its format — `GIF`, `WEBP` (the author, 2026-11-28). */
    data class Animation(val format: String) : CarouselChip
    data class Frames(val count: Int) : CarouselChip
}

/**
 * Per file and modification time, what the probe found. A few bytes each, kept because the
 * lazy row disposes a tile the moment it scrolls off and would open the file again on its
 * way back. A film keeps its *source* length here; its edit is applied on every read.
 */
private val probed = ConcurrentHashMap<String, CarouselChip>()

/**
 * [item]'s chip. A film reads **the result's length** (the author, 2026-11-28) — what the
 * transport's trailing label shows after cuts and speed, `EditedClock.totalMs`.
 */
internal suspend fun chipFor(item: MediaItem): CarouselChip = withContext(ImageBitmapCache.ioDispatcher) {
    val chip = probed.getOrPut("${item.file.absolutePath}|${item.lastModified}") { probe(item) }
    if (chip !is CarouselChip.Film) return@withContext chip
    val clock = AppliedVideoEdits.editOf(item.file)?.clock?.takeIf { it.ranges.isNotEmpty() }
    clock?.let { CarouselChip.Film(it.totalMs) } ?: chip
}

private suspend fun probe(item: MediaItem): CarouselChip = when (item) {
    is MediaItem.Audio -> CarouselChip.Music
    is MediaItem.Video -> CarouselChip.Film(containerLengthMs(item.file))
    is MediaItem.Image -> {
        val set = MediaThumbnails.frameSet(item)
        when {
            set.isAnimated -> CarouselChip.Animation(item.file.extension.uppercase())
            set.viewable.size > 1 -> CarouselChip.Frames(set.viewable.size)
            else -> CarouselChip.None
        }
    }
    else -> CarouselChip.None
}

/**
 * The chip itself (`.media-chip`): 16 dp, `hudScrim` with `onScrim` 9 sp, an 8 dp glyph. The
 * fill is `hudScrim`, which the prototype's `--black-53p3` moved to (§6).
 */
@Composable
internal fun MediaChip(chip: CarouselChip, modifier: Modifier = Modifier) {
    val glyph: DrawableResource? = when (chip) {
        CarouselChip.Music -> Icons.Static.MusicNote
        is CarouselChip.Film -> Icons.Static.PlayFill
        else -> null
    }
    val text = when (chip) {
        is CarouselChip.Film -> formatTime(chip.lengthMs)
        is CarouselChip.Animation -> chip.format
        is CarouselChip.Frames -> chip.count.toString()
        else -> null
    }
    if (glyph == null && text == null) return
    Row(
        modifier = modifier
            .padding(4.dp)
            .height(16.dp)
            .background(Theme.viewer.hudScrim, RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp)
            .testTag(CAROUSEL_CHIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        glyph?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                colorFilter = ColorFilter.tint(Theme.viewer.onScrim)
            )
        }
        text?.let {
            BasicText(it, style = TextStyle(color = Theme.viewer.onScrim, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
