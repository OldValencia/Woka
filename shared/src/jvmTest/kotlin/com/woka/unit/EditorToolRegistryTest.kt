package com.woka.unit

import com.woka.core.data.MediaKind
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.ToolStage
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private class StubTool(
    id: String,
    domain: ToolDomain,
    order: Int,
    supports: Set<MediaKind> = setOf(MediaKind.Image, MediaKind.Video)
) : EditorTool {
    override val manifest = ToolManifest(
        id = id,
        title = id,
        summary = id,
        icon = Icons.Static.Close,
        domain = domain,
        order = order,
        supports = supports
    )
    override val params: List<ParamSpec<*>> = emptyList()
    override fun stage(params: ParamSet): ToolStage? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditorToolRegistryTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val crop = StubTool("crop", ToolDomain.Geometry, order = 1)
    private val rotate = StubTool("rotate", ToolDomain.Geometry, order = 0)
    private val adjust = StubTool("adjust", ToolDomain.Color, order = 0)
    private val trim = StubTool("trim", ToolDomain.Geometry, order = 2, supports = setOf(MediaKind.Video))

    private val declared = listOf(adjust, crop, rotate, trim)

    private fun withRegistry(block: (EditorToolRegistry, SettingsRepositoryImpl) -> Unit) =
        withTempStorageRoot {
            val settings = SettingsRepositoryImpl()
            block(EditorToolRegistry(declared, settings), settings)
        }

    @Test
    fun `tools are sorted by domain then by order`() = withRegistry { registry, _ ->
        assertEquals(
            listOf("rotate", "crop", "adjust"),
            registry.tools(MediaKind.Image).value.map { it.manifest.id }
        )
    }

    @Test
    fun `supports filters by media kind`() = withRegistry { registry, _ ->
        val video = registry.tools(MediaKind.Video).value.map { it.manifest.id }
        val image = registry.tools(MediaKind.Image).value.map { it.manifest.id }
        assertEquals(listOf("rotate", "crop", "trim", "adjust"), video)
        assertEquals(listOf("rotate", "crop", "adjust"), image)
    }

    @Test
    fun `a disabled tool leaves tools but stays in all`() = withRegistry { registry, settings ->
        settings.setEditorToolEnabled("crop", enabled = false)

        assertEquals(
            listOf("rotate", "adjust"),
            registry.tools(MediaKind.Image).value.map { it.manifest.id }
        )
        // You cannot re-enable what is not listed.
        assertEquals(
            listOf("rotate", "crop", "trim", "adjust"),
            registry.all().map { it.manifest.id }
        )
    }

    @Test
    fun `re-enabling re-emits`() = withRegistry { registry, settings ->
        val tools = registry.tools(MediaKind.Image)
        settings.setEditorToolEnabled("adjust", enabled = false)
        assertEquals(listOf("rotate", "crop"), tools.value.map { it.manifest.id })

        settings.setEditorToolEnabled("adjust", enabled = true)
        assertEquals(listOf("rotate", "crop", "adjust"), tools.value.map { it.manifest.id })
    }

    @Test
    fun `toolFor resolves by id and ignores enablement`() = withRegistry { registry, settings ->
        settings.setEditorToolEnabled("crop", enabled = false)
        // Disabled is not the same as unknown — the fold has to tell them apart.
        assertSame(crop, registry.toolFor(ToolOp("crop", ParamSet())))
        assertNull(registry.toolFor(ToolOp("nonexistent", ParamSet())))
    }
}
