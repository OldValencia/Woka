package io.iris.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.iris.ui.components.FolderCard
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.jetbrains.compose.resources.painterResource
import io.iris.ui.icons.IrisIcons

object HomeDependencies : KoinComponent {
    val viewModel: HomeViewModel by inject()
}

@Composable
fun HomeScreen(
    onNavigateToFolder: (String) -> Unit = {}
) {
    val viewModel = remember { HomeDependencies.viewModel }
    val state by viewModel.state.collectAsState()
    val dockAccessories = io.iris.ui.components.LocalDockAccessories.current

    DisposableEffect(Unit) {
        dockAccessories.setLeft("home") {
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(52.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = Color.Black.copy(alpha = 0.5f),
                        ambientColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .background(IrisTheme.colors.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interactionSource, indication = null) { 
                        val p = io.iris.utils.openDirectoryPicker("Link folder")
                        if (p != null) io.iris.AppDependencies.settingsRepository.addLinkedFolder(p)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(IrisIcons.Static.Add),
                    contentDescription = "Add Linked Folder",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (isHovered) IrisTheme.colors.textPrimary else IrisTheme.colors.textTertiary)
                )
            }
        }
        onDispose { dockAccessories.clearLeft("home") }
    }

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.linkedFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "No linked folders", style = TextStyle(color = IrisTheme.colors.textSecondary))
                }
            } else {
                val gridState = rememberLazyGridState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(240.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        // Paddings from mockup: 20px 40px 120px 40px
                        contentPadding = PaddingValues(top = 20.dp, start = 40.dp, end = 40.dp, bottom = 120.dp)
                    ) {
                        items(state.linkedFolders.size, key = { state.linkedFolders[it] }) { index ->
                            val path = state.linkedFolders[index]
                            FolderCard(
                                path = path,
                                onClick = { onNavigateToFolder(path) },
                                showTitle = state.showTitles,
                                isSelected = false,
                                contextMenuContent = { dismiss ->
                                    io.iris.ui.components.DropdownItem("Open") { dismiss(); onNavigateToFolder(path) }
                                    io.iris.ui.components.DropdownItem("Pin to Library", textColor = IrisTheme.colors.accentFolders) { dismiss(); viewModel.pinFolder(path) }
                                    io.iris.ui.components.DropdownItem("Unlink folder", textColor = IrisTheme.colors.danger) { dismiss(); viewModel.removeLinkedFolder(path) }
                                },
                                onRemove = { viewModel.removeLinkedFolder(path) }
                            )
                        }
                    }
                    
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
                        adapter = rememberScrollbarAdapter(gridState)
                    )
                }
            }
        }
    }
}
