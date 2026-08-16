package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.support.awaitSettingsWrite
import com.woka.support.onSettingsDisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `editorDisabledTools` through `settings.json` and back.
 *
 * Its own class rather than another case in `SettingsPersistenceTest`, which sits
 * at the 150-line ceiling — the file-size mandate is not something a test file is
 * exempt from.
 */
class EditorSettingsPersistenceTest {

    @Test
    fun `a disabled tool survives the round trip`() = onSettingsDisk { repository ->
        repository.setEditorToolEnabled("markup", enabled = false)
        awaitSettingsWrite(""""editorDisabledTools"""")

        val reopened = SettingsRepositoryImpl().settings.value
        assertEquals(setOf("markup"), reopened.editorDisabledTools)
    }

    @Test
    fun `re-enabling removes the id rather than recording an enabled one`() =
        onSettingsDisk { repository ->
            repository.setEditorToolEnabled("markup", enabled = false)
            repository.setEditorToolEnabled("crop", enabled = false)
            repository.setEditorToolEnabled("markup", enabled = true)
            awaitSettingsWrite(""""crop"""")

            val reopened = SettingsRepositoryImpl().settings.value
            // The set names what is off. An enabled tool is simply absent — which
            // is what lets a tool shipped later appear for an existing install.
            assertEquals(setOf("crop"), reopened.editorDisabledTools)
        }

    @Test
    fun `a file written before the field existed reads as nothing disabled`() =
        onSettingsDisk { repository ->
            repository.setTheme("Dark")
            awaitSettingsWrite(""""theme": "Dark"""")

            val reopened = SettingsRepositoryImpl().settings.value
            assertTrue(reopened.editorDisabledTools.isEmpty())
        }
}
