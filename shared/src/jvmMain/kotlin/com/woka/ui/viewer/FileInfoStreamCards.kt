package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.theme.Theme
import com.woka.ui.viewer.player.MediaTracks

/** Stream sections for the File Info dialog (§2.5): General / Video / Audio / Subtitles. */
@Composable
fun StreamInfoSections(tracks: MediaTracks, durationMs: Long?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val videos = tracks.video
        InfoSection("General") {
            if (videos.size == 1) videos[0].resolution()?.let { InfoRow("Resolution", it) }
            durationMs?.takeIf { it > 0 }?.let { InfoRow("Duration", formatDuration(it)) }
        }
        if (videos.isNotEmpty()) InfoSection("Video") {
            videos.forEachIndexed { i, v ->
                // Multiple video streams are rare, but when present each one is
                // labelled and carries its own resolution so they don't merge.
                if (videos.size > 1) InfoSubheader(v.label, topGap = i > 0)
                InfoRow("Codec", v.codec.uppercase())
                if (videos.size > 1) v.resolution()?.let { InfoRow("Resolution", it) }
                v.frameRate?.let { InfoRow("Frame rate", formatFps(it)) }
                v.bitRate?.let { InfoRow("Bitrate", formatBitrate(it)) }
            }
        }
        if (tracks.audio.isNotEmpty()) InfoSection("Audio") {
            tracks.audio.forEach { a -> InfoRow(a.label, audioDetail(a)) }
        }
        if (tracks.subtitle.isNotEmpty()) InfoSection("Subtitles") {
            tracks.subtitle.forEach { s -> InfoRow(s.label, subtitleDetail(s)) }
        }
    }
}

@Composable
private fun InfoSection(title: String, rows: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.colors.surfaceHover, RoundedCornerShape(10.dp))
                .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows()
        }
    }
}

@Composable
private fun InfoSubheader(text: String, topGap: Boolean) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = if (topGap) 6.dp else 0.dp),
        style = TextStyle(color = Theme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 12.sp)
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            textAlign = TextAlign.End
        )
    }
}
