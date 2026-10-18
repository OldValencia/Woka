package com.woka.feature.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val READONLY_MEDIA_EDIT_NOTICE = "Editing is not supported for this format"

/**
 * Transient notice banner for editor actions (e.g. attempting to edit read-only formats).
 */
@Stable
object EditorNoticeStatus {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String = READONLY_MEDIA_EDIT_NOTICE) {
        message = text
    }

    fun clear() {
        message = null
    }
}
