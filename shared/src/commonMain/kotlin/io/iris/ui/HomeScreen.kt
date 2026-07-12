package io.iris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.iris.ui.components.FolderCard
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object HomeDependencies : KoinComponent {
    val viewModel: HomeViewModel by inject()
}

@Composable
fun HomeScreen(
    onNavigateToFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val viewModel = remember { HomeDependencies.viewModel }
    val state by viewModel.state.collectAsState()

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
                                showTitle = state.showTitles
                            )
                        }
                    }
                    
                    androidx.compose.foundation.VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(gridState)
                    )
                }
            }
        }
    }
}
