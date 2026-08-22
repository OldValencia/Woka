package com.woka.feature.editor.tools.crop

/**
 * What a pointer is holding: one of the eight handles, the body, or nothing (ME-109).
 *
 * The four corners and the four edges are one enum rather than two, because every
 * caller — the hit test, the resize arithmetic, the cursor — branches on the same
 * question and would otherwise branch on it twice.
 *
 * [movesLeft] and friends are the whole of what a handle *is*: which edges it drags.
 * A corner moves two, an edge moves one, and the resize needs no `when` over eight
 * cases to work that out.
 */
enum class CropHandle(
    val movesLeft: Boolean = false,
    val movesTop: Boolean = false,
    val movesRight: Boolean = false,
    val movesBottom: Boolean = false
) {
    TopLeft(movesLeft = true, movesTop = true),
    Top(movesTop = true),
    TopRight(movesTop = true, movesRight = true),
    Right(movesRight = true),
    BottomRight(movesRight = true, movesBottom = true),
    Bottom(movesBottom = true),
    BottomLeft(movesLeft = true, movesBottom = true),
    Left(movesLeft = true),

    /** The whole rect, dragged rather than resized. */
    Body;

    val isCorner: Boolean
        get() = (if (movesLeft) 1 else 0) + (if (movesRight) 1 else 0) +
            (if (movesTop) 1 else 0) + (if (movesBottom) 1 else 0) == 2

    companion object {
        /** The eight, in the order an overlay draws them. Body is not one of these. */
        val resizing: List<CropHandle> = entries.filter { it != Body }
    }
}

/**
 * Which handle sits at [x], [y] — in **source** pixels, with [tolerance] in the same.
 *
 * The tolerance is the caller's because it is a *screen* distance converted back
 * through the canvas scale: forty screen pixels around a handle is a comfortable target
 * at 100 % and an unusable one at 12 %, and only the overlay knows which it is drawing.
 *
 * **Corners win over edges**, which is why the order below is not the enum's: at a
 * corner both tests pass, and a drag that resized one axis when the user grabbed the
 * corner would feel broken rather than merely wrong.
 *
 * [cornerTolerance] is separately wider, because a corner is the hardest target on the
 * screen and the two at the bottom sit where the picture meets the inspector — asked
 * for by hand, 2026-08-22, after "practically on the corner" turned out to be true.
 */
fun CropRect.handleAt(
    x: Int,
    y: Int,
    tolerance: Int,
    cornerTolerance: Int = tolerance
): CropHandle? {
    val atLeft = near(x, left, cornerTolerance)
    val atRight = near(x, right, cornerTolerance)
    val atTop = near(y, top, cornerTolerance)
    val atBottom = near(y, bottom, cornerTolerance)
    val nearLeft = near(x, left, tolerance)
    val nearRight = near(x, right, tolerance)
    val nearTop = near(y, top, tolerance)
    val nearBottom = near(y, bottom, tolerance)
    val withinX = x >= left - tolerance && x <= right + tolerance
    val withinY = y >= top - tolerance && y <= bottom + tolerance

    return when {
        atLeft && atTop -> CropHandle.TopLeft
        atRight && atTop -> CropHandle.TopRight
        atRight && atBottom -> CropHandle.BottomRight
        atLeft && atBottom -> CropHandle.BottomLeft
        nearTop && withinX -> CropHandle.Top
        nearBottom && withinX -> CropHandle.Bottom
        nearLeft && withinY -> CropHandle.Left
        nearRight && withinY -> CropHandle.Right
        x in left..right && y in top..bottom -> CropHandle.Body
        else -> null
    }
}

private fun near(value: Int, edge: Int, tolerance: Int): Boolean =
    value >= edge - tolerance && value <= edge + tolerance
