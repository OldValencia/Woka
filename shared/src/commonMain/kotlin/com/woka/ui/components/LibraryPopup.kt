package com.woka.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.woka.AppDependencies
import com.woka.data.PinnedSession
import com.woka.data.SettingsRepository
import com.woka.navigation.Screen
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryPopup(
    pinnedSessions: List<PinnedSession>,
    settingsRepo: SettingsRepository,
    onHoverEnter: () -> Unit,
    onHoverExit: () -> Unit
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -35)
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .width(220.dp)
                .shadow(24.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                .background(Theme.colors.surfaceElevated, RoundedCornerShape(12.dp))
                .border(1.dp, Theme.colors.border, RoundedCornerShape(12.dp))
                .onPointerEvent(PointerEventType.Enter) { onHoverEnter() }
                .onPointerEvent(PointerEventType.Exit) { onHoverExit() }
                .padding(6.dp)
        ) {
            BasicText(
                text = "PINNED SESSIONS",
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            
            pinnedSessions.forEach { session ->
                val file = java.io.File(session.path)
                val name = file.name.takeIf { it.isNotEmpty() } ?: session.path
                
                val src = remember { MutableInteractionSource() }
                val hov by src.collectIsHoveredAsState()
                val bg by animateColorAsState(if (hov) Theme.colors.surfaceHover else Color.Transparent)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = src, indication = null) { 
                            AppDependencies.router.navigateTo(Screen.Folder(session.path))
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = name,
                        style = TextStyle(color = Theme.colors.textPrimary, fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (hov) {
                        Image(
                            painter = painterResource(Icons.Static.Close),
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { settingsRepo.removePinnedSession(session.path) }
                                .padding(2.dp),
                            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
                        )
                    }
                }
            }
        }
    }
}
