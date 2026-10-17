package com.woka.app.shell

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Compose's drag-and-drop target, reading the AWT payload a drag from Explorer
 * carries. Anything that is not a file list is not started on, so text dragged over
 * the window lights nothing and shows the "not allowed" cursor, as it should.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.fileDrops(): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                WindowDrops.dragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                WindowDrops.dragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                WindowDrops.dragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    .orEmpty()
                    .filterIsInstance<File>()
                WindowDrops.drop(files)
                return files.isNotEmpty()
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        },
        target = target
    )
}
