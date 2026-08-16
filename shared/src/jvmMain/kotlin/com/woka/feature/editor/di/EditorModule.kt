package com.woka.feature.editor.di

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditorSession
import org.koin.dsl.module

/**
 * **The single place a tool is declared** (feature doc §3.4).
 *
 * Adding an editing tool is one directory under `feature/editor/tools/` and one
 * `single<EditorTool> { … }` line here. Nothing else learns the tool exists: the
 * rail, the inspector, the shortcut table and the Settings row are all derived
 * from its manifest, and the registry is built from `getAll<EditorTool>()` — so
 * joining the set is that one line, and leaving it is deleting that one line.
 *
 * Empty until ME-005 defines `EditorTool` and ME-006 builds the registry. It is
 * created now, and composed into `platformModule`, so that the seam exists before
 * anything needs it — a module added later is a module every call site has to be
 * taught about.
 *
 * JVM-only: the editor engines are Skia and ffmpeg, so `appModule` in `commonMain`
 * cannot name this.
 */
val editorModule = module {
    // `factory`, not `single`: a session is per-open, holds the document being
    // edited, and owns a scope its caller cancels on close (`di.md`).
    factory { (media: MediaItem) -> EditorSession(media) }
}
