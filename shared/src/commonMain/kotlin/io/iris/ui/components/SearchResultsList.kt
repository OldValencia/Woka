package io.iris.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource
import java.io.File

@Composable
fun SearchResultsList(
    results: List<File>,
    onResultClick: (String) -> Unit,
    onClose: () -> Unit
) {
    if (results.isEmpty()) return

    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IrisTheme.colors.border))
    
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(results.size) { index ->
            val file = results[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        onResultClick(file.absolutePath)
                        onClose()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(if (file.isDirectory) IrisIcons.Static.Folder else IrisIcons.Static.Image),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    BasicText(
                        text = file.name,
                        style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = file.parentFile?.absolutePath ?: "",
                        style = TextStyle(color = IrisTheme.colors.textTertiary, fontSize = 12.sp)
                    )
                }
            }
        }
    }
}
