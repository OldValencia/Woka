package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * What a card looks like: the 16:10 cover with its preview, badge and hover-only
 * remove button, and the title beneath. Split out of `FolderCard`, which is left
 * holding interaction and the context menu (§11).
 */
@Composable
internal fun FolderCardBody(
    data: FolderCardData,
    style: FolderCardStyle,
    showTitle: Boolean,
    isHovered: Boolean,
    onRemove: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(
                    elevation = style.shadowElev,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = style.glowColor,
                    ambientColor = style.ambientColor
                )
                .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(style.borderWidth, style.borderColor, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            FolderCardPreview(
                isDir = data.isDir,
                file = data.file,
                previews = data.previews,
                modifier = Modifier.fillMaxSize()
            )

            FolderCardBadge(
                isDir = data.isDir,
                dynamicBadgeText = data.badge,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            if (onRemove != null && isHovered) {
                FolderCardRemoveButton(
                    onRemove = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        if (showTitle) {
            FolderCardTitle(
                name = data.name,
                subtitleText = data.subtitle,
                titleColor = style.titleColor
            )
        }
    }
}
