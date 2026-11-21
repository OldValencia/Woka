package nativeimage

import java.io.File

/** One FFM call shape as the metadata names it: `jint(void*, jlong)`. */
data class Shape(val returnType: String, val parameterTypes: List<String>)

private val CARRIERS = mapOf(
    "JAVA_INT" to "jint", "JAVA_LONG" to "jlong", "JAVA_BYTE" to "jbyte", "JAVA_SHORT" to "jshort",
    "JAVA_FLOAT" to "jfloat", "JAVA_DOUBLE" to "jdouble", "JAVA_CHAR" to "jchar", "JAVA_BOOLEAN" to "jboolean"
)

/**
 * Every downcall and upcall shape written out in [sources]:
 * - a literal `FunctionDescriptor.of(…)` / `ofVoid(…)`, as written **and** with a leading
 *   pointer — `WinRt.call` inserts `this` before what its caller wrote;
 * - `callChecked(…, listOf(…))`, which is `jint(this, …)`;
 * - `status("…", …)`, JxlNative's `jint(…)`;
 * - a literal in a file holding an `upcallStub` is an upcall as well.
 * A spread (`*xs`) or a struct layout is dynamic and left to the agents.
 */
fun ffmDescriptors(sources: File): Pair<Set<Shape>, Set<Shape>> {
    val downcalls = HashSet<Shape>()
    val upcalls = HashSet<Shape>()
    sources.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
        val text = file.readText()
        val literals = Regex("""FunctionDescriptor\.(of|ofVoid)\(""").findAll(text).mapNotNull { match ->
            val args = arguments(text, match.range.last + 1)
            when {
                args.any { it.startsWith("*") } -> null
                match.groupValues[1] == "of" -> carrier(args.first())?.let { shape(it, args.drop(1)) }
                else -> shape("void", args)
            }
        }.toList()
        literals.forEach {
            downcalls += it
            downcalls += it.copy(parameterTypes = listOf("void*") + it.parameterTypes)
            if ("upcallStub" in text) upcalls += it
        }
        Regex("""callChecked\(""").findAll(text).forEach { match ->
            val window = text.substring(match.range.last, minOf(text.length, match.range.last + 400))
            Regex("""listOf\(([^)]*)\)""").find(window)?.let { list ->
                shape("jint", listOf("ADDRESS") + list.groupValues[1].split(',').filter { it.isNotBlank() })
            }?.let { downcalls += it }
        }
        Regex("""\bstatus\("""").findAll(text).forEach { match ->
            shape("jint", arguments(text, match.range.first + "status(".length).drop(1))?.let { downcalls += it }
        }
    }
    return downcalls to upcalls
}

private fun shape(returnType: String, layouts: List<String>): Shape? {
    val types = layouts.map { carrier(it) ?: return null }
    return Shape(returnType, types)
}

private fun carrier(layout: String): String? {
    val token = layout.trim().substringAfterLast("ValueLayout.")
    return if (token.startsWith("ADDRESS")) "void*" else CARRIERS[token]
}

/** The top-level arguments of the call whose `(` ends just before [from]. */
private fun arguments(text: String, from: Int): List<String> {
    val parts = mutableListOf<String>()
    var depth = 1
    var start = from
    var i = from
    while (depth > 0 && i < text.length) {
        when (text[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) parts += text.substring(start, i)
            ',' -> if (depth == 1) {
                parts += text.substring(start, i)
                start = i + 1
            }
        }
        i++
    }
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}
