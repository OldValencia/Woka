package com.woka.integration

import com.woka.app.navigation.Router
import com.woka.core.data.SettingsRepository
import com.woka.support.startKoinOnce
import org.koin.core.context.GlobalContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The container still resolves with `editorModule` composed into `platformModule`.
 *
 * `editorModule` is empty until ME-006, so there is nothing of its own to resolve
 * yet — what this guards is the composition itself. A duplicate definition or a
 * broken `includes` fails at `startKoin`, which would otherwise surface as every
 * Koin-using test dying at once with no indication of the cause.
 */
class EditorModuleTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the container starts with the editor module loaded`() {
        val koin = assertNotNull(GlobalContext.getOrNull(), "Koin did not start")
        // Resolution throws when a definition is missing or declared twice, so
        // these calls are the assertion.
        koin.get<Router>()
        koin.get<SettingsRepository>()
    }
}
