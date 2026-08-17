package com.woka.feature.editor.di

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.OverwriteConsent
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.tools.transform.TransformTool
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
 * `TransformTool` is the first, added in ME-105 — one line, and nothing else in the
 * app changed to make it appear in the rail, the inspector, the Settings list and the
 * fold's ordering. That was the claim; that is the evidence.
 *
 * JVM-only: the editor engines are Skia and ffmpeg, so `appModule` in `commonMain`
 * cannot name this.
 */
val editorModule = module {
    // `factory`, not `single`: a session is per-open, holds the document being
    // edited, and owns a scope its caller cancels on close (`di.md`).
    factory { (media: MediaItem) -> EditorSession(media) }

    // The whole of adding a tool: one line, and nothing else learns it exists.
    single<EditorTool> { TransformTool }

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
