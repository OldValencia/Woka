package com.woka.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.floatingPanel
import com.woka.core.ui.theme.Theme
import com.woka.app.AppDependencies
import com.woka.app.navigation.Screen
import com.woka.core.data.isMedia
import java.io.File

/**
 * Files dragged onto the window from Explorer (SIDE-010). The shell's root carries
 * [fileDrops], which reports here; the shell draws [dragging] and acts on [drop].
 *
 * **On the root node, through Compose's own drag-and-drop.** The first cut set an
 * AWT `DropTarget` on the window, and Explorer showed the "not allowed" cursor over
 * the whole app (2026-10-17): Compose installs its own target on the component that
 * draws the content, and that one answered first. Compose's target on the root
 * receives every drop that no deeper node claims, which is all of them.
 */
object WindowDrops {
    /** True while something that carries files is held over the window. */
    var dragging by mutableStateOf(false)

    /**
     * Folders are linked and the first is opened — linking is what dragging a folder
     * onto a library means. With no folder among them, the first media file opens in
     * the viewer over its own folder, as a double-click from Explorer does.
     */
    fun drop(files: List<File>) {
        dragging = false
        val settings = AppDependencies.settingsRepository
        val router = AppDependencies.router
        val folders = files.filter { it.isDirectory }
        if (folders.isNotEmpty()) {
            folders.forEach { settings.addLinkedFolder(it.absolutePath) }
            router.navigateTo(Screen.Folder(folders.first().absolutePath))
            return
        }
        val media = files.firstOrNull { it.isFile && it.isMedia } ?: return
        val parent = media.parentFile ?: return
        router.navigateTo(
            Screen.Folder(
                path = parent.absolutePath,
                openViewerForId = media.absolutePath,
                nonce = System.nanoTime(),
                offerPin = true
            )
        )
    }
}

/** `.dropov`: the whole window lit while files are held over it (SIDE-010). */
@Composable
fun DropOverlay() {
    if (!WindowDrops.dragging) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.accentSoft)
            .border(2.dp, Theme.colors.accent, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            "Drop a folder to link it, or a file to open it",
            modifier = Modifier.floatingPanel().padding(horizontal = 18.dp, vertical = 10.dp),
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** The root's drop target: files in, folders linked, a file opened. `actual` reads the AWT payload. */
@Composable
expect fun Modifier.fileDrops(): Modifier
