package io.iris.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.iris.ui.theme.IrisTheme

@Composable
fun SettingsOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    globalShowTitles: Boolean,
    onGlobalShowTitlesChange: (Boolean) -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .wrapContentHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Block clicks */ }
                        .clip(RoundedCornerShape(12.dp))
                        .background(IrisTheme.colors.surface)
                        .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        BasicText(
                            text = "Settings",
                            style = TextStyle(
                                color = IrisTheme.colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        BasicText(
                            text = "Appearance",
                            style = TextStyle(
                                color = IrisTheme.colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IrisTheme.colors.background)
                                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("System", "Light", "Dark").forEach { themeName ->
                                val isSelected = currentTheme == themeName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) IrisTheme.colors.surface else Color.Transparent)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onThemeChange(themeName) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = themeName,
                                        style = TextStyle(
                                            color = if (isSelected) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        BasicText(
                            text = "Library",
                            style = TextStyle(
                                color = IrisTheme.colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IrisTheme.colors.background)
                                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                text = "Show Item Titles globally",
                                style = TextStyle(
                                    color = IrisTheme.colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            IosSwitch(
                                checked = globalShowTitles,
                                onCheckedChange = onGlobalShowTitlesChange
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IrisTheme.colors.background)
                                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onClose() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "Close",
                                style = TextStyle(
                                    color = IrisTheme.colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
