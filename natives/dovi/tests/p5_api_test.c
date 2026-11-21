#include "woka_p5.h"
#include <libavutil/dovi_meta.h>
#include <libavutil/frame.h>
#include <libavutil/version.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct Metadata {
    AVDOVIMetadata root;
    AVDOVIRpuDataHeader header;
    AVDOVIDataMapping mapping;
    AVDOVIColorMetadata color;
} Metadata;

static int checks, failures;
#define CHECK(condition) do { ++checks; if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); ++failures; } } while (0)

static Metadata metadata(void)
{
    Metadata m = {0};
    int i;
    m.root.header_offset = offsetof(Metadata, header);
    m.root.mapping_offset = offsetof(Metadata, mapping);
    m.root.color_offset = offsetof(Metadata, color);
    m.header.bl_bit_depth = 10;
    m.header.bl_video_full_range_flag = 1;
    m.header.disable_residual_flag = 1;
    m.header.rpu_type = 2;
    m.header.rpu_format = 18;
    m.header.vdr_rpu_normalized_idc = 1;
    m.header.coef_log2_denom = 23;
    m.mapping.nlq_method_idc = AV_DOVI_NLQ_NONE;
    m.color.signal_eotf = 65535;
    m.color.signal_bit_depth = 12;
    m.color.signal_color_space = 2;
    m.color.signal_full_range_flag = 1;
    m.color.source_max_pq = 3079;
    for (i = 0; i < 3; ++i) {
        m.mapping.curves[i].num_pivots = 2;
        m.mapping.curves[i].pivots[1] = 1023;
        m.mapping.curves[i].poly_order[0] = 1;
        m.mapping.curves[i].poly_coef[0][1] = 1 << 23;
        m.color.ycc_to_rgb_offset[i].den = 1;
    }
    for (i = 0; i < 9; ++i) {
        m.color.ycc_to_rgb_matrix[i].den = 1;
        m.color.rgb_to_lms_matrix[i].den = 1;
        m.color.ycc_to_rgb_matrix[i].num = i % 4 == 0;
        m.color.rgb_to_lms_matrix[i].num = i % 4 == 0;
    }
    return m;
}

int main(void)
{
    Metadata m = metadata();
    AVFrame frame = {0};
    AVFrameSideData side = {0}, *sides[2] = {&side, &side};
    uint16_t planes[3][32];
    unsigned char out[192], before[192], full[192];
    void *context = woka_p5_create(LIBAVUTIL_VERSION_INT);
    int i, width, location;
    CHECK(context != NULL);
    CHECK(woka_p5_create(0) == NULL);
    CHECK(woka_p5_version(0) == -2);
    CHECK(woka_p5_prepare(context, (void *)1, 0) == -2); /* Version before frame dereference. */
    CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 4) == -5);
    CHECK(woka_p5_gpu(context, out, sizeof(out), 48, 8, 4) == -5);
    frame.width = 8; frame.height = 4; frame.format = AV_PIX_FMT_YUV420P10LE;
    frame.chroma_location = AVCHROMA_LOC_LEFT;
    frame.nb_side_data = 1; frame.side_data = sides;
    side.type = AV_FRAME_DATA_DOVI_METADATA;
    side.data = (uint8_t *)&m; side.size = sizeof(m);
    for (i = 0; i < 3; ++i) {
        int j;
        frame.data[i] = (uint8_t *)planes[i]; frame.linesize[i] = 16;
        for (j = 0; j < 32; ++j) planes[i][j] = (uint16_t)((j*103+i*73)%1024);
    }
    for (width = 6; width <= 8; width += 2) {
        frame.width = width; /* Scalar fallback and AVX2-eligible geometry. */
        for (location = AVCHROMA_LOC_LEFT; location <= AVCHROMA_LOC_BOTTOM; ++location) {
            frame.chroma_location = location;
            CHECK(woka_p5_prepare(context, &frame, LIBAVUTIL_VERSION_INT) == 0);
            memset(out, 0x5a, sizeof(out)); memcpy(before, out, sizeof(out));
            CHECK(woka_p5_rows(context, out, 1, 48, 0, 4) == -1);
            CHECK(woka_p5_rows(context, out, sizeof(out), 1, 0, 4) == -1);
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, -1, 4) == -1);
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 5) == -1);
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, 3, 2) == -1);
            CHECK(memcmp(out, before, sizeof(out)) == 0);
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 4) == 0);
            memcpy(full, out, sizeof(out));
            if (width == 8 && location == AVCHROMA_LOC_LEFT) {
                int status;
                memset(out, 0x5a, sizeof(out));
                status = woka_p5_gpu(context, out, sizeof(out), 48, width, 4);
                CHECK(status == 0 || status == 1);
                if (status == 0) {
                    for (i = 0; i < 192; ++i) {
                        if (i%48 >= width*4) CHECK(out[i] == 0x5a);
                        else CHECK(abs((int)out[i] - full[i]) <= 1);
                    }
                }
                printf("GPU strided output status: %d (1 = unavailable)\n", status);
            }
            memset(out, 0x5a, sizeof(out));
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, 2, 4) == 0);
            CHECK(memcmp(out, before, 96) == 0);
            CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 2) == 0);
            CHECK(memcmp(full, out, sizeof(out)) == 0);
            for (i = 0; i < 192; ++i) {
                if (i%48 >= width*4) CHECK(out[i] == 0x5a);
                else if (i%4 == 3) CHECK(out[i] == 255);
            }
        }
    }
    frame.chroma_location = AVCHROMA_LOC_LEFT;
#define REJECT(change, undo, status) do { \
    CHECK(woka_p5_prepare(context, &frame, LIBAVUTIL_VERSION_INT) == 0); \
    change; \
    CHECK(woka_p5_prepare(context, &frame, LIBAVUTIL_VERSION_INT) == status); \
    memcpy(before, out, sizeof(out)); \
    CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 4) == -5); \
    CHECK(woka_p5_gpu(context, out, sizeof(out), 48, 8, 4) == -5); \
    CHECK(memcmp(out, before, sizeof(out)) == 0); \
    undo; } while (0)
    REJECT(frame.width = 7, frame.width = 8, -1);
    REJECT(frame.height = 8194, frame.height = 4, -1);
    REJECT(frame.linesize[1] = -16, frame.linesize[1] = 16, -1);
    REJECT(frame.data[2] = NULL, frame.data[2] = (uint8_t *)planes[2], -1);
    REJECT(frame.format = AV_PIX_FMT_P010LE, frame.format = AV_PIX_FMT_YUV420P10LE, -4);
    REJECT(frame.chroma_location = AVCHROMA_LOC_UNSPECIFIED, frame.chroma_location = AVCHROMA_LOC_LEFT, -4);
    REJECT(frame.nb_side_data = 0, frame.nb_side_data = 1, -3);
    REJECT(frame.nb_side_data = 2, frame.nb_side_data = 1, -3);
    REJECT(side.size = 1, side.size = sizeof(m), -3);
    REJECT(m.header.disable_residual_flag = 0, m.header.disable_residual_flag = 1, -4);
    REJECT(m.color.ycc_to_rgb_matrix[0].num = 17, m.color.ycc_to_rgb_matrix[0].num = 1, -4);
    CHECK(woka_p5_prepare(context, &frame, LIBAVUTIL_VERSION_INT) == 0);
    memcpy(before, out, sizeof(out));
    CHECK(woka_p5_gpu(context, out, 1, 48, 8, 4) == -1);
    CHECK(woka_p5_gpu(context, out, sizeof(out), 1, 8, 4) == -1);
    CHECK(woka_p5_gpu(context, out, sizeof(out), 48, 8193, 4) == -1);
    CHECK(woka_p5_gpu(context, out, sizeof(out), 48, 0, 4) == -1);
    CHECK(memcmp(before, out, sizeof(out)) == 0);
    frame.chroma_location = AVCHROMA_LOC_CENTER;
    CHECK(woka_p5_prepare(context, &frame, LIBAVUTIL_VERSION_INT) == 0);
    CHECK(woka_p5_gpu(context, out, sizeof(out), 48, 8, 4) == 1);
    CHECK(memcmp(before, out, sizeof(out)) == 0);
    woka_p5_finish(context);
    CHECK(woka_p5_rows(context, out, sizeof(out), 48, 0, 4) == -5);
    woka_p5_destroy(context);
    woka_p5_finish(NULL);
    woka_p5_destroy(NULL);
    printf("%d checks, %d failures\n", checks, failures);
    return failures != 0;
}
