package com.woka.feature.home

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.FolderCard
import com.woka.core.ui.components.GridDensityHud
import com.woka.core.ui.components.gridColumnSize
import com.woka.core.ui.theme.Theme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import com.woka.core.ui.components.DropdownItem
import com.woka.app.shell.LocalDockAccessories

object HomeDependencies : KoinComponent {
    val viewModel: HomeViewModel by inject()
}

@Composable
fun HomeScreen(
    onNavigateToFolder: (String) -> Unit = {}
) {
    val viewModel = remember { HomeDependencies.viewModel }
    val state by viewModel.state.collectAsState()
    val dockAccessories = LocalDockAccessories.current
    val menu = remember { HomeMenuState() }

    HomeTopBarSetup(folderCount = state.linkedFolders.size)

    DisposableEffect(dockAccessories) {
        dockAccessories.setInline("home") { HomeAddButton() }
        onDispose { dockAccessories.clearInline("home") }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.colors.background)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // On the whole area, not on the grid: right-clicking an empty Home
                // is the one place the "Link folder" offer is most wanted.
                .homeGridInput(
                    layout = state.layout,
                    onLayoutChange = { viewModel.setLayout(it) },
                    onBackgroundMenu = { menu.open(it) }
                )
        ) {
            if (state.linkedFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "No linked folders", style = TextStyle(color = Theme.colors.textSecondary))
                }
            } else {
                val gridState = rememberLazyGridState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(state.layout.gridColumnSize),
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
                                    DropdownItem("Open") { dismiss(); onNavigateToFolder(path) }
                                    DropdownItem("Pin to Library", textColor = Theme.colors.accentFolders) { dismiss(); viewModel.pinFolder(path) }
                                    DropdownItem("Unlink folder", textColor = Theme.colors.danger) { dismiss(); viewModel.removeLinkedFolder(path) }
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

            GridDensityHud(state.layout, modifier = Modifier.align(Alignment.Center))

            HomeContextMenu(
                menu = menu,
                layout = state.layout,
                onLayoutChange = { viewModel.setLayout(it) }
            )
        }
    }
}
