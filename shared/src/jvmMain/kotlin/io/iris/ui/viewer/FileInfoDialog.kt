package io.iris.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.data.MediaItem
import io.iris.ui.components.dialogs.InformationDialog
import io.iris.ui.theme.IrisTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun FileInfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    InformationDialog(
        title = "File Information",
        onDismiss = onDismiss,
        width = 300.dp,
        closeText = "Close"
    ) {
        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Name: ${item.file.name}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                Text("Path: ${item.file.absolutePath}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                val sizeStr = item.file.length().let { bytes ->
                    when {
                        bytes < 1024 -> "$bytes B"
                        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                    }
                }
                Text("Size: $sizeStr", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                Text("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
            }
        }
    }
}
