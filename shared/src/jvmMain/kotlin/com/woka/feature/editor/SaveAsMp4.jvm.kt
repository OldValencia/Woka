package com.woka.feature.editor

import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.engine.video.render.convertToMp4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File

/** Off the UI thread, and cancelled with the job the banner's ✕ cancels. */
internal actual suspend fun convertVideoToMp4(path: String, progress: (Float) -> Unit): EditSaveResult =
    withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        convertToMp4(File(path), { job.isActive }, progress)
    }
