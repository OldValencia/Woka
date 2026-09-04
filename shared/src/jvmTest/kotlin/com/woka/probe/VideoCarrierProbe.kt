package com.woka.probe

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.SampleMedia
import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/** Representative rather than empty: a real op, so the payload has a real size. */
private val DOCUMENT =
    EditDocument(ops = listOf(ToolOp("transform", ParamSet(mapOf("angle" to IntValue(90))))))

/**
 * The gate on the whole of `save/` (ME-281): we append a `uuid` box to an `mp4` and a
 * `mov` — what does everything else do with those files afterwards?
 *
 * Structurally an unknown top-level box must be skipped by its size, which is the
 * extension point the specification provides. That is a claim about the specification and
 * not about mpv, and the difference is the reason this exists.
 *
 * **Half of the question is not machine-answerable**, exactly as in `EditorHdrProbe`: this
 * writes the files, prints where they are, and names the four programs somebody has to
 * open them in. It therefore writes to `build/carrier-probe` and **not** to a temp storage
 * root — a directory that deletes itself on the way out answers nothing.
 *
 * The machine-answerable half is printed anyway, because those two columns passing while a
 * player still refuses the file is the interesting result: it separates "the box is
 * malformed" from "that player is strict".
 *
 *     ./gradlew.bat :shared:jvmTest --rerun --tests "com.woka.probe.VideoCarrierProbe" -i
 */
class VideoCarrierProbe {

    @Test
    fun `what our uuid box does to an mp4 and a mov`() {
        val root = File("build/carrier-probe").apply { mkdirs() }
        val binary = runCatching { Loader.load(ffmpeg::class.java) }.getOrNull()
        if (binary == null) {
            println("Video carrier probe — the bundled ffmpeg could not be unpacked; nothing measured")
            return
        }

        val mov = File(root, "carrier.mov")
        SampleMedia.file(LIVE_PHOTO_MOTION).copyTo(mov, overwrite = true)
        // No `mp4` in the corpus, so a real muxer makes one rather than us hand-building
        // a header. A stream copy, so the picture is the fixture's own.
        val mp4 = File(root, "carrier.mp4")
        val remuxed = run(
            listOf(
                binary, "-y", "-hide_banner", "-loglevel", "error",
                "-i", mov.absolutePath, "-c", "copy", "-movflags", "+faststart",
                mp4.absolutePath
            )
        )

        val subjects = listOfNotNull(mov, mp4.takeIf { it.isFile && it.length() > 0 })
        // The **before** state goes in beside each, so the by-hand question is "does this
        // behave like that one" rather than "does this look right to me".
        subjects.forEach { it.copyTo(File(root, "reference_${it.name}"), overwrite = true) }

        val rows = subjects.map { row(binary, it) }

        println("Video carrier probe — a Woka recipe appended to an mp4 and a mov")
        if (subjects.size < 2) println("The mp4 remux failed: $remuxed")
        println(renderTable(HEADERS, rows))
        printByHand(root)
    }

    private fun row(binary: String, file: File): List<String> {
        val before = file.length()
        val written = EditRecipe.write(file, DOCUMENT)
        if (written.isFailure) {
            val why = written.exceptionOrNull()?.message.orEmpty().take(48)
            return listOf(file.name, "$before", "**✗** $why", "—", "—")
        }
        val readBack = EditRecipe.read(file)?.ops?.size
        val reopened = run(
            listOf(
                binary, "-hide_banner", "-loglevel", "error",
                "-i", file.absolutePath, "-frames:v", "1", "-f", "null", "-"
            )
        )
        return listOf(
            file.name,
            "$before",
            "${file.length()} (+${file.length() - before})",
            if (readBack == DOCUMENT.ops.size) "ok" else "**✗** read back $readBack ops",
            if (reopened == "ok") "ok" else "**✗** ${reopened.take(48)}"
        )
    }

    private fun printByHand(root: File) {
        println()
        println("Files are in `${root.absolutePath}`.")
        println("Each `reference_*` is the same file **without** our box — compare against it.")
        println()
        println("**By hand**, open both `carrier.mp4` and `carrier.mov` in each of:")
        println("  1. mpv")
        println("  2. VLC")
        println("  3. Windows Explorer's preview pane")
        println("  4. a phone's gallery (copy the file across)")
        println("and record one of: *plays as before* / *plays with a complaint* / *refuses it*.")
        println()
        println("**If any common program refuses either file**, `Done` cannot be the default save")
        println("for that container: `save.md` §3.1 is rewritten, `Done ▾` collapses to")
        println("`Render video…`, and ME-282/ME-283 are re-planned before they are written.")
        println("Paste the results into ME-281's § Measured with the date.")
    }

    /** Merged output, drained before waiting: ffmpeg fills a pipe and deadlocks. */
    private fun run(args: List<String>): String {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return "timed out"
        }
        return if (process.exitValue() == 0) "ok" else text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
    }
}

private val HEADERS = listOf("subject", "before", "after", "our reader", "ffmpeg re-opens")
