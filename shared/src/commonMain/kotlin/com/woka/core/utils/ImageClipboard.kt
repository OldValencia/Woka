package com.woka.core.utils

/**
 * Copy the current photo as both a native-resolution picture and a pasteable file.
 * Edited photos use baked temporary files; unedited photos keep the original path.
 * Returns false when decoding, rendering or publishing to the clipboard fails.
 */
expect suspend fun copyImageToClipboard(path: String, frameIndex: Int = 0): Boolean
