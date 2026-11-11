package com.woka.integration

import com.woka.app.AppDependencies
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.VideoEditorEngine
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DoviWriteGuardTest {
    @Test
    fun `Done rejects a P5 edit and keeps the whole source byte identical`() = withTempStorageRoot { root ->
        val sample = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", sample.isFile)
        startKoinOnce()
        val file = sample.copyTo(File(root, "p5.mp4"))
        val before = hash(file)
        val registry = EditorToolRegistry(emptyList(), AppDependencies.settingsRepository)
        val engine = VideoEditorEngine(file, registry, EditCapability.Recorded)
        val result = runBlocking { engine.save(EditLayers(EditDocument(listOf(ToolOp("adjust"))))) }
        assertTrue(assertIs<EditSaveResult.Failed>(result).reason.contains("Profile 5"))
        assertContentEquals(before, hash(file))
    }

    private fun hash(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), digest).use { input ->
            input.transferTo(OutputStream.nullOutputStream())
        }
        return digest.digest()
    }
}
