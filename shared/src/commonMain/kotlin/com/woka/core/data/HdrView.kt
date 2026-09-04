package com.woka.core.data

/**
 * What the app should believe about a still's colour, whatever the file claims (ME-119).
 *
 * **This overrides detection, not output.** Woka draws into an sRGB Skia window and there
 * is no HDR swapchain anywhere in the stack, so [ForceHdr] cannot mean "send HDR to the
 * monitor" and does not pretend to. It means *treat this file as PQ*, which is a useful
 * thing to say about a file whose tags are wrong — the corpus holds three that carry a
 * perfect PQ description over ordinary SDR pixels.
 */
enum class HdrViewMode {
    /** Tone map when the file's transfer says HDR. The default, and what shipped before. */
    Auto,

    /** Tone map as PQ / BT.2020 even when the tags say SDR. */
    ForceHdr,

    /**
     * Never tone map.
     *
     * Also the app's answer to *"is this really HDR?"* — a genuine HDR picture loses its
     * highlights here, and a tags-only fake looks exactly as it did.
     */
    ForceSdr
}

/**
 * Whether the `HDR ▾` control belongs in the top bar's pill for this picture (ME-303).
 *
 * **Hidden on an SDR file**, by the author's decision: `Auto`, `On` and `Off` are three
 * names for leaving such a picture alone, and a control that moves nothing is worse than
 * no control — the same line ME-180 held for video and ME-302 for the rail.
 *
 * **It reads the transfer and ignores [StillColorInfo.isWideGamut] deliberately**, which
 * is why it takes the whole description rather than the one field: [needsToneCurve] and
 * not [needsColorManagement] is the question, because `TransferKind`'s own KDoc records
 * that BT.2020 primaries with an ordinary transfer are not HDR. The cost is real and
 * accepted — such a file *is* gamut mapped, and `Force SDR` is no longer reachable to
 * stop that.
 *
 * **The [HdrViewMode.ForceHdr] clause is not removable.** The pill *writes* this setting,
 * and the mode outlives the file it was set on: `Force HDR` chosen on an HDR photograph is
 * still in force when the user walks to an SDR one, where `colourToBelieve` then tone maps
 * it as PQ. Hiding the control there would leave a picture visibly wrong with nothing on
 * screen to put it back. So the rule is *shown while it is doing something, or while it
 * could undo something* — and [HdrViewMode.ForceSdr] earns no clause of its own, because
 * it changes nothing about a picture that was never going to be tone mapped.
 */
fun hdrControlApplies(color: StillColorInfo, mode: HdrViewMode): Boolean =
    needsToneCurve(color.transfer) || mode == HdrViewMode.ForceHdr

/**
 * The current [HdrViewMode], for the readers that cannot resolve a setting.
 *
 * The same shape `AppliedStillEdits` uses and for the same reason: `displayable` and
 * `WokaStorage` are plain functions on the decode path, called from places that hold no
 * Koin scope, so the seam is a holder DI fills once rather than a parameter threaded
 * through every caller.
 *
 * **A supplier rather than a value**, so nothing has to observe the settings flow and
 * push. It reads `settings.value` at the moment it is asked, which cannot be stale.
 *
 * Uninstalled it answers [HdrViewMode.Auto] — the default, so a still decoded before the
 * graph is built behaves exactly as it always did rather than briefly wrong.
 */
object HdrView {

    private var supplier: (() -> HdrViewMode)? = null

    fun install(source: () -> HdrViewMode) {
        supplier = source
    }

    val mode: HdrViewMode
        get() = supplier?.invoke() ?: HdrViewMode.Auto

    /**
     * What the mode adds to a cache key, and **empty for [HdrViewMode.Auto]**.
     *
     * The mode belongs in the key rather than in an invalidation step: a key that carries
     * it means switching misses, switching back *hits*, and no code has to remember to
     * clear anything. Empty on the default keeps every existing key byte-identical, so
     * nobody's thumbnails are rebuilt for a switch they never touch.
     */
    val cacheSuffix: String
        get() = when (mode) {
            HdrViewMode.Auto -> ""
            HdrViewMode.ForceHdr -> "|hdr"
            HdrViewMode.ForceSdr -> "|sdr"
        }
}
