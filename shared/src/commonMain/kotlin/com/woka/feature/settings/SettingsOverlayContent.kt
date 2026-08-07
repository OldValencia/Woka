package com.woka.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource

/**
 * The settings card itself: title row, category sidebar and the selected panel.
 * Split out of [SettingsOverlay] per the §2 file-size mandate, which keeps the
 * scrim and the open/close animation in that file and the contents here.
 *
 * [selectedCategory] stays owned by [SettingsOverlay]: held here it would live
 * only as long as the card is composed, so reopening Settings would always land
 * back on Appearance instead of where the user left off.
 */
@Composable
internal fun SettingsOverlayContent(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onClose: () -> Unit,
    settings: WokaSettings,
    repo: SettingsRepository
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "Settings",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.weight(1f))
            CloseIconButton(onClick = onClose)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(140.dp).fillMaxHeight()) {
                CategoryItem("Appearance", Icons.Static.Image, selectedCategory, onSelectCategory)
                CategoryItem("Library", Icons.Static.Folder, selectedCategory, onSelectCategory)
                CategoryItem("Media Viewer", Icons.Static.Eye, selectedCategory, onSelectCategory)
                CategoryItem("Video Player", Icons.Static.Video, selectedCategory, onSelectCategory)
                // Short label: the column is 140.dp and "Picture in Picture"
                // clips. The panel carries the full name as its section heading.
                CategoryItem("PiP", Icons.Static.Collapse, selectedCategory, onSelectCategory)
            }
            Spacer(modifier = Modifier.width(28.dp))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                    label = "CategoryTransition"
                ) { category ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (category) {
                            "Appearance" -> AppearanceSettingsPanel(settings, repo)
                            "Library" -> LibrarySettingsPanel(settings, repo)
                            "Media Viewer" -> MediaViewerSettingsPanel(settings, repo)
                            // Reads the repository itself, so nothing new is
                            // threaded through SettingsOverlay or App.
                            "Video Player" -> VideoPlayerSettingsPanel()
                            "PiP" -> PipSettingsPanel(settings, repo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    name: String,
    icon: DrawableResource,
    selected: String,
    onSelect: (String) -> Unit
) {
    SidebarItem(
        text = name,
        icon = icon,
        isSelected = selected == name,
        onClick = { onSelect(name) }
    )
    Spacer(modifier = Modifier.height(4.dp))
}
