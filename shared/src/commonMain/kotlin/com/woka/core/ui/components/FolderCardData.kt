package com.woka.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.core.data.mayHoldFrames
import com.woka.core.utils.countFrames
import java.io.File

/**
 * Everything a card draws, resolved from its path.
 *
 * [name] is the raw name; the caption is what shortens it for display.
 */
@Immutable
data class FolderCardData(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val meta: String,
    val previews: List<File>
)

/**
 * Resolves a path into what the card shows. Split out of `FolderCard` so the card
 * itself is left with interaction and layout (§11).
 *
 * Every filesystem call here is behind a `remember(path)` or a `LaunchedEffect`:
 * `isDirectory` and the subtitle's `length()` are stats, and unremembered they
 * re-ran on every hover, selection and highlight pass of every card (§10.3).
 */
@Composable
fun rememberFolderCardData(path: String): FolderCardData {
    val file = remember(path) { File(path) }
    val isDir = remember(path) { file.isDirectory }
    val name = file.name.takeIf { it.isNotEmpty() } ?: path
    val subtitle = remember(path) { getFolderCardSubtitle(isDir, file) }

    var itemCount by remember(path) { mutableStateOf(0) }
    var previews by remember(path) { mutableStateOf<List<File>>(emptyList()) }
    var frameCount by remember(path) { mutableStateOf(1) }

    LaunchedEffect(path) {
        if (isDir) {
            val data = fetchFolderStateData(file)
            itemCount = data.itemCount
            previews = data.previews
        } else if (file.mayHoldFrames) {
            // Only for the formats that can hold a set — every other file would
            // be opened to be told what its name already said.
            frameCount = countFrames(file)
        }
    }

    val meta = when {
        isDir -> folderCardMeta(itemCount)
        // "12 frames" says the same thing about a file that "51 items" says about a
        // folder: there is more inside this than one thing. It joins the size rather
        // than replacing it — the caption has room for both now that it is one line
        // on the cover instead of a badge and a subtitle arguing across it.
        frameCount > 1 -> withFrameCount(subtitle, frameCount)
        else -> subtitle
    }

    return FolderCardData(file, name, isDir, meta, previews)
}
