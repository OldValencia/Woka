#include <stdlib.h>
#include "libavutil/frame.h"
#include "libavutil/version.h"
#include "woka_p5.h"
#include "woka_p5_math.h"
#ifdef WOKA_P5_D3D11
#include "woka_p5_gpu.h"
#endif
#ifdef WOKA_P5_SIMD
#include "woka_p5_simd.h"
#endif
#ifdef WOKA_P5_D3D11
/* Keep the embedded shader's lookup layout tied to the checked CPU tables. */
_Static_assert(P5_EOTF_STEPS == 65536 && P5_OETF_STEPS == 50176 &&
    P5_OETF_BASE == 0x28000000u && P5_OETF_SHIFT == 13, "Update the GPU PQ lookup layout");
_Static_assert(sizeof(((struct p5_simd *)0)->curves) / sizeof(float) == 3 * 8193,
    "Update the GPU reshape lookup layout");
#endif

struct converter {
#ifdef WOKA_P5_D3D11
    void *gpu;
    int gpu_attempted;
#endif
    struct p5_tables tables;
    struct p5_lut math;
    const uint16_t *plane[3];
    int stride[3], w, h, ready;
    double chroma_x, chroma_y;
#ifdef WOKA_P5_SIMD
    struct p5_simd vector;
    int capable, vector_ready;
#endif
};

int woka_p5_version(unsigned version) { return version == LIBAVUTIL_VERSION_INT ? 1 : -2; }

void *woka_p5_create(unsigned version)
{
    struct converter *p;
    if (woka_p5_version(version) != 1) return NULL;
    p = calloc(1, sizeof(*p));
    if (!p) return NULL;
    p5_tables_init(&p->tables);
#ifdef WOKA_P5_SIMD
    p->capable = p5_simd_supported();
    if (p->capable) p5_simd_tables(&p->vector, &p->tables);
#endif
    return p;
}

void woka_p5_destroy(void *context) {
#ifdef WOKA_P5_D3D11
    struct converter *p = context;
    if (p) p5_gpu_destroy(p->gpu);
#endif
    free(context);
}

int woka_p5_gpu(void *context, void *output, uint64_t size, int stride, int width, int height)
{
    struct converter *p = context;
    if (!p || !p->ready) return -5;
    if (!output || width < 1 || height < 1 || width > 8192 || height > 8192 || stride < width*4 ||
        size < (uint64_t)(height-1)*(unsigned)stride+(unsigned)width*4) return -1;
#ifdef WOKA_P5_D3D11
    if (p->vector_ready && width*8 >= p->w && height*8 >= p->h) {
        struct p5_gpu_input input;
        int c;
        if (!p->gpu_attempted) {
            p->gpu_attempted = 1;
            p->gpu = p5_gpu_create();
        }
        if (!p->gpu) return 1;
        for (c = 0; c < 3; ++c) { input.plane[c] = p->plane[c]; input.stride[c] = p->stride[c]; }
        input.width = p->w; input.height = p->h;
        input.curves = &p->vector.curves[0][0]; input.eotf = p->vector.eotf; input.oetf = p->vector.oetf;
        input.nonlinear = p->vector.nonlinear; input.bias = p->vector.bias; input.linear = p->vector.linear;
        if (p5_gpu_render(p->gpu, &input, output, stride, width, height) == 0) return 0;
        p5_gpu_destroy(p->gpu);
        p->gpu = NULL;
    }
#endif
    return 1;
}

void woka_p5_finish(void *context)
{
    struct converter *p = context;
    if (!p) return;
    p->ready = 0;
    p->plane[0] = p->plane[1] = p->plane[2] = NULL;
}

int woka_p5_prepare(void *context, const void *picture, unsigned version)
{
    struct converter *p = context;
    const AVFrame *frame = picture;
    const AVFrameSideData *side = NULL;
    double meta[704];
    int c;
    woka_p5_finish(context);
    if (woka_p5_version(version) != 1) return -2;
    if (!p || !frame || frame->width < 2 || frame->height < 2 ||
        frame->width > 8192 || frame->height > 8192 || frame->width%2 || frame->height%2) return -1;
    if (frame->format != AV_PIX_FMT_YUV420P10LE) return -4;
    if (frame->nb_side_data < 1 || frame->nb_side_data > 256 || !frame->side_data) return -3;
    for (c = 0; c < frame->nb_side_data; ++c) {
        if (!frame->side_data[c]) return -3;
        if (frame->side_data[c]->type == AV_FRAME_DATA_DOVI_METADATA) {
            if (side) return -3;
            side = frame->side_data[c];
        }
    }
    if (!side || woka_dovi_metadata(side->data, side->size, version, meta, 704)) return -3;
    if (meta[1] != 10 || meta[2] != 1 || meta[10] != 1 || meta[11] || meta[12] ||
        meta[13] != -1 || meta[14] || meta[15] || meta[37] != 65535 || meta[39] != 2 ||
        meta[40] || meta[41] != 1 || meta[44] != 2 || meta[45] != 18 || meta[46] ||
        meta[48] || meta[49] || meta[50] != 1 || meta[51] || meta[52]) return -4;
    for (c = 16; c <= 36; ++c) if (!isfinite(meta[c]) || fabs(meta[c]) > 16) return -4;
    switch (frame->chroma_location) {
    case AVCHROMA_LOC_LEFT: p->chroma_x = 0; p->chroma_y = -0.25; break;
    case AVCHROMA_LOC_CENTER: p->chroma_x = -0.25; p->chroma_y = -0.25; break;
    case AVCHROMA_LOC_TOPLEFT: p->chroma_x = 0; p->chroma_y = 0; break;
    case AVCHROMA_LOC_TOP: p->chroma_x = -0.25; p->chroma_y = 0; break;
    case AVCHROMA_LOC_BOTTOMLEFT: p->chroma_x = 0; p->chroma_y = -0.5; break;
    case AVCHROMA_LOC_BOTTOM: p->chroma_x = -0.25; p->chroma_y = -0.5; break;
    default: return -4;
    }
    for (c = 0; c < 3; ++c) {
        int width = c ? frame->width/2 : frame->width;
        if (!frame->data[c] || ((uintptr_t)frame->data[c] & 1) ||
            frame->linesize[c] < width*2 || frame->linesize[c]%2) return -1;
    }
    p5_lut_init(&p->math, &p->tables, meta);
#ifdef WOKA_P5_SIMD
    p->vector_ready = p->capable && frame->width%8 == 0 && frame->chroma_location == AVCHROMA_LOC_LEFT &&
        p5_simd_prepare(&p->vector, &p->math);
#endif
    for (c = 0; c < 3; ++c) {
        p->plane[c] = (const uint16_t *)frame->data[c];
        p->stride[c] = frame->linesize[c]/2;
    }
    p->w = frame->width; p->h = frame->height;
    p->ready = 1;
    return 0;
}

static double sample(const struct converter *p, int c, double x, double y)
{
    int x0, y0, x1, y1;
    double fx, fy;
    const uint16_t *plane = p->plane[c];
    size_t stride = (size_t)p->stride[c];
    x = p5_clamp(x, 0, p->w/2-1); y = p5_clamp(y, 0, p->h/2-1);
    x0 = (int)x; y0 = (int)y;
    x1 = x0+1 < p->w/2 ? x0+1 : x0; y1 = y0+1 < p->h/2 ? y0+1 : y0;
    fx = x-x0; fy = y-y0;
    return (1-fy)*((1-fx)*plane[y0*stride+x0]+fx*plane[y0*stride+x1]) +
        fy*((1-fx)*plane[y1*stride+x0]+fx*plane[y1*stride+x1]);
}

int woka_p5_rows(const void *context, void *output, uint64_t size, int stride, int first, int end)
{
    const struct converter *p = context;
    uint8_t *rgba = output;
    int y, x, c;
    if (!p || !p->ready) return -5;
    if (!output || stride < p->w*4 || first < 0 || end < first || end > p->h ||
        size < (uint64_t)(p->h-1)*(unsigned)stride+(unsigned)p->w*4) return -1;
    for (y = first; y < end; ++y) {
#ifdef WOKA_P5_SIMD
        if (p->vector_ready) {
            struct p5_image image = {{p->plane[0], p->plane[1], p->plane[2]},
                {p->stride[0], p->stride[1], p->stride[2]}, p->w, p->h};
            p5_simd_image_row(&p->vector, &image, rgba+(size_t)y*(stride-p->w*4), y);
            continue;
        }
#endif
        for (x = 0; x < p->w; ++x) {
            double input[3], rgb[3];
            size_t offset = (size_t)y*(unsigned)stride+(unsigned)x*4;
            input[0] = p->plane[0][(size_t)y*p->stride[0]+x];
            input[1] = sample(p, 1, x*0.5+p->chroma_x, y*0.5+p->chroma_y);
            input[2] = sample(p, 2, x*0.5+p->chroma_x, y*0.5+p->chroma_y);
            p5_lut_pixel(&p->math, input, rgb);
            for (c = 0; c < 3; ++c) rgba[offset+c] = (uint8_t)(p5_clamp(rgb[c], 0, 1)*255+0.5);
            rgba[offset+3] = 255;
        }
    }
    return 0;
}
