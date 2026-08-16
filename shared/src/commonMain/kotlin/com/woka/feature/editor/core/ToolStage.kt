package com.woka.feature.editor.core

/**
 * What a tool performs, as a value the fold can collapse — deliberately empty.
 *
 * **This marker is the reason `EditorTool` can live in `commonMain`** (§3.3,
 * feature doc §8 hole 2). The earlier plan had `stage()` return a `RasterStage`
 * directly, which put a graphics type in the shared contract and meant the video
 * editor could not implement a tool without pretending a trim is a matrix. A trim
 * has a stage, and it is not a matrix.
 *
 * `RasterStage` implements this in `jvmMain` (ME-007) because its cases carry
 * Skia types; the video stage joins later.
 *
 * **Not sealed, and it cannot be** — measured 2026-08-16, ME-005 planned it as
 * sealed and ME-007 disproved it. The compiler rejects a `jvmMain` implementation
 * of a `commonMain` sealed interface twice over: *"Extending sealed classes or
 * interfaces from a different module is prohibited"* — the two source sets count
 * as separate modules — and *"a class can only extend a sealed class or interface
 * declared in the same package"*, which `feature/editor/engine/image` is not.
 *
 * Nothing is lost. Exhaustiveness was only ever wanted where the fold branches,
 * and that is over `RasterStage`, which is sealed within its own package. This
 * marker's job is to keep `EditorTool` in `commonMain`, and an open interface does
 * that just as well.
 */
interface ToolStage
