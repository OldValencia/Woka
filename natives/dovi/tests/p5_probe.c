/* DV5-101 end-to-end research bridge. Not packaged or linked by the app.
 * One owner calls convert; internal row workers finish before it returns.
 * All frame pointers are borrowed for that call. Buffers have a fixed capacity.
 */
#include <stdlib.h>
#include "libavutil/frame.h"
#include "libavutil/version.h"
#include "woka_dovi_metadata.h"
#include "p5_simd.h"

struct probe {
    int w, h, threads;
    uint8_t *rgba;
    struct p5_tables tables;
    struct p5_lut scalar;
    struct p5_simd vector;
};

WOKA_DOVI_EXPORT void woka_p5_probe_destroy(struct probe *p)
{
    if (!p) return;
    free(p->rgba);
    free(p);
}

WOKA_DOVI_EXPORT struct probe *woka_p5_probe_create(int w, int h, int threads)
{
    struct probe *p;
    size_t pixels;
    if (w < 8 || h < 2 || w > 8192 || h > 8192 || w%8 || h%2 || threads < 1 || threads > 8)
        return NULL;
    if (!p5_simd_supported()) return NULL;
    p = calloc(1, sizeof(*p));
    if (!p) return NULL;
    pixels = (size_t)w*h;
    p->w = w; p->h = h; p->threads = threads;
    p->rgba = malloc(pixels*4);
    if (!p->rgba) { woka_p5_probe_destroy(p); return NULL; }
    p5_tables_init(&p->tables);
    p5_simd_tables(&p->vector, &p->tables);
    return p;
}

WOKA_DOVI_EXPORT const uint8_t *woka_p5_probe_pixels(const struct probe *p)
{
    return p ? p->rgba : NULL;
}

WOKA_DOVI_EXPORT int woka_p5_probe_convert(struct probe *p, const AVFrame *frame, unsigned version)
{
    const AVFrameSideData *side = NULL;
    double meta[704];
    struct p5_image image;
    int i, row, status;
    if (version != LIBAVUTIL_VERSION_INT) return -2;
    if (!p || !frame || frame->format != AV_PIX_FMT_YUV420P10LE ||
        frame->width != p->w || frame->height != p->h ||
        frame->chroma_location != AVCHROMA_LOC_LEFT) return -1;
    if (frame->nb_side_data < 0 || frame->nb_side_data > 256) return -3;
    for (i = 0; i < frame->nb_side_data; ++i) {
        if (!frame->side_data[i]) return -3;
        if (frame->side_data[i]->type == AV_FRAME_DATA_DOVI_METADATA) {
            if (side) return -3;
            side = frame->side_data[i];
        }
    }
    if (!side) return -3;
    status = woka_dovi_metadata(side->data, side->size, version, meta, 704);
    if (status) return status;
    if (meta[1] != 10 || meta[2] != 1 || meta[10] != 1 || meta[11] || meta[12] || meta[13] != -1)
        return -4;
    for (i = 0; i < 3; ++i) {
        int width = i ? p->w/2 : p->w;
        if (!frame->data[i] || frame->linesize[i] < width*2 || frame->linesize[i]%2) return -1;
        image.plane[i] = (const uint16_t *)frame->data[i];
        image.stride[i] = frame->linesize[i]/2;
    }
    image.w = p->w; image.h = p->h;
    p5_lut_init(&p->scalar, &p->tables, meta);
    if (!p5_simd_prepare(&p->vector, &p->scalar)) return -5;
#pragma omp parallel for schedule(static) num_threads(p->threads)
    for (row = 0; row < p->h; ++row)
        p5_simd_image_row(&p->vector, &image, p->rgba, row);
    return 0;
}
