package io.iris.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource

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
                ) { onClose() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.padding(bottom = 110.dp)) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(tween(240)) + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
                ) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
                ) { it / 16 },
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .width(440.dp)
                        .wrapContentHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* absorb clicks so the scrim doesn't close the sheet */ }
                        .shadow(
                            elevation = 48.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color.Black.copy(alpha = 0.30f),
                            spotColor = Color.Black.copy(alpha = 0.45f)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(IrisTheme.colors.surfaceElevated)
                        .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(20.dp))
                        .padding(28.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                text = "Settings",
                                style = TextStyle(
                                    color = IrisTheme.colors.textPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            CloseIconButton(onClick = onClose)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        SectionLabel("Appearance")
                        Spacer(modifier = Modifier.height(10.dp))
                        SegmentedControl(
                            options = listOf("System", "Light", "Dark"),
                            selected = currentTheme,
                            onSelect = onThemeChange
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        SectionLabel("Library")
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsToggleRow(
                            title = "Show Item Titles",
                            subtitle = "Display file names under thumbnails everywhere",
                            checked = globalShowTitles,
                            onCheckedChange = onGlobalShowTitlesChange
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = TextStyle(
            color = IrisTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
}



@Composable
private fun CloseIconButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) IrisTheme.colors.surfaceHover else Color.Transparent,
        animationSpec = tween(140)
    )
    val rotation by animateFloatAsState(
        targetValue = if (isHovered) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(IrisIcons.Static.Close),
            contentDescription = "Close settings",
            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rotation },
            colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
        )
    }
}
