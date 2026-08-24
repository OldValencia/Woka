package com.woka.feature.editor.di

import com.woka.core.data.MediaItem
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.feature.editor.tools.transform.TransformTool
import org.koin.core.qualifier.named
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

    // The whole of adding a tool: one line, and nothing else learns it exists —
    // **with its own qualifier**. Two unqualified `single<EditorTool>` declarations are
    // one definition declared twice: the second replaces the first, and `getAll` then
    // returns a single tool. That is how Transform vanished from the rail the day Crop
    // arrived (2026-08-22).
    single<EditorTool>(named(TransformTool.manifest.id)) { TransformTool }
    single<EditorTool>(named(CropTool.manifest.id)) { CropTool }
    single<EditorTool>(named(AdjustTool.manifest.id)) { AdjustTool }
    single<EditorTool>(named(FilterTool.manifest.id)) { FilterTool }
    single<EditorTool>(named(MarkupTool.manifest.id)) { MarkupTool }
    single<EditorTool>(named(ResizeTool.manifest.id)) { ResizeTool }

    // `getAll` is what makes declaring a tool the only step in joining the set.
    // `single`, because the registry's per-kind flows are built once and every
    // consumer must collect the same ones.
    single { EditorToolRegistry(getAll<EditorTool>(), get()) }

    // The one place that answers "is this file editable" — both Edit entry points
    // resolve it, so neither can grow its own extension list (§4.1).
    single<EditorEngineFactory> { EditorEngineFactoryImpl(get()) }

    // **`createdAtStart`, and that is the whole point of the line.** Every still in the
    // app asks `AppliedStillEdits` whether its file carries an edit, and none of those
    // readers resolves anything from Koin — so a lazy `single` would sit unbuilt and an
    // edited photograph would draw unedited forever (ME-133). `startKoin` builds the
    // eager instances, and building this one installs it.
    single(createdAtStart = true) {
        RecipeStillEdits(get()).also { AppliedStillEdits.install(it) }
    }
}
