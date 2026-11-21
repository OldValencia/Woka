package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * What Copy hands over for one still: the file for Explorer and chats, and the picture
 * for programs that paste pixels.
 *
 * [picture] is the baked pixels when a bake ran — the clipboard reuses them rather than
 * decoding the file a second time. Null when the file goes over as it is (no edit), and
 * always for a GIF, which goes over as a file only ([copiesAsFileOnly]).
 */
data class StillCopy(val file: File, val picture: ImageBitmap? = null)

/**
 * A GIF is copied as its file and never as a picture: the clipboard's picture is one
 * frame, so a chat would receive a still where the person copied an animation.
 */
val File.copiesAsFileOnly: Boolean get() = extension.equals("gif", ignoreCase = true)
