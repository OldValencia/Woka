package nativeimage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The parsing the native image's rules stand on (ST-004); each case is one the real tree has. */
class NativeImageRulesTest {

    @Test
    fun `a JNI export names its class without the method`() {
        assertEquals("sun.awt.windows.WFileDialogPeer", exportedClass("Java_sun_awt_windows_WFileDialogPeer_initIDs"))
    }

    @Test
    fun `an underscore in a name and an overload signature are decoded`() {
        assertEquals("org.bytedeco.ffmpeg.avutil.AVFrame_1x", exportedClass("Java_org_bytedeco_ffmpeg_avutil_AVFrame_11x_data"))
        assertEquals("org.bytedeco.javacpp.Pointer", exportedClass("Java_org_bytedeco_javacpp_Pointer_allocate__J"))
    }

    @Test
    fun `a nested class arrives through its escaped dollar`() {
        assertEquals("java.awt.Toolkit\$Inner", exportedClass("Java_java_awt_Toolkit_00024Inner_run"))
    }

    @Test
    fun `FFM literals, callChecked and status are read as the code builds them`() {
        val dir = Files.createTempDirectory("ffm").toFile()
        try {
            File(dir, "Calls.kt").writeText(
                """
                val a = WinRt.call(obj, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG))
                fun b() = WinRt.callChecked(obj, 3, "put", listOf(JAVA_INT), 1)
                val c by lazy { status("JxlDecoderSetCms", ADDRESS, ADDRESS) }
                val d = FunctionDescriptor.of(JAVA_INT, *layouts)
                """.trimIndent()
            )
            val (downcalls, upcalls) = ffmDescriptors(dir)
            assertTrue(Shape("jint", listOf("void*", "jlong")) in downcalls, "the literal as written")
            assertTrue(Shape("jint", listOf("void*", "void*", "jlong")) in downcalls, "with WinRt's this")
            assertTrue(Shape("jint", listOf("void*", "jint")) in downcalls, "callChecked")
            assertTrue(Shape("jint", listOf("void*", "void*")) in downcalls, "status")
            assertTrue(upcalls.isEmpty(), "no upcallStub in the file")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a class header gives the name, the superclass and whether it is concrete`() {
        val bytes = assertNotNull(ArrayList::class.java.getResourceAsStream("ArrayList.class")).readBytes()
        val header = assertNotNull(readClassHeader(bytes))
        assertEquals("java.util.ArrayList", header.name)
        assertEquals("java.util.AbstractList", header.superName)
        assertTrue(header.isConcrete)
    }
}
