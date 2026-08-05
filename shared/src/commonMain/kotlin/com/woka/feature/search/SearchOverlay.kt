package com.woka.feature.search

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import java.io.File
import androidx.compose.ui.graphics.TransformOrigin

@Composable
fun SearchOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    linkedFolders: List<String>,
    onResultClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            query = ""
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(tween(240)) + scaleIn(
                    initialScale = 0.3f,
                    transformOrigin = TransformOrigin(0.5f, 1.2f),
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                ) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                ) { it / 2 },
                exit = fadeOut(tween(150)) + scaleOut(
                    targetScale = 0.3f,
                    transformOrigin = TransformOrigin(0.5f, 1.2f),
                    animationSpec = tween(150)
                ) + slideOutVertically(
                    animationSpec = tween(150)
                ) { it / 2 }
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 160.dp)
                        .width(600.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* absorb clicks */ }
                        .shadow(
                            elevation = 48.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.30f),
                            spotColor = Color.Black.copy(alpha = 0.45f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Theme.colors.surfaceElevated)
                        .border(1.dp, Theme.colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SearchInput(query = query, onQueryChange = { query = it })

                        // Results
                        val results = remember(query, linkedFolders) {
                            if (query.isEmpty()) emptyList()
                            else {
                                val matches = mutableListOf<File>()
                                linkedFolders.forEach { root ->
                                    val rootFile = File(root)
                                    if (rootFile.name.contains(query, ignoreCase = true)) matches.add(rootFile)
                                    rootFile.listFiles()?.forEach { child ->
                                        if (child.name.contains(query, ignoreCase = true)) matches.add(child)
                                    }
                                }
                                matches.take(10)
                            }
                        }

                        SearchResultsList(results, onResultClick, onClose)
                    }
                }
            }
        }
    }
}
