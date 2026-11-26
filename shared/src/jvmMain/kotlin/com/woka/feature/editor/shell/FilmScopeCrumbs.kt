package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ClipScope
import org.jetbrains.compose.resources.painterResource
import java.util.Locale

internal const val SCOPE_WHOLE_TAG = "editor-scope-whole"

internal const val SCOPE_CLIP_TAG = "editor-scope-clip"

private const val WHOLE_VIDEO = "Whole video"

private val CRUMB_SHAPE = RoundedCornerShape(7.dp)

/**
 * A film's scope line (RD-204, `.scope .crumb`): `Whole video` alone, or `Whole video ›
 * Clip 2  2.4 s` with a clip selected, where `Whole video` takes the selection back (ME-221).
 */
@Composable
internal fun FilmScopeCrumbs(clip: ClipScope?, onWholeVideo: () -> Unit) {
    val room = Theme.room
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (clip == null) {
            CurrentCrumb(WHOLE_VIDEO, Modifier)
            return@Row
        }
        WholeVideoCrumb(onWholeVideo)
        Image(
            painter = painterResource(Icons.Static.ChevronRightMac),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(room.faint)
        )
        CurrentCrumb("Clip ${clip.number}", Modifier.testTag(SCOPE_CLIP_TAG), meta = secondsOf(clip.lengthMs))
    }
}

@Composable
private fun WholeVideoCrumb(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    BasicText(
        text = WHOLE_VIDEO,
        modifier = Modifier
            .heightIn(min = 26.dp)
            .clip(CRUMB_SHAPE)
            .background(if (hovered) room.hoverStrong else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .testTag(SCOPE_WHOLE_TAG),
        style = TextStyle(color = if (hovered) room.text else room.muted, fontSize = 13.sp)
    )
}

/** The crumb the inspector speaks for: semibold, its length beside it in mono. */
@Composable
private fun CurrentCrumb(label: String, modifier: Modifier, meta: String? = null) {
    val room = Theme.room
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
        if (meta != null) {
            withStyle(SpanStyle(color = room.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)) {
                append("  $meta")
            }
        }
    }
    BasicText(
        text = text,
        modifier = modifier.padding(horizontal = 7.dp, vertical = 5.dp),
        style = TextStyle(color = room.text, fontSize = 13.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** `2.4 s` — the prototype's one decimal, a point whatever the locale. */
internal fun secondsOf(lengthMs: Long): String = String.format(Locale.ROOT, "%.1f s", lengthMs / 1000.0)
