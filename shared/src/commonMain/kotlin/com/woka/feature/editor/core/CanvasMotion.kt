package com.woka.feature.editor.core

/**
 * A whole-picture movement an operation made: a turn by [degrees] (clockwise positive) and a
 * mirror on either axis. The canvas plays it backwards into the new picture (RD-203).
 */
data class CanvasMotion(
    val degrees: Float = 0f,
    val flipX: Boolean = false,
    val flipY: Boolean = false
)
