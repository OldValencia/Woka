package com.woka.feature.editor.core

/**
 * What editing this file would cost it — §5.1's three buckets, decided by the
 * container and nothing else.
 *
 * Named for what **happens**, not for what is missing, because the answer drives a
 * menu item and a notice rather than an error message.
 *
 * The set is not static and is not meant to be. Each new carrier (ME-108) moves a
 * format from [Unavailable] straight to [Recorded], never through [Destructive] —
 * so [Destructive] is a transitional state that holds one format today and is
 * expected to hold none.
 */
enum class EditCapability {

    /**
     * The container has a private extension slot, so the edit is written as a
     * recipe beside the pixels and the pixels are never touched. History, revert,
     * and no generational loss however many times it is edited.
     */
    Recorded,

    /**
     * No slot, but Skia can encode the format — so `Done` re-encodes the file in
     * place and there is nothing to revert to afterwards. The editor says so
     * before any work is done (§4.5); it is never allowed to be a surprise.
     */
    Destructive,

    /**
     * Neither. `Edit` is unavailable, with the reason in the menu.
     *
     * This is where the refusal belongs. Before 2026-08-19 every image format
     * opened happily and eight of eleven then failed at save time, which spends
     * the user's work before telling them it cannot be kept.
     */
    Unavailable
}
