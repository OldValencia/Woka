package com.woka.support

import com.woka.core.data.STORAGE_ROOT_PROPERTY
import com.woka.core.data.WokaStorage
import java.io.File
import java.nio.file.Files

/**
 * Points [WokaStorage] at a throwaway directory for the
 * duration of a test. Without it every settings or thumbnail test would read and
 * overwrite the real `Documents/OldValencia/Woka` of whoever runs the suite.
 *
 * The previous value of the property is restored rather than cleared, so nesting
 * and parallel classes cannot leak a root into each other.
 */
class TempStorageRoot {

    val dir: File = Files.createTempDirectory("woka-test").toFile()

    private val previous: String? = System.getProperty(STORAGE_ROOT_PROPERTY)

    init {
        System.setProperty(STORAGE_ROOT_PROPERTY, dir.absolutePath)
    }

    fun dispose() {
        if (previous == null) {
            System.clearProperty(STORAGE_ROOT_PROPERTY)
        } else {
            System.setProperty(STORAGE_ROOT_PROPERTY, previous)
        }
        dir.deleteRecursively()
    }
}

/** Scoped form for a single test method; prefer this over manual setup/teardown. */
fun <T> withTempStorageRoot(block: (File) -> T): T {
    val root = TempStorageRoot()
    return try {
        block(root.dir)
    } finally {
        root.dispose()
    }
}
