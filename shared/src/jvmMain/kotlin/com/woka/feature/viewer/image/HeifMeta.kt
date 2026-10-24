package com.woka.feature.viewer.image

/** Where an item's bytes are: in the file (`construction_method` 0) or inside `idat` (1). */
internal data class HeifExtent(val inIdat: Boolean, val offset: Long, val length: Int)

/**
 * The parts of a HEIF `meta` box an item-based still needs — and nothing that
 * FFmpeg already reads for HEIC/AVIF. Written for what Windows 11 Paint stores
 * (`unci` layers under an `iovl` overlay) and nothing broader: the header fields
 * it does not use are skipped by their declared sizes, not interpreted.
 */
internal class HeifMeta(
    val bytes: IsoBytes,
    val primaryItem: Int,
    val itemTypes: Map<Int, String>,
    val extents: Map<Int, List<HeifExtent>>,
    /** `ipco` children, in order — `ipma` indexes them from 1. */
    val properties: List<HeifBox>,
    val associations: Map<Int, List<Int>>,
    /** `iref dimg`: an overlay's inputs, first is bottom. */
    val derivedFrom: Map<Int, List<Int>>,
    val idat: HeifBox?
) {
    /** The first property of [type] associated with [item], or null. */
    fun property(item: Int, type: String): HeifBox? =
        associations[item].orEmpty().map { properties[it - 1] }.firstOrNull { it.type == type }
}

/** `meta` is a full box: its children start after four bytes of version and flags. */
private const val FULL_BOX_HEADER = 4

internal fun parseHeifMeta(meta: IsoBytes): HeifMeta {
    val children = meta.boxes(FULL_BOX_HEADER, meta.bytes.size).associateBy { it.type }
    fun box(type: String): HeifBox = children[type] ?: error("meta has no $type")
    val iprp = meta.boxes(box("iprp").start, box("iprp").end).associateBy { it.type }
    val ipco = iprp["ipco"] ?: error("iprp has no ipco")
    return HeifMeta(
        bytes = meta,
        primaryItem = parsePrimary(meta, box("pitm")),
        itemTypes = parseItemTypes(meta, box("iinf")),
        extents = parseLocations(meta, box("iloc")),
        properties = meta.boxes(ipco.start, ipco.end),
        associations = parseAssociations(meta, iprp["ipma"] ?: error("iprp has no ipma")),
        derivedFrom = children["iref"]?.let { parseDerivations(meta, it) }.orEmpty(),
        idat = children["idat"]
    )
}

private fun parsePrimary(meta: IsoBytes, pitm: HeifBox): Int =
    if (meta.u8(pitm.start) == 0) meta.u16(pitm.start + 4) else meta.u32(pitm.start + 4)

/** `infe` version 2 and 3 only — the versions that carry an item type. */
private fun parseItemTypes(meta: IsoBytes, iinf: HeifBox): Map<Int, String> {
    val countSize = if (meta.u8(iinf.start) == 0) 2 else 4
    return meta.boxes(iinf.start + FULL_BOX_HEADER + countSize, iinf.end).associate { infe ->
        val version = meta.u8(infe.start)
        check(version >= 2) { "infe version $version" }
        val idSize = if (version == 2) 2 else 4
        val id = meta.uN(infe.start + 4, idSize)
        id to meta.fourCc(infe.start + 4 + idSize + 2)
    }
}

private fun parseLocations(meta: IsoBytes, iloc: HeifBox): Map<Int, List<HeifExtent>> {
    val version = meta.u8(iloc.start)
    var at = iloc.start + FULL_BOX_HEADER
    val offsetSize = meta.u8(at) shr 4
    val lengthSize = meta.u8(at) and 0x0F
    val baseOffsetSize = meta.u8(at + 1) shr 4
    val indexSize = if (version >= 1) meta.u8(at + 1) and 0x0F else 0
    val idSize = if (version < 2) 2 else 4
    val count = meta.uN(at + 2, idSize)
    at += 2 + idSize
    val located = mutableMapOf<Int, List<HeifExtent>>()
    repeat(count) {
        val id = meta.uN(at, idSize)
        at += idSize
        val inIdat = version >= 1 && (meta.u16(at) and 0x0F) == 1
        if (version >= 1) at += 2
        at += 2 // data_reference_index: 0, this file
        val base = meta.uN(at, baseOffsetSize).toLong()
        at += baseOffsetSize
        val extentCount = meta.u16(at)
        at += 2
        located[id] = List(extentCount) {
            at += indexSize
            val offset = meta.uN(at, offsetSize)
            val length = meta.uN(at + offsetSize, lengthSize)
            at += offsetSize + lengthSize
            HeifExtent(inIdat, base + offset, length)
        }
    }
    return located
}

private fun parseAssociations(meta: IsoBytes, ipma: HeifBox): Map<Int, List<Int>> {
    val version = meta.u8(ipma.start)
    val wideIndex = (meta.u8(ipma.start + 3) and 1) == 1
    var at = ipma.start + FULL_BOX_HEADER
    val count = meta.u32(at)
    at += 4
    val associated = mutableMapOf<Int, List<Int>>()
    repeat(count) {
        val idSize = if (version < 1) 2 else 4
        val id = meta.uN(at, idSize)
        val associationCount = meta.u8(at + idSize)
        at += idSize + 1
        associated[id] = List(associationCount) {
            val index = if (wideIndex) meta.u16(at) and 0x7FFF else meta.u8(at) and 0x7F
            at += if (wideIndex) 2 else 1
            index
        }
    }
    return associated
}

private fun parseDerivations(meta: IsoBytes, iref: HeifBox): Map<Int, List<Int>> {
    val idSize = if (meta.u8(iref.start) == 0) 2 else 4
    return meta.boxes(iref.start + FULL_BOX_HEADER, iref.end)
        .filter { it.type == "dimg" }
        .associate { reference ->
            val count = meta.u16(reference.start + idSize)
            val first = reference.start + idSize + 2
            meta.uN(reference.start, idSize) to List(count) { meta.uN(first + it * idSize, idSize) }
        }
}
