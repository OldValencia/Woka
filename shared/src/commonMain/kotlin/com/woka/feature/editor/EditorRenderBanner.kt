package com.woka.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.STATUS_SETTLE_MS
import com.woka.core.ui.components.StatusPill
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** The mockup's `.banner .bar`: a short track, so the name stays the loudest thing. */
private val BAR_WIDTH = 120.dp

/**
 * A render's banner (ME-293; `video-editor.html` state 10): the name, a bar, the percent
 * and a ✕ while it runs; then `StatusPill`'s plain answer, which settles like a save's.
 *
 * The running form is not `StatusPill` with a longer string because it is the first
 * banner with a control in it — the cancel — and a pill is a message, not a control.
 */
@Composable
fun EditorRenderBanner() {
    val message = EditorRenderStatus.message
    val fraction = EditorRenderStatus.fraction
    LaunchedEffect(message, fraction) {
        if (message != null && fraction == null) {
            delay(STATUS_SETTLE_MS.milliseconds)
            EditorRenderStatus.clear()
        }
    }
    if (fraction == null || message == null) return StatusPill(message)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .shadow(12.dp, shape)
            .background(Theme.colors.surfaceElevated, shape)
            .border(1.dp, Theme.colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("editor-render-banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BannerText(message, Theme.colors.textPrimary)
        Box(Modifier.width(BAR_WIDTH).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Theme.colors.border)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Theme.colors.accentEditor))
        }
        BannerText("${(fraction * 100).toInt()}%", Theme.colors.textSecondary)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { EditorRenderStatus.cancel() }
                .padding(horizontal = 4.dp)
                .testTag("editor-render-cancel")
        ) {
            BannerText("✕", Theme.colors.textSecondary)
        }
    }
}

@Composable
private fun BannerText(text: String, color: Color) {
    BasicText(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
}
