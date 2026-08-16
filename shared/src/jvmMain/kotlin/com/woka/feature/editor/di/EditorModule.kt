package com.woka.feature.editor.di

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.OverwriteConsent
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
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
 * No tool is declared yet — the first arrives with the transform tool in ME-105.
 * The registry below is already wired to collect them, so that ticket adds a line
 * here and touches nothing else.
 *
 * JVM-only: the editor engines are Skia and ffmpeg, so `appModule` in `commonMain`
 * cannot name this.
 */
val editorModule = module {
    // `factory`, not `single`: a session is per-open, holds the document being
    // edited, and owns a scope its caller cancels on close (`di.md`).
    factory { (media: MediaItem) -> EditorSession(media) }

    // `getAll` is what makes declaring a tool the only step in joining the set.
    // `single`, because the registry's per-kind flows are built once and every
    // consumer must collect the same ones.
    single { EditorToolRegistry(getAll<EditorTool>(), get()) }

    // The one place that answers "is this file editable" — both Edit entry points
    // resolve it, so neither can grow its own extension list (§4.1).
    single<EditorEngineFactory> { EditorEngineFactoryImpl(get()) }

    // `single`, so "don't ask again" holds for the run of the app rather than for
    // one file. It is in memory only, by design (§5).
    single { OverwriteConsent() }
}
