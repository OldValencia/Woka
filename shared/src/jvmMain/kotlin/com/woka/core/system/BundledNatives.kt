package com.woka.core.system

import java.io.File
import java.lang.foreign.SymbolLookup

/**
 * Set by Compose Desktop for the packaged app and for `:desktopApp:run` — the
 * merged `appResources/<os>-<arch>/` folder — and by the test task to the same
 * folder in the source tree.
 */
private const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"

/**
 * Native libraries Woka ships inside itself — libjxl, jxrlib — loaded by absolute
 * path from the app's own resources folder and **never searched for** on the
 * system: the user installs nothing, and a system copy of a different version is
 * not something to bind against by accident.
 *
 * Bytedeco's libraries (FFmpeg, LibRaw) are not these; javacpp's `Loader`
 * unpacks and loads its own.
 *
 * [names] are loaded in order, dependencies first: Windows resolves a DLL's
 * imports against modules already loaded, so loading the leaves first is what
 * lets `jxl.dll` find `brotlidec.dll` beside it without touching `PATH`. The
 * symbols are then found through [SymbolLookup.loaderLookup], which sees every
 * library `System.load` brought into this class loader.
 */
internal object BundledNatives {

    fun load(vararg names: String): SymbolLookup {
        val directory = resourcesDirectory()
        names.forEach { name ->
            val library = File(directory, System.mapLibraryName(name))
            check(library.isFile) { "bundled native ${library.name} is missing from $directory" }
            System.load(library.absolutePath)
        }
        return SymbolLookup.loaderLookup()
    }

    private fun resourcesDirectory(): File {
        val path = System.getProperty(RESOURCES_DIR_PROPERTY)
            ?: error("$RESOURCES_DIR_PROPERTY is not set — not started through Compose or the test task")
        return File(path)
    }
}
