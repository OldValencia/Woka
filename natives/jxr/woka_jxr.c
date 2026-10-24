/*
 * woka_jxr — JPEG XR decode shim over jxrlib (BSD-2), see README.md.
 *
 * Flow: CreateWS_Memory -> PKImageDecode_Create_WMP -> Initialize (container + header parse)
 *       -> configure the decoder for its native pixel format (as JxrDecApp does)
 *       -> Copy() into a native-format buffer -> hand-convert to RGBA8 sRGB.
 */
#include "woka_jxr.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "JXRGlue.h"

/* ---- sample types of the native buffer ---- */
enum SampleType { ST_U8, ST_U16, ST_S16FX, ST_HALF, ST_S32FX, ST_F32 };

typedef struct {
    const PKPixelFormatGUID* guid;
    int bytes_per_pixel;  /* stride of one pixel in the native buffer */
    int channels;         /* 1 = gray, 3 = color, 4 = color + alpha */
    int bgr;              /* channel order B,G,R */
    int premultiplied;
    enum SampleType type;
} FormatDesc;

/* Generic formats handled by convert_generic(). Integer samples are treated as sRGB-encoded,
 * fixed-point / half / float samples as linear scRGB. */
static const FormatDesc kGeneric[] = {
    { &GUID_PKPixelFormat8bppGray,              1,  1, 0, 0, ST_U8    },
    { &GUID_PKPixelFormat16bppGray,             2,  1, 0, 0, ST_U16   },
    { &GUID_PKPixelFormat16bppGrayFixedPoint,   2,  1, 0, 0, ST_S16FX },
    { &GUID_PKPixelFormat16bppGrayHalf,         2,  1, 0, 0, ST_HALF  },
    { &GUID_PKPixelFormat32bppGrayFixedPoint,   4,  1, 0, 0, ST_S32FX },
    { &GUID_PKPixelFormat32bppGrayFloat,        4,  1, 0, 0, ST_F32   },
    { &GUID_PKPixelFormat24bppRGB,              3,  3, 0, 0, ST_U8    },
    { &GUID_PKPixelFormat24bppBGR,              3,  3, 1, 0, ST_U8    },
    { &GUID_PKPixelFormat32bppRGB,              4,  3, 0, 0, ST_U8    },
    { &GUID_PKPixelFormat32bppBGR,              4,  3, 1, 0, ST_U8    },
    { &GUID_PKPixelFormat32bppRGBA,             4,  4, 0, 0, ST_U8    },
    { &GUID_PKPixelFormat32bppBGRA,             4,  4, 1, 0, ST_U8    },
    { &GUID_PKPixelFormat32bppPRGBA,            4,  4, 0, 1, ST_U8    },
    { &GUID_PKPixelFormat32bppPBGRA,            4,  4, 1, 1, ST_U8    },
    { &GUID_PKPixelFormat48bppRGB,              6,  3, 0, 0, ST_U16   },
    { &GUID_PKPixelFormat64bppRGBA,             8,  4, 0, 0, ST_U16   },
    { &GUID_PKPixelFormat64bppPRGBA,            8,  4, 0, 1, ST_U16   },
    { &GUID_PKPixelFormat48bppRGBFixedPoint,    6,  3, 0, 0, ST_S16FX },
    { &GUID_PKPixelFormat64bppRGBFixedPoint,    8,  3, 0, 0, ST_S16FX },
    { &GUID_PKPixelFormat64bppRGBAFixedPoint,   8,  4, 0, 0, ST_S16FX },
    { &GUID_PKPixelFormat48bppRGBHalf,          6,  3, 0, 0, ST_HALF  },
    { &GUID_PKPixelFormat64bppRGBHalf,          8,  3, 0, 0, ST_HALF  },
    { &GUID_PKPixelFormat64bppRGBAHalf,         8,  4, 0, 0, ST_HALF  },
    { &GUID_PKPixelFormat96bppRGBFixedPoint,    12, 3, 0, 0, ST_S32FX },
    { &GUID_PKPixelFormat128bppRGBFixedPoint,   16, 3, 0, 0, ST_S32FX },
    { &GUID_PKPixelFormat128bppRGBAFixedPoint,  16, 4, 0, 0, ST_S32FX },
    { &GUID_PKPixelFormat96bppRGBFloat,         12, 3, 0, 0, ST_F32   },
    { &GUID_PKPixelFormat128bppRGBFloat,        16, 3, 0, 0, ST_F32   },
    { &GUID_PKPixelFormat128bppRGBAFloat,       16, 4, 0, 0, ST_F32   },
    { &GUID_PKPixelFormat128bppPRGBAFloat,      16, 4, 0, 1, ST_F32   },
};

/* ---- scalar helpers ---- */
static float half_to_float(uint16_t h)
{
    uint32_t sign = (uint32_t)(h & 0x8000u) << 16;
    uint32_t exp = (h >> 10) & 0x1Fu;
    uint32_t man = h & 0x3FFu;
    uint32_t bits;
    float f;
    if (exp == 0) {
        /* zero / subnormal: man * 2^-24 */
        f = (float)man * (1.0f / 16777216.0f);
        return sign ? -f : f;
    }
    if (exp == 31) bits = sign | 0x7F800000u | (man << 13);          /* inf / nan */
    else bits = sign | ((exp + 112u) << 23) | (man << 13);
    memcpy(&f, &bits, sizeof f);
    return f;
}

static float read_sample(const uint8_t* p, enum SampleType t)
{
    switch (t) {
    case ST_U8:    return (float)p[0] * (1.0f / 255.0f);
    case ST_U16:   { uint16_t v; memcpy(&v, p, 2); return (float)v * (1.0f / 65535.0f); }
    case ST_S16FX: { int16_t v; memcpy(&v, p, 2); return (float)v * (1.0f / 8192.0f); }
    case ST_HALF:  { uint16_t v; memcpy(&v, p, 2); return half_to_float(v); }
    case ST_S32FX: { int32_t v; memcpy(&v, p, 4); return (float)((double)v * (1.0 / 16777216.0)); }
    case ST_F32:   { float v; memcpy(&v, p, 4); return v; }
    }
    return 0.0f;
}

static int sample_size(enum SampleType t)
{
    return (t == ST_U8) ? 1 : (t == ST_S32FX || t == ST_F32) ? 4 : 2;
}

static float clamp01(float v)
{
    if (!(v > 0.0f)) return 0.0f;   /* also maps NaN to 0 */
    return v < 1.0f ? v : 1.0f;
}

static uint8_t to_u8(float v) { return (uint8_t)(clamp01(v) * 255.0f + 0.5f); }

/* sRGB OETF lookup: index = round(linear * 65535), value = round(oetf * 255). */
#define LUT_SIZE 65536
static void build_oetf_lut(uint8_t* lut)
{
    int i;
    for (i = 0; i < LUT_SIZE; ++i) {
        double l = (double)i / (LUT_SIZE - 1);
        double s = (l <= 0.0031308) ? 12.92 * l : 1.055 * pow(l, 1.0 / 2.4) - 0.055;
        lut[i] = (uint8_t)(s * 255.0 + 0.5);
    }
}

static uint8_t linear_to_srgb8(const uint8_t* lut, float v)
{
    return lut[(int)(clamp01(v) * (LUT_SIZE - 1) + 0.5f)];
}

/* ---- conversions ---- */
static int convert_generic(const FormatDesc* d, const uint8_t* src, size_t stride,
                           int w, int h, uint8_t* dst)
{
    const int linear = d->type != ST_U8 && d->type != ST_U16;
    const int ss = sample_size(d->type);
    uint8_t* lut = NULL;
    int x, y, c;
    if (linear) {
        lut = (uint8_t*)malloc(LUT_SIZE);
        if (!lut) return WOKA_JXR_ERR_NOMEM;
        build_oetf_lut(lut);
    }
    for (y = 0; y < h; ++y) {
        const uint8_t* row = src + (size_t)y * stride;
        uint8_t* out = dst + (size_t)y * w * 4;
        for (x = 0; x < w; ++x) {
            const uint8_t* px = row + (size_t)x * d->bytes_per_pixel;
            float rgb[3], a = 1.0f;
            if (d->channels == 1) {
                rgb[0] = rgb[1] = rgb[2] = read_sample(px, d->type);
            } else {
                for (c = 0; c < 3; ++c) rgb[c] = read_sample(px + c * ss, d->type);
                if (d->bgr) { float t = rgb[0]; rgb[0] = rgb[2]; rgb[2] = t; }
                if (d->channels == 4) a = read_sample(px + 3 * ss, d->type);
            }
            if (d->premultiplied) {
                if (a > 0.0f) for (c = 0; c < 3; ++c) rgb[c] /= a;
                else rgb[0] = rgb[1] = rgb[2] = 0.0f;
            }
            for (c = 0; c < 3; ++c) out[x * 4 + c] = linear ? linear_to_srgb8(lut, rgb[c]) : to_u8(rgb[c]);
            out[x * 4 + 3] = to_u8(a);
        }
    }
    free(lut);
    return WOKA_JXR_OK;
}

/* 16bppRGB555 / 16bppRGB565 / 32bppRGB101010. Despite the jxrlib names these GUIDs are WIC's
 * Bgr555/Bgr565/Bgr101010: with RED_BLUE_SWAPPED = 0 (what WIC writes) the decoder output has
 * B in the low bits (verified against WIC). With the flag set jxrlib swaps, so R is low.
 * Below, `lo` / `hi` name the low / high bit fields. */
static void convert_packed(const PKPixelFormatGUID* g, int rb_swapped, const uint8_t* src,
                           size_t stride, int w, int h, uint8_t* dst)
{
    int x, y;
    for (y = 0; y < h; ++y) {
        const uint8_t* row = src + (size_t)y * stride;
        uint8_t* out = dst + (size_t)y * w * 4;
        for (x = 0; x < w; ++x) {
            uint32_t lo, gr, hi;
            if (IsEqualGUID(g, &GUID_PKPixelFormat32bppRGB101010)) {
                uint32_t v; memcpy(&v, row + x * 4, 4);
                lo = ((v & 0x3FFu) * 255u + 511u) / 1023u;
                gr = (((v >> 10) & 0x3FFu) * 255u + 511u) / 1023u;
                hi = (((v >> 20) & 0x3FFu) * 255u + 511u) / 1023u;
            } else {
                uint16_t v; memcpy(&v, row + x * 2, 2);
                lo = ((v & 0x1Fu) * 255u + 15u) / 31u;
                if (IsEqualGUID(g, &GUID_PKPixelFormat16bppRGB565)) {
                    gr = (((v >> 5) & 0x3Fu) * 255u + 31u) / 63u;
                    hi = (((v >> 11) & 0x1Fu) * 255u + 15u) / 31u;
                } else {
                    gr = (((v >> 5) & 0x1Fu) * 255u + 15u) / 31u;
                    hi = (((v >> 10) & 0x1Fu) * 255u + 15u) / 31u;
                }
            }
            out[x * 4 + 0] = (uint8_t)(rb_swapped ? lo : hi);
            out[x * 4 + 1] = (uint8_t)gr;
            out[x * 4 + 2] = (uint8_t)(rb_swapped ? hi : lo);
            out[x * 4 + 3] = 255;
        }
    }
}

/* BlackWhite: 1 bpp, MSB first, 1 = white (jxrlib already applies the WhiteIsZero flag). */
static void convert_bw(const uint8_t* src, size_t stride, int w, int h, uint8_t* dst)
{
    int x, y;
    for (y = 0; y < h; ++y) {
        const uint8_t* row = src + (size_t)y * stride;
        uint8_t* out = dst + (size_t)y * w * 4;
        for (x = 0; x < w; ++x) {
            uint8_t v = ((row[x >> 3] >> (7 - (x & 7))) & 1) ? 255 : 0;
            out[x * 4 + 0] = out[x * 4 + 1] = out[x * 4 + 2] = v;
            out[x * 4 + 3] = 255;
        }
    }
}

/* 32bppRGBE (Radiance): linear = mantissa * 2^(e - 136), then sRGB OETF. */
static int convert_rgbe(const uint8_t* src, size_t stride, int w, int h, uint8_t* dst)
{
    uint8_t* lut = (uint8_t*)malloc(LUT_SIZE);
    int x, y, c;
    if (!lut) return WOKA_JXR_ERR_NOMEM;
    build_oetf_lut(lut);
    for (y = 0; y < h; ++y) {
        const uint8_t* row = src + (size_t)y * stride;
        uint8_t* out = dst + (size_t)y * w * 4;
        for (x = 0; x < w; ++x) {
            const uint8_t* p = row + x * 4;
            float f = p[3] ? (float)ldexp(1.0, (int)p[3] - 136) : 0.0f;
            for (c = 0; c < 3; ++c) out[x * 4 + c] = linear_to_srgb8(lut, (float)p[c] * f);
            out[x * 4 + 3] = 255;
        }
    }
    free(lut);
    return WOKA_JXR_OK;
}

/* ---- decoder plumbing ---- */
static int open_decoder(const uint8_t* data, size_t size, PKImageDecode** out)
{
    struct WMPStream* ws = NULL;
    PKImageDecode* dec = NULL;
    *out = NULL;
    if (CreateWS_Memory(&ws, (void*)data, size) != WMP_errSuccess) return WOKA_JXR_ERR_NOMEM;
    if (PKImageDecode_Create_WMP(&dec) != WMP_errSuccess) {
        ws->Close(&ws);
        return WOKA_JXR_ERR_NOMEM;
    }
    dec->fStreamOwner = !0;  /* Release() closes the memory stream (not the caller's bytes) */
    if (dec->Initialize(dec, ws) != WMP_errSuccess) {
        dec->pStream = ws;
        dec->Release(&dec);
        return WOKA_JXR_ERR_DECODE;
    }
    *out = dec;
    return WOKA_JXR_OK;
}

static int checked_size(PKImageDecode* dec, int* w, int* h)
{
    I32 iw = 0, ih = 0;
    if (dec->GetSize(dec, &iw, &ih) != WMP_errSuccess || iw <= 0 || ih <= 0) return WOKA_JXR_ERR_DECODE;
    if ((size_t)iw * (size_t)ih > WOKA_JXR_MAX_PIXELS) return WOKA_JXR_ERR_TOO_LARGE;
    *w = (int)iw;
    *h = (int)ih;
    return WOKA_JXR_OK;
}

/* jxrlib's memory stream silently clamps reads past the end, so a truncated file would decode
 * to garbage with success. Require the image (and planar alpha) payloads to be inside `size`. */
static int payload_complete(PKImageDecode* dec, size_t size)
{
    const WmpDEMisc* m = &dec->WMP.wmiDEMisc;
    if ((uint64_t)m->uImageOffset + m->uImageByteCount > size) return 0;
    if (dec->WMP.bHasAlpha && (uint64_t)m->uAlphaOffset + m->uAlphaByteCount > size) return 0;
    return 1;
}

/* YUV-tagged and 8-bit CMYK files are decoded straight to 24bppRGB by jxrlib's own colour
 * conversion (the same override JxrDecApp applies for CMYK -> RGB). */
static int needs_rgb_output(const PKPixelFormatGUID* g)
{
    return IsEqualGUID(g, &GUID_PKPixelFormat12bppYUV420) || IsEqualGUID(g, &GUID_PKPixelFormat16bppYUV422)
        || IsEqualGUID(g, &GUID_PKPixelFormat24bppYUV444) || IsEqualGUID(g, &GUID_PKPixelFormat32bppCMYK);
}

static int configure(PKImageDecode* dec, PKPixelInfo* pi)
{
    PKPixelFormatGUID fmt = dec->guidPixFormat;
    if (needs_rgb_output(&fmt)) {
        fmt = GUID_PKPixelFormat24bppRGB;
        dec->guidPixFormat = fmt;
        dec->WMP.wmiI.bRGB = 1;
    }
    pi->pGUIDPixFmt = &dec->guidPixFormat;
    if (PixelFormatLookup(pi, LOOKUP_FORWARD) != WMP_errSuccess) return WOKA_JXR_ERR_UNSUPPORTED;
    if (pi->cfColorFormat == NCOMPONENT || pi->cfColorFormat == CMYK) return WOKA_JXR_ERR_UNSUPPORTED;

    dec->WMP.wmiSCP.uAlphaMode = (pi->grBit & PK_pixfmtHasAlpha) ? 2 : 0;
    dec->WMP.wmiI.cfColorFormat = pi->cfColorFormat;
    dec->WMP.wmiI.bdBitDepth = pi->bdBitDepth;
    dec->WMP.wmiI.cBitsPerUnit = pi->cbitUnit;
    dec->WMP.wmiI.cThumbnailWidth = dec->WMP.wmiI.cWidth;
    dec->WMP.wmiI.cThumbnailHeight = dec->WMP.wmiI.cHeight;
    dec->WMP.wmiI.bSkipFlexbits = FALSE;
    dec->WMP.wmiI.cROILeftX = 0;
    dec->WMP.wmiI.cROITopY = 0;
    dec->WMP.wmiI.cROIWidth = dec->WMP.wmiI.cWidth;
    dec->WMP.wmiI.cROIHeight = dec->WMP.wmiI.cHeight;
    return WOKA_JXR_OK;
}

/* RED_BLUE_SWAPPED bit of the JPEG XR image header: header = "WMPHOTO\0", then byte 8
 * (version), byte 9 (tiling/orientation/overlap), byte 10 = SHORT_HEADER, LONG_WORD, WINDOWING,
 * TRIM_FLEXBITS, TILE_STRETCH, RED_BLUE_SWAPPED, reserved, ALPHA (MSB first) — the order
 * jxrlib's ReadImageHeader() reads them in. */
static int rb_swapped(PKImageDecode* dec, const uint8_t* data, size_t size)
{
    size_t off = (size_t)dec->WMP.wmiDEMisc.uImageOffset + 10;
    return off < size ? (data[off] >> 2) & 1 : 0;
}

static int convert(const PKPixelFormatGUID* g, int swapped, const uint8_t* src, size_t stride,
                   int w, int h, uint8_t* dst)
{
    size_t i;
    for (i = 0; i < sizeof kGeneric / sizeof kGeneric[0]; ++i)
        if (IsEqualGUID(g, kGeneric[i].guid)) return convert_generic(&kGeneric[i], src, stride, w, h, dst);
    if (IsEqualGUID(g, &GUID_PKPixelFormatBlackWhite)) { convert_bw(src, stride, w, h, dst); return WOKA_JXR_OK; }
    if (IsEqualGUID(g, &GUID_PKPixelFormat32bppRGBE)) return convert_rgbe(src, stride, w, h, dst);
    if (IsEqualGUID(g, &GUID_PKPixelFormat16bppRGB555) || IsEqualGUID(g, &GUID_PKPixelFormat16bppRGB565)
        || IsEqualGUID(g, &GUID_PKPixelFormat32bppRGB101010)) {
        convert_packed(g, swapped, src, stride, w, h, dst);
        return WOKA_JXR_OK;
    }
    return WOKA_JXR_ERR_UNSUPPORTED;
}

/* ---- public API ---- */
WOKA_JXR_API int woka_jxr_info(const uint8_t* data, size_t size, int* out_width, int* out_height)
{
    PKImageDecode* dec = NULL;
    int w = 0, h = 0, rc;
    if (!data || size == 0 || !out_width || !out_height) return WOKA_JXR_ERR_ARGS;
    rc = open_decoder(data, size, &dec);
    if (rc != WOKA_JXR_OK) return rc;
    rc = checked_size(dec, &w, &h);
    dec->Release(&dec);
    if (rc != WOKA_JXR_OK) return rc;
    *out_width = w;
    *out_height = h;
    return WOKA_JXR_OK;
}

WOKA_JXR_API int woka_jxr_decode(const uint8_t* data, size_t size,
                                 int* out_width, int* out_height, uint8_t** out_rgba)
{
    PKImageDecode* dec = NULL;
    PKPixelInfo pi;
    PKRect rect;
    U8* native = NULL;
    uint8_t* rgba = NULL;
    size_t stride;
    int w = 0, h = 0, rc;

    if (!data || size == 0 || !out_width || !out_height || !out_rgba) return WOKA_JXR_ERR_ARGS;
    *out_rgba = NULL;
    rc = open_decoder(data, size, &dec);
    if (rc != WOKA_JXR_OK) return rc;
    rc = checked_size(dec, &w, &h);
    if (rc == WOKA_JXR_OK && !payload_complete(dec, size)) rc = WOKA_JXR_ERR_DECODE;
    if (rc == WOKA_JXR_OK) rc = configure(dec, &pi);
    if (rc != WOKA_JXR_OK) goto done;

    /* Same stride rule as PKImageEncode_WriteSource; 16 spare rows guard against MB-row overrun. */
    stride = (pi.bdBitDepth == BD_1) ? ((size_t)w + 7) / 8 : (size_t)((pi.cbitUnit + 7) / 8) * (size_t)w;
    if (PKAllocAligned((void**)&native, stride * ((size_t)h + 16), 128) != WMP_errSuccess || !native) {
        rc = WOKA_JXR_ERR_NOMEM;
        goto done;
    }
    memset(native, 0, stride * ((size_t)h + 16));
    rect.X = 0;
    rect.Y = 0;
    rect.Width = w;
    rect.Height = h;
    if (dec->Copy(dec, &rect, native, (U32)stride) != WMP_errSuccess) {
        rc = WOKA_JXR_ERR_DECODE;
        goto done;
    }
    rgba = (uint8_t*)malloc((size_t)w * (size_t)h * 4);
    if (!rgba) {
        rc = WOKA_JXR_ERR_NOMEM;
        goto done;
    }
    rc = convert(&dec->guidPixFormat, rb_swapped(dec, data, size), native, stride, w, h, rgba);
    if (rc == WOKA_JXR_OK) {
        *out_width = w;
        *out_height = h;
        *out_rgba = rgba;
        rgba = NULL;
    }

done:
    free(rgba);
    if (native) PKFreeAligned((void**)&native);
    if (dec) dec->Release(&dec);
    return rc;
}

WOKA_JXR_API void woka_jxr_free(uint8_t* p)
{
    free(p);
}
