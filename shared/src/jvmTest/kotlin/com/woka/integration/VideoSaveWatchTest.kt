package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.probe.generateClip
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ME-285: a `Done` that changes how long a film is leaves it not watched — no resume point,
 * no mark. A look changes nothing about where the film was watched to, and keeps the record.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoSaveWatchTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a saved edit forgets where the film was watched to`() = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-watch").toFile()
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 3, gop = 15)
            val settings = SettingsRepositoryImpl()
            settings.setWatchPosition(film.absolutePath, 2_000L, 3_000L)
            assertNotNull(settings.settings.value.watchRecords[film.absolutePath])

            val engine = assertNotNull(EditorEngineFactoryImpl(EditorToolRegistry(listOf(TrimTool), settings)).create(film.path, 0))
            val look = EditDocument(listOf(ToolOp("adjust", ParamSet())))
            assertIs<EditSaveResult.Saved>(runBlocking { engine.save(EditLayers(look)) })
            assertNotNull(settings.settings.value.watchRecords[film.absolutePath], "a look reset the record")

            val cut = EditDocument(look.ops + TrimTool.opFor(listOf(TrimClip("a", 0L, 1_000L))))
            assertIs<EditSaveResult.Saved>(runBlocking { engine.save(EditLayers(cut)) })

            assertNull(settings.settings.value.watchRecords[film.absolutePath], "the old resume point survived the edit")
        } finally {
            dir.deleteRecursively()
        }
    }
}
