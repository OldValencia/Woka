package io.iris.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.iris.data.MediaItem
import io.iris.ui.theme.IrisTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun FileInfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .shadow(24.dp, RoundedCornerShape(12.dp))
                .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .width(300.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("File Information", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                
                SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Name: ${item.file.name}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                        Text("Path: ${item.file.absolutePath}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                        Text("Size: ${item.file.length() / 1024} KB", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                        Text("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}", modifier = Modifier.fillMaxWidth(), style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp))
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Close", style = TextStyle(color = IrisTheme.colors.accentFolders, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}
