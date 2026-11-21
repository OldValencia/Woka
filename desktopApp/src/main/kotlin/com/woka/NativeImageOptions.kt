package com.woka

import java.io.File

/**
 * The JVM launcher reads its options from `Woka.cfg`; a GraalVM native image is
 * started straight from `woka.exe` and gets none of them (ST-001). The heap cap is
 * baked in at build time (`-R:MaxHeapSize`); everything here is a system property
 * that is first read after `main` starts, so it is set as the first thing `main`
 * does — before [StartupPrewarm] brings up AWT and skiko on another thread.
 *
 * The image folder mirrors jpackage's `app\` without the jars: `woka.exe` and the
 * JDK's DLLs, `bin\jawt.dll` and `lib\` (skiko finds jawt and AWT its font
 * configuration under `java.home`), skiko's DLL and `icudtl.dat`, and `resources\`
 * with `javacpp\` inside. Values are set unconditionally, as `Woka.cfg` overrides
 * the JVM's own defaults.
 */
internal object NativeImageOptions {

    private const val IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode"

    fun apply() {
        if (System.getProperty(IMAGE_CODE_PROPERTY) != "runtime") return
        val home = imageDirectory() ?: return
        val resources = File(home, "resources")
        val options = mapOf(
            "java.home" to home.path,
            "compose.application.resources.dir" to resources.path,
            "skiko.library.path" to home.path,
            "java.library.path" to File(resources, "javacpp").path,
            "org.bytedeco.javacpp.pathsFirst" to "true",
            "org.bytedeco.javacpp.findLibraries" to "false",
            "sun.java2d.d3d" to "false",
            "compose.application.configure.swing.globals" to "true"
        )
        options.forEach { (key, value) -> System.setProperty(key, value) }
    }

    private fun imageDirectory(): File? {
        val command = ProcessHandle.current().info().command().orElse(null)
        val directory = command?.let { File(it).absoluteFile.parentFile }
        if (directory == null) {
            System.err.println("Woka: native image could not resolve its own folder; options from Woka.cfg not applied")
        }
        return directory
    }
}
