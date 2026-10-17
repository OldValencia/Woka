package com.woka.feature.folder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where the keyboard and the pointer are in a folder's grid (SIDE-005).
 *
 * A UI holder, not a ViewModel: it says nothing about the folder, only about what
 * the person in front of it is pointing at, and it is gone with the screen. Owned
 * by `FolderContent` through `remember(path)`, so a new folder starts unfocused.
 *
 * - [focused] is the keyboard's item: what Enter opens and the arrows move from.
 *   A click on a tile sets it, and so does the viewer's last file on its way out.
 * - [hovered] is the pointer's item, so Space previews what is under the cursor —
 *   the gesture Quick Look is named for — before it falls back to [focused].
 * - [quickLook] is whether the preview is up; it shows [focused].
 */
@Stable
class FolderFocus {
    var focused by mutableStateOf<String?>(null)
    var hovered by mutableStateOf<String?>(null)
    var quickLook by mutableStateOf(false)
}
