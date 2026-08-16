package com.woka.feature.editor.core

/**
 * Whether the destructive-overwrite confirmation still has to be shown (§5).
 *
 * **Deliberately not a `WokaSettings` field, and deliberately not on disk.** A
 * persisted opt-out would quietly turn destructive overwrite into the permanent
 * default, which is the single outcome the whole save model exists to prevent.
 * Repetition is the point for an action that cannot be undone — but twenty
 * overwrites in one sitting are not served by twenty dialogs, so the answer is
 * remembered for the run of the process and forgotten by the process ending.
 *
 * Nothing resets it explicitly because nothing needs to: there is no code path
 * that writes it anywhere, so a restart is the reset.
 *
 * A Koin `single`, so the one in-memory answer is shared by every editor session
 * in the run rather than re-asked per file.
 */
class OverwriteConsent {

    private var remembered = false

    fun shouldAsk(): Boolean = !remembered

    /** The dialog's "Don't ask again this session" box. In memory only. */
    fun rememberForSession() {
        remembered = true
    }
}
