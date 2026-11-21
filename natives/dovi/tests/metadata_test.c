#include "woka_dovi_metadata.h"
#include <libavutil/dovi_meta.h>
#include <libavutil/version.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

typedef struct Fixture {
    AVDOVIMetadata root;
    AVDOVIRpuDataHeader header;
    AVDOVIDataMapping mapping;
    AVDOVIColorMetadata color;
    AVDOVIDmData ext[2];
} Fixture;

static int checks;
static int failures;
#define CHECK(condition) do { ++checks; if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); ++failures; } } while (0)

static Fixture fixture(void)
{
    Fixture f = {0};
    int c, i;
    f.root.header_offset = offsetof(Fixture, header);
    f.root.mapping_offset = offsetof(Fixture, mapping);
    f.root.color_offset = offsetof(Fixture, color);
    f.root.ext_block_offset = offsetof(Fixture, ext);
    f.root.ext_block_size = sizeof(AVDOVIDmData);
    f.root.num_ext_blocks = 1;
    f.header.bl_bit_depth = 10;
    f.header.coef_log2_denom = 23;
    f.header.bl_video_full_range_flag = 1;
    f.header.disable_residual_flag = 1;
    f.mapping.nlq_method_idc = AV_DOVI_NLQ_NONE;
    for (c = 0; c < 3; ++c) {
        f.mapping.curves[c].num_pivots = 2;
        f.mapping.curves[c].pivots[1] = 1023;
        f.mapping.curves[c].poly_order[0] = 1;
        f.mapping.curves[c].poly_coef[0][1] = 1 << 23;
        f.color.ycc_to_rgb_offset[c].den = 1;
    }
    for (i = 0; i < 9; ++i) {
        f.color.ycc_to_rgb_matrix[i].den = 1;
        f.color.rgb_to_lms_matrix[i].den = 1;
        f.color.ycc_to_rgb_matrix[i].num = i % 4 == 0;
        f.color.rgb_to_lms_matrix[i].num = i % 4 == 0;
    }
    f.color.source_max_pq = 3079;
    f.ext[0].level = 1;
    f.ext[0].l1.max_pq = 2048;
    f.ext[0].l1.avg_pq = 1024;
    return f;
}

static void rejected(const void *data, size_t size, unsigned version, int expected)
{
    double out[WOKA_DOVI_VALUES], before[WOKA_DOVI_VALUES];
    memset(out, 0x5a, sizeof(out));
    memcpy(before, out, sizeof(out));
    CHECK(woka_dovi_metadata(data, size, version, out, WOKA_DOVI_VALUES) == expected);
    CHECK(memcmp(out, before, sizeof(out)) == 0);
}

int main(void)
{
    Fixture f = fixture();
    double out[WOKA_DOVI_VALUES];
    size_t n;
    CHECK(woka_dovi_metadata(&f, sizeof(f), LIBAVUTIL_VERSION_INT, out, WOKA_DOVI_VALUES) == 0);
    CHECK(out[0] == 1 && out[1] == 10 && out[5] == 3079);
    CHECK(out[6] == 1 && out[8] == 2048 && out[9] == 1024);
    CHECK(out[64] == 2 && out[66] == 1023 && out[77] == 1);
    CHECK(out[16] == 1 && out[17] == 0 && out[36] == 1);
    CHECK(woka_dovi_metadata(&f, sizeof(f), LIBAVUTIL_VERSION_INT, NULL, WOKA_DOVI_VALUES) == -3);
    CHECK(woka_dovi_metadata(&f, sizeof(f), LIBAVUTIL_VERSION_INT, out, 0) == -3);
    rejected(NULL, 0, LIBAVUTIL_VERSION_INT, -1);
    rejected(NULL, UINT64_MAX, 0, -2);
    for (n = 0; n < sizeof(AVDOVIMetadata); ++n)
        rejected(&f, n, LIBAVUTIL_VERSION_INT, -1);
    for (n = 0; n < sizeof(f.ext[0]); ++n)
        rejected(&f, offsetof(Fixture, ext)+n, LIBAVUTIL_VERSION_INT, -1);
    f.root.mapping_offset = SIZE_MAX;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.color_offset = f.root.mapping_offset;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.header_offset = 0;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); ++f.root.mapping_offset;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.num_ext_blocks = -1;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.num_ext_blocks = 33;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.ext_block_size = SIZE_MAX;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.root.ext_block_offset = f.root.color_offset;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -1);
    f = fixture(); f.header.bl_bit_depth = 32;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.header.coef_log2_denom = 255;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.color.ycc_to_rgb_matrix[8].den = 0;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.color.rgb_to_lms_matrix[0].den = -1;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.color.ycc_to_rgb_offset[2].den = 0;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[0].num_pivots = 10;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[1].pivots[1] = 0;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[2].pivots[1] = 1024;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[2].mapping_idc[0] = 7;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[2].poly_order[0] = 3;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.mapping.curves[2].mapping_idc[0] = AV_DOVI_MAPPING_MMR;
    f.mapping.curves[2].mmr_order[0] = 4;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f.mapping.curves[2].mmr_order[0] = 3;
    f.mapping.curves[2].mmr_coef[0][2][6] = -(1 << 22);
    CHECK(woka_dovi_metadata(&f, sizeof(f), LIBAVUTIL_VERSION_INT, out, WOKA_DOVI_VALUES) == 0);
    CHECK(out[64+420+10+3+14+6] == -0.5);
    f = fixture(); f.root.num_ext_blocks = 2; f.ext[1] = f.ext[0];
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.ext[0].l1.max_pq = 4096;
    rejected(&f, sizeof(f), LIBAVUTIL_VERSION_INT, -4);
    f = fixture(); f.root.num_ext_blocks = 0;
    CHECK(woka_dovi_metadata(&f, sizeof(f), LIBAVUTIL_VERSION_INT, out, WOKA_DOVI_VALUES) == 0);
    CHECK(out[6] == 0 && out[8] == 0);
    printf("DV5 native boundary checks: %d; failures: %d\n", checks, failures);
    return failures ? 1 : 0;
}
