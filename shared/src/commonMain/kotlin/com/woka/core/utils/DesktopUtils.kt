package com.woka.core.utils

expect fun openDirectoryPicker(title: String, initialDirectory: String? = null): String?

/** Picks a single existing file, filtered to [extensions] (lower-case, no dot). */
expect fun openFilePicker(
    title: String,
    extensions: List<String>,
    initialDirectory: String? = null
): String?
