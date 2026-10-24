package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixels per PDF point. An artboard has no pixel size of its own, so "actual
 * size" has to be chosen: 2× is 144 DPI, sharp on a HiDPI display at 100 %.
 */
private const val RENDER_SCALE = 2f

/** A poster-sized artboard would otherwise allocate gigabytes; the long edge stops here. */
private const val MAX_EDGE = 8192

/**
 * Adobe Illustrator `.ai` — view-only, through Apache PDFBox.
 *
 * Since Illustrator CS an `.ai` saved with "Create PDF Compatible File" (the
 * default) **is** a PDF whose first page is the artwork composite, so this
 * renders page 1 and nothing else. An `.ai` from Illustrator 8 or earlier is
 * PostScript; PDFBox refuses it, and the viewer shows its usual placeholder —
 * there is no PostScript interpreter here and none is worth adding for it.
 */
internal object IllustratorStillDecoder : StillDecoder {

    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    override fun nativeSize(file: File, frameIndex: Int): IntSize = onDocument(file) { document ->
        val box = document.getPage(0).cropBox
        val scale = scaleFor(box.width, box.height)
        IntSize((box.width * scale).roundToInt(), (box.height * scale).roundToInt())
    }

    override fun frame(file: File, frameIndex: Int): ImageBitmap = onDocument(file) { document ->
        val box = document.getPage(0).cropBox
        PDFRenderer(document)
            .renderImage(0, scaleFor(box.width, box.height), ImageType.ARGB)
            .toStillBitmap()
    }
}

private fun scaleFor(width: Float, height: Float): Float =
    minOf(RENDER_SCALE, MAX_EDGE / max(width, height))

private fun <T> onDocument(file: File, block: (PDDocument) -> T): T =
    Loader.loadPDF(file).use(block)
