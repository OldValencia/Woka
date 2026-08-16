package com.woka.feature.editor.core

import kotlinx.serialization.json.Json

/**
 * The `Json` an [ObjectParam] payload round-trips through.
 *
 * **Not the instance `SettingsStore` owns** (ME-003 step 5). Settings and edit
 * documents are different schemas with different tolerance for change: settings
 * are pretty-printed because a human opens `settings.json` and edits it, while a
 * document is machine-written and gains nothing from the whitespace. Sharing one
 * instance would mean a formatting decision taken for one schema silently
 * applying to the other.
 *
 * `ignoreUnknownKeys` for the reason the rest of the schema degrades rather than
 * throws: a payload written by a newer build must not make an older one refuse
 * the whole document (`code-style/model.md`).
 */
internal val editorJson = Json {
    ignoreUnknownKeys = true
}
