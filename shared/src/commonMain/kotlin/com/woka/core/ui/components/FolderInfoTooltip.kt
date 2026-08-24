package com.woka.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How much is in a folder, on hover over the breadcrumb that names it.
 *
 * On the breadcrumb rather than on the grid tile: a tile already says its count in
 * its caption, and the question "how big is this folder" is asked about the one you
 * are standing in, not about each of the twelve you can see. This also keeps the
 * answer out of the top bar, where it would be a line held up permanently.
 *
 * [overflowName] is the folder's full name, and **only** when the breadcrumb had to
 * ellipsise it — otherwise the pill is already showing it and repeating it here
 * would be a second copy of a word the cursor is pointing at.
 *
 * `TooltipArea` listens for hover and nothing else, so a click still reaches the
 * breadcrumb underneath and navigates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderInfoTooltip(
    file: File,
    overflowName: String?,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = { FolderInfoCard(file, overflowName) },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(10.dp, 20.dp)),
        content = content
    )
}

@Composable
private fun FolderInfoCard(file: File, overflowName: String?) {
    // Composed only while the tooltip is up, which is what keeps the walk off every
    // breadcrumb the bar happens to be drawing.
    var stats by remember(file.absolutePath) { mutableStateOf<FolderInfo?>(null) }
    LaunchedEffect(file.absolutePath) {
        stats = folderInfo(file)
    }

    Column(
        modifier = Modifier
            .shadow(16.dp, RoundedCornerShape(9.dp), spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Theme.colors.surfaceElevated, RoundedCornerShape(9.dp))
            .border(1.dp, Theme.colors.borderGlow, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        if (overflowName != null) {
            BasicText(
                text = overflowName,
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        BasicText(
            text = stats.describe(),
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
        )
    }
}

private data class FolderInfo(val itemCount: Int, val bytes: Long)

/** The line is one thing, so it waits for both halves rather than growing in place. */
private fun FolderInfo?.describe(): String = when {
    this == null -> "Reading…"
    itemCount == 0 -> "Empty"
    itemCount == 1 -> "1 item · ${formatFileSize(bytes)}"
    else -> "$itemCount items · ${formatFileSize(bytes)}"
}

private val folderInfoCache = ConcurrentHashMap<String, Pair<Long, FolderInfo>>()

/**
 * Item count and bytes under a folder, subfolders included. The count matches what
 * a card's caption says — folders and media, not every stray file — so the two
 * never disagree about the same directory.
 *
 * Cached against the directory's own mtime: the walk is the expensive half and
 * hovering the same breadcrumb twice must not pay for it twice.
 */
private suspend fun folderInfo(file: File): FolderInfo = withContext(Dispatchers.IO) {
    val key = file.absolutePath
    val stamp = file.lastModified()
    folderInfoCache[key]?.let { (cached, info) ->
        if (cached == stamp) return@withContext info
    }
    val count = fetchFolderStateData(file).itemCount
    val bytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    FolderInfo(count, bytes).also { folderInfoCache[key] = stamp to it }
}
