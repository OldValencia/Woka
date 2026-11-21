package nativeimage

import java.io.File

/**
 * The GraalVM `bin` DLLs a desktop image ships beside its executable, byte for byte — what the
 * image's own output folder held on 2026-11-21. The rules are derived before the build, so
 * they cannot read that folder; a DLL added here adds the classes its JNI code names.
 *
 * Not `java.dll` or `jvm.dll`: the image writes its own, and the HotSpot `bin\java.dll` names
 * 76 core classes the image's never reaches.
 */
val IMAGE_JDK_LIBRARIES = listOf(
    "awt", "fontmanager", "freetype", "javaaccessbridge",
    "javajpeg", "jawt", "jsound", "lcms", "mlib_image"
)

private val LITERAL = Regex("(?:java|javax|sun|jdk|com/sun|org/jetbrains|org/bytedeco)/[A-Za-z0-9_/$]{2,}")
private val EXPORT = Regex("Java_[A-Za-z0-9_]+")

/**
 * Every class the native code in [libraries] can reach through JNI, of those [known]:
 * - a class named as a string literal — what `FindClass` and signatures are built from;
 * - a class whose natives a library exports (`Java_<class>_<method>`): its `initIDs` reads
 *   members through the `jclass` it is handed, a name that never appears as a string;
 * - every `java.awt.event` class, built by `awt.dll` through constructors seen only under
 *   real input;
 * - **every superclass of each** — JNI resolves a member through the class it is asked on,
 *   the image registers it on the declaring class (drag and drop failed on exactly that).
 */
fun jniClasses(libraries: Collection<File>, known: Set<String>, superOf: (String) -> String?): Set<String> {
    val found = HashSet<String>()
    libraries.forEach { library ->
        val text = String(library.readBytes(), Charsets.ISO_8859_1)
        LITERAL.findAll(text).map { it.value.replace('/', '.') }.filterTo(found) { it in known }
        EXPORT.findAll(text).map { exportedClass(it.value) }.filterTo(found) { it in known }
    }
    known.filterTo(found) { it.startsWith("java.awt.event.") && '$' !in it }
    found.toList().forEach { name ->
        var parent = superOf(name)
        while (parent != null && parent != "java.lang.Object") {
            found += parent
            parent = superOf(parent)
        }
    }
    return found
}

/**
 * The class a JNI export belongs to. Mangling: `_` separates, `_1` is an underscore, `_0xxxx`
 * a UTF-16 unit, `__` starts the argument signature; the last token is the method.
 */
fun exportedClass(symbol: String): String {
    val body = symbol.removePrefix("Java_").substringBefore("__")
    val parts = mutableListOf<String>()
    val token = StringBuilder()
    var i = 0
    while (i < body.length) {
        val next = body.getOrNull(i + 1)
        when {
            body[i] == '_' && next == '1' -> token.append('_').also { i += 2 }
            body[i] == '_' && next == '0' && i + 6 <= body.length -> {
                token.append(body.substring(i + 2, i + 6).toInt(16).toChar())
                i += 6
            }
            body[i] == '_' -> {
                parts += token.toString()
                token.clear()
                i++
            }
            else -> token.append(body[i]).also { i++ }
        }
    }
    return parts.joinToString(".")
}
