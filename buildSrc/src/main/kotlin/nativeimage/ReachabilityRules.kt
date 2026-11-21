package nativeimage

import groovy.json.JsonOutput
import java.io.File

private val SWING_UI = Regex("""^(com\.sun\.java\.swing\.plaf\.windows\.Windows|javax\.swing\.plaf\.basic\.Basic|javax\.swing\.plaf\.metal\.Metal)[A-Za-z]*UI$""")

private val RESOURCE_GLOBS = listOf(
    "composeResources/**", "org/apache/pdfbox/resources/**",
    "org/apache/fontbox/cmap/**", "org/apache/fontbox/unicode/**"
)

private val SWING_BUNDLES = listOf(
    "com.sun.swing.internal.plaf.basic.resources.basic",
    "com.sun.swing.internal.plaf.metal.resources.metal",
    "com.sun.java.swing.plaf.windows.resources.windows"
)

/**
 * Reachability metadata **by rule** (ST-001, 2026-11-21): everything Woka's dependencies can
 * reach dynamically, derived from the dependencies themselves rather than from what a traced
 * run happened to execute. Writes `reachability-metadata.json` into [out]; returns the counts,
 * for the log and for comparing against an earlier generation.
 *
 * [appDir] is the distributable's `app` folder — its jars, its skiko DLL, its
 * `resources\javacpp\jni*.dll`; [sources] is the Kotlin source root FFM descriptors are read
 * from.
 */
fun writeRules(graalHome: File, appDir: File, sources: File, out: File): Map<String, Int> {
    val jdk = jdkClasses(graalHome)
    val jars = jarClasses(appDir.listFiles { f -> f.extension == "jar" }.orEmpty().toList())
    val jdkParents = jdkSupers(graalHome)
    val superOf = { name: String -> jdkParents[name] ?: jars[name]?.superName }
    val entries = LinkedHashMap<String, MutableMap<String, Any>>()
    fun entry(name: String) = entries.getOrPut(name) { linkedMapOf("type" to name) }

    val libraries = IMAGE_JDK_LIBRARIES.map { File(graalHome, "bin/$it.dll") }.filter { it.isFile } +
        appDir.listFiles { f -> f.extension == "dll" }.orEmpty() +
        File(appDir, "resources/javacpp").listFiles { f -> f.name.startsWith("jni") && f.extension == "dll" }.orEmpty()
    val jni = jniClasses(libraries, jdk + jars.keys, superOf)
    jni.forEach {
        entry(it).putAll(
            mapOf("jniAccessible" to true, "allDeclaredFields" to true, "allDeclaredMethods" to true, "allDeclaredConstructors" to true)
        )
    }

    fun under(name: String, root: String): Boolean = generateSequence(name) { jars[it]?.superName }.any { it == root }
    val pointers = jars.values.filter { it.isConcrete && under(it.name, "org.bytedeco.javacpp.Pointer") }
    pointers.forEach { entry(it.name)["unsafeAllocated"] = true }

    val uis = jdk.filter { SWING_UI.matches(it) }
    uis.forEach { entry(it)["allDeclaredMethods"] = true }
    entry("com.sun.java.swing.plaf.windows.WindowsLookAndFeel").addMethod("<init>")
    listOf("java.rmi.MarshalledObject", "java.rmi.Remote").forEach {
        entry(it).putAll(mapOf("allDeclaredConstructors" to true, "allDeclaredMethods" to true))
    }

    // Component.checkCoalescing asks each subclass for coalesceEvents; registered, the type
    // answers "not declared" instead of throwing.
    val components = jars.keys.filter { name ->
        generateSequence(name) { jars[it]?.superName }.last().let { it.startsWith("java.awt.") || it.startsWith("javax.swing.") }
    }
    components.forEach { entry(it).addMethod("coalesceEvents", "java.awt.AWTEvent", "java.awt.AWTEvent") }

    // Kotlin's DebugMetadata reads a continuation's `label` by reflection to build a stack frame.
    val continuations = jars.keys.filter { under(it, "kotlin.coroutines.jvm.internal.BaseContinuationImpl") }
    continuations.forEach { entry(it).addToList("fields", mapOf("name" to "label")) }

    val handles = jdk.filter { it.startsWith("java.lang.invoke.VarHandleSegmentAs") }
    handles.forEach { entry(it)["allDeclaredMethods"] = true }

    val (downcalls, upcalls) = ffmDescriptors(sources)
    val document = mapOf(
        "reflection" to entries.values.sortedBy { it["type"] as String },
        "resources" to RESOURCE_GLOBS.map { mapOf("glob" to it) } +
            listOf("javax/swing/plaf/**", "com/sun/java/swing/plaf/**").map { mapOf("module" to "java.desktop", "glob" to it) } +
            SWING_BUNDLES.map { mapOf("bundle" to it) },
        "foreign" to mapOf("downcalls" to downcalls.sortedBy { it.toString() }.map { it.toJson() }, "upcalls" to upcalls.map { it.toJson() })
    )
    out.mkdirs()
    File(out, "reachability-metadata.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(document)))
    return mapOf(
        "jni" to jni.size, "pointers" to pointers.size, "ui" to uis.size, "components" to components.size,
        "continuations" to continuations.size, "varhandles" to handles.size, "types" to entries.size,
        "downcalls" to downcalls.size, "upcalls" to upcalls.size
    )
}

private fun Shape.toJson() = mapOf("returnType" to returnType, "parameterTypes" to parameterTypes)

private fun MutableMap<String, Any>.addMethod(name: String, vararg parameters: String) =
    addToList("methods", mapOf("name" to name, "parameterTypes" to parameters.toList()))

@Suppress("UNCHECKED_CAST")
private fun MutableMap<String, Any>.addToList(key: String, item: Map<String, Any>) {
    (getOrPut(key) { mutableListOf<Map<String, Any>>() } as MutableList<Map<String, Any>>) += item
}
