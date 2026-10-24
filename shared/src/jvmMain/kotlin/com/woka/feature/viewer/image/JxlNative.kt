package com.woka.feature.viewer.image

import com.woka.core.system.BundledNatives
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

/** `JxlDecoderStatus` values and `JxlDecoderSubscribeEvents` bits, from `jxl/decode.h` 0.12. */
internal const val JXL_DEC_SUCCESS = 0
internal const val JXL_DEC_ERROR = 1
internal const val JXL_DEC_NEED_MORE_INPUT = 2
internal const val JXL_DEC_NEED_IMAGE_OUT_BUFFER = 5
internal const val JXL_DEC_BASIC_INFO = 0x40
internal const val JXL_DEC_COLOR_ENCODING = 0x100
internal const val JXL_DEC_FRAME = 0x400
internal const val JXL_DEC_FULL_IMAGE = 0x1000

/** Field offsets in `JxlBasicInfo` (`jxl/codestream_header.h` 0.12, 204 bytes). */
internal const val BASIC_INFO_SIZE = 204L
internal const val BASIC_XSIZE = 4L
internal const val BASIC_YSIZE = 8L
internal const val BASIC_HAVE_ANIMATION = 44L
internal const val BASIC_ORIENTATION = 48L
internal const val BASIC_COLOR_CHANNELS = 52L
internal const val BASIC_TPS_NUMERATOR = 80L
internal const val BASIC_TPS_DENOMINATOR = 84L

/** `JxlFrameHeader` is 52 bytes; `duration` (in ticks) leads it. */
internal const val FRAME_HEADER_SIZE = 64L

/** `JxlColorEncoding` is 104 bytes; `JxlColorEncodingSetToSRGB` fills it. */
internal const val COLOR_ENCODING_SIZE = 128L

/**
 * `JxlPixelFormat`: `num_channels`, `data_type`, `endianness`, then a `size_t`
 * `align` — 24 bytes with its padding. `JXL_TYPE_UINT8` is 2, native endian 0.
 */
internal const val PIXEL_FORMAT_SIZE = 24L
internal const val JXL_TYPE_UINT8 = 2

/** `JxlCmsInterface` — eight pointers, passed **by value** to `JxlDecoderSetCms`. */
private val CMS_INTERFACE = MemoryLayout.structLayout(*Array(8) { ADDRESS })

/**
 * libjxl 0.12's decoder API as Java FFM downcalls, bound on first use.
 *
 * The six libraries are the official Windows release's minimal set, leaves
 * first (`natives/jxl/README.md`). Only what [JxlStillDecoder] calls is bound.
 */
internal object JxlNative {

    private val lookup by lazy {
        BundledNatives.load("brotlicommon", "brotlidec", "brotlienc", "jxl_cms", "jxl", "jxl_threads")
    }

    private val linker = Linker.nativeLinker()

    private fun bind(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.find(name).orElseThrow { error("libjxl has no $name") }, descriptor)

    private fun status(name: String, vararg arguments: MemoryLayout): MethodHandle =
        bind(name, FunctionDescriptor.of(JAVA_INT, *arguments))

    val decoderCreate by lazy { bind("JxlDecoderCreate", FunctionDescriptor.of(ADDRESS, ADDRESS)) }
    val decoderDestroy by lazy { bind("JxlDecoderDestroy", FunctionDescriptor.ofVoid(ADDRESS)) }
    val runnerCreate by lazy {
        bind("JxlThreadParallelRunnerCreate", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG))
    }
    val runnerDefaultThreads by lazy {
        bind("JxlThreadParallelRunnerDefaultNumWorkerThreads", FunctionDescriptor.of(JAVA_LONG))
    }
    val runnerDestroy by lazy { bind("JxlThreadParallelRunnerDestroy", FunctionDescriptor.ofVoid(ADDRESS)) }
    val runner: MemorySegment by lazy {
        lookup.find("JxlThreadParallelRunner").orElseThrow { error("libjxl has no thread runner") }
    }
    val setParallelRunner by lazy { status("JxlDecoderSetParallelRunner", ADDRESS, ADDRESS, ADDRESS) }
    val subscribeEvents by lazy { status("JxlDecoderSubscribeEvents", ADDRESS, JAVA_INT) }
    val setInput by lazy { status("JxlDecoderSetInput", ADDRESS, ADDRESS, JAVA_LONG) }
    val closeInput by lazy { bind("JxlDecoderCloseInput", FunctionDescriptor.ofVoid(ADDRESS)) }
    val processInput by lazy { status("JxlDecoderProcessInput", ADDRESS) }
    val getBasicInfo by lazy { status("JxlDecoderGetBasicInfo", ADDRESS, ADDRESS) }
    val getFrameHeader by lazy { status("JxlDecoderGetFrameHeader", ADDRESS, ADDRESS) }
    val skipFrames by lazy { bind("JxlDecoderSkipFrames", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG)) }
    val defaultCms by lazy { bind("JxlGetDefaultCms", FunctionDescriptor.of(ADDRESS.withTargetLayout(CMS_INTERFACE))) }
    val setCms by lazy { status("JxlDecoderSetCms", ADDRESS, CMS_INTERFACE) }
    val setToSrgb by lazy { bind("JxlColorEncodingSetToSRGB", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT)) }
    val setOutputColorProfile by lazy {
        status("JxlDecoderSetOutputColorProfile", ADDRESS, ADDRESS, ADDRESS, JAVA_LONG)
    }
    val imageOutBufferSize by lazy { status("JxlDecoderImageOutBufferSize", ADDRESS, ADDRESS, ADDRESS) }
    val setImageOutBuffer by lazy { status("JxlDecoderSetImageOutBuffer", ADDRESS, ADDRESS, ADDRESS, JAVA_LONG) }
}
