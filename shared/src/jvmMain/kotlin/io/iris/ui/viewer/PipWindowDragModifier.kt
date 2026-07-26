package io.iris.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit

fun Modifier.pipWindowDraggable(window: Frame): Modifier = composed {
    var dragStartMouse by remember { mutableStateOf<Point?>(null) }
    var dragStartWin by remember { mutableStateOf<Point?>(null) }

    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed

                if (event.type == PointerEventType.Press && !isCtrl) {
                    dragStartMouse = MouseInfo.getPointerInfo().location
                    dragStartWin = window.location
                }

                if (event.type == PointerEventType.Move && !isCtrl) {
                    val maximizedFlag = (window.extendedState and Frame.MAXIMIZED_BOTH) != 0
                    val gc = window.graphicsConfiguration
                    val screen = gc.bounds
                    val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
                    val fillsScreen =
                        window.width >= screen.width - ins.left - ins.right - 4 &&
                                window.height >= screen.height - ins.top - ins.bottom - 4
                    val isMaximized = maximizedFlag && fillsScreen
                    if (!isMaximized) {
                        val startMouse = dragStartMouse
                        val startWin = dragStartWin
                        if (startMouse != null && startWin != null && event.changes.any { it.pressed }) {
                            val currentMouse = MouseInfo.getPointerInfo().location
                            window.location = Point(
                                startWin.x + (currentMouse.x - startMouse.x),
                                startWin.y + (currentMouse.y - startMouse.y)
                            )
                        }
                    }
                }

                if (event.type == PointerEventType.Release) {
                    dragStartMouse = null
                    dragStartWin = null
                }
            }
        }
    }
}
