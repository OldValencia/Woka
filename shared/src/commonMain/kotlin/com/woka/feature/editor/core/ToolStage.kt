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
 * Skia types; the video stage joins later. Sealed, so the set of media a tool can
 * describe stays closed and the fold's `when` stays exhaustive — implementations
 * in another source set of the same module are permitted, which is exactly the
 * `commonMain`/`jvmMain` split this relies on.
 */
sealed interface ToolStage
