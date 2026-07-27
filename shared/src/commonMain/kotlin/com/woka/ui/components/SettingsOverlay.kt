package com.woka.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.data.CarouselVisibility
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme

@Composable
fun SettingsOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    globalShowTitles: Boolean,
    onGlobalShowTitlesChange: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onShowHiddenFilesChange: (Boolean) -> Unit,
    carouselMode: CarouselVisibility,
    onCarouselModeChange: (CarouselVisibility) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Appearance") }
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
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .animateEnterExit(
                        enter = fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.3f,
                            transformOrigin = TransformOrigin(0.65f, 1.2f),
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                        ) + slideInVertically(
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                        ) { it / 2 },
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.3f,
                            transformOrigin = TransformOrigin(0.65f, 1.2f),
                            animationSpec = tween(150)
                        ) + slideOutVertically(
                            animationSpec = tween(150)
                        ) { it / 2 }
                    )
                    .width(560.dp)
                    .height(420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb clicks */ }
                    .shadow(
                        elevation = 48.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(Theme.colors.surfaceElevated)
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(20.dp))
                    .padding(28.dp)
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
                        // Sidebar
                        Column(modifier = Modifier.width(140.dp).fillMaxHeight()) {
                            SidebarItem(
                                text = "Appearance",
                                icon = Icons.Static.Image,
                                isSelected = selectedCategory == "Appearance",
                                onClick = { selectedCategory = "Appearance" }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SidebarItem(
                                text = "Library",
                                icon = Icons.Static.Folder,
                                isSelected = selectedCategory == "Library",
                                onClick = { selectedCategory = "Library" }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SidebarItem(
                                text = "Media Viewer",
                                icon = Icons.Static.Eye,
                                isSelected = selectedCategory == "Media Viewer",
                                onClick = { selectedCategory = "Media Viewer" }
                            )
                        }
                        Spacer(modifier = Modifier.width(28.dp))
                        // Content
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                                },
                                label = "CategoryTransition"
                            ) { category ->
                                Column(modifier = Modifier.fillMaxSize()) {
                                    when (category) {
                                        "Appearance" -> {
                                            AppearanceSettingsPanel(currentTheme, onThemeChange)
                                        }
                                        "Library" -> {
                                            LibrarySettingsPanel(
                                                globalShowTitles,
                                                onGlobalShowTitlesChange,
                                                showHiddenFiles,
                                                onShowHiddenFilesChange
                                            )
                                        }
                                        "Media Viewer" -> {
                                            MediaViewerSettingsPanel(carouselMode, onCarouselModeChange)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
