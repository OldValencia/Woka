package nativeimage

import java.io.DataInputStream
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipFile

/** A class file's name, superclass (null for `java.lang.Object`) and access flags. */
data class ClassHeader(val name: String, val superName: String?, val access: Int) {
    val isConcrete: Boolean get() = access and (ACC_INTERFACE or ACC_ABSTRACT) == 0

    private companion object {
        const val ACC_INTERFACE = 0x0200
        const val ACC_ABSTRACT = 0x0400
    }
}

/**
 * Reads only as far as the superclass: the constant pool is walked for the class names it
 * holds, then `access_flags`, `this_class` and `super_class`. Null for anything that is not
 * a class file this walk understands.
 */
fun readClassHeader(bytes: ByteArray): ClassHeader? = runCatching {
    val input = DataInputStream(bytes.inputStream())
    if (input.readInt() != 0xCAFEBABE.toInt()) return null
    input.skipBytes(4)
    val count = input.readUnsignedShort()
    val utf = HashMap<Int, String>()
    val classRefs = HashMap<Int, Int>()
    var index = 1
    while (index < count) {
        when (val tag = input.readUnsignedByte()) {
            1 -> utf[index] = input.readUTF()
            7 -> classRefs[index] = input.readUnsignedShort()
            8, 16, 19, 20 -> input.skipBytes(2)
            15 -> input.skipBytes(3)
            3, 4, 9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
            5, 6 -> {
                input.skipBytes(8)
                index++
            }
            else -> error("constant tag $tag")
        }
        index++
    }
    val access = input.readUnsignedShort()
    val name = { ref: Int -> classRefs[ref]?.let { utf[it] }?.replace('/', '.') }
    val self = name(input.readUnsignedShort()) ?: return null
    ClassHeader(self, name(input.readUnsignedShort()), access)
}.getOrNull()

/** Every class in [jars], keyed by binary name. */
fun jarClasses(jars: Collection<File>): Map<String, ClassHeader> = buildMap {
    jars.forEach { jar ->
        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                .mapNotNull { readClassHeader(zip.getInputStream(it).readBytes()) }
                .forEach { put(it.name, it) }
        }
    }
}

/** Every class of GraalVM's own JDK, through the `jrt:/` file system opened on [graalHome]. */
fun jdkClasses(graalHome: File): Set<String> {
    val jrt = FileSystems.newFileSystem(URI.create("jrt:/"), mapOf("java.home" to graalHome.path))
    return jrt.use { fs ->
        Files.walk(fs.getPath("/modules")).use { paths ->
            paths.map { it.toString() }
                .filter { it.endsWith(".class") && !it.endsWith("module-info.class") }
                .map { it.substringAfter("/modules/").substringAfter('/').removeSuffix(".class").replace('/', '.') }
                .toList()
                .toSet()
        }
    }
}

/**
 * The superclass of each class in the JDK modules a desktop image carries, read from
 * GraalVM's `jmods` — a jmod is a zip behind a four-byte header, which `ZipFile` reads
 * from its end.
 */
fun jdkSupers(graalHome: File, modules: List<String> = DESKTOP_MODULES): Map<String, String?> = buildMap {
    modules.forEach { module ->
        ZipFile(File(graalHome, "jmods/$module.jmod")).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("classes/") && it.name.endsWith(".class") }
                .filterNot { it.name.endsWith("module-info.class") }
                .mapNotNull { readClassHeader(zip.getInputStream(it).readBytes()) }
                .forEach { put(it.name, it.superName) }
        }
    }
}

private val DESKTOP_MODULES = listOf("java.base", "java.desktop", "java.datatransfer")
