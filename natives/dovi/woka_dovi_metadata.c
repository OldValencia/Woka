#include "woka_dovi_metadata.h"
#include <libavutil/dovi_meta.h>
#include <libavutil/version.h>
#include <math.h>
#include <stddef.h>
#include <string.h>

#ifdef _MSC_VER
#define ALIGNMENT(T) __alignof(T)
#else
#define ALIGNMENT(T) _Alignof(T)
#endif

static int region(uint64_t size, size_t offset, size_t length, size_t alignment)
{
    return offset >= sizeof(AVDOVIMetadata) && offset % alignment == 0 &&
        offset <= size && length <= size - offset;
}

static int disjoint(size_t a, size_t an, size_t b, size_t bn)
{
    return a <= b ? an <= b - a : bn <= a - b;
}

static int rational(AVRational value, double *result)
{
    if (value.den <= 0) return 0;
    *result = (double)value.num / value.den;
    return isfinite(*result);
}

static int curve(const AVDOVIReshapingCurve *in, unsigned bits, unsigned denom, double *out)
{
    int i, j, k;
    if (in->num_pivots < 2 || in->num_pivots > 9) return 0;
    out[0] = in->num_pivots;
    for (i = 0; i < in->num_pivots; ++i) {
        if (in->pivots[i] >= (1u << bits) || (i && in->pivots[i] <= in->pivots[i-1]))
            return 0;
        out[1+i] = in->pivots[i];
    }
    for (i = 0; i < in->num_pivots - 1; ++i) {
        double *piece = out + 10 + 25*i;
        piece[0] = in->mapping_idc[i];
        if (in->mapping_idc[i] == AV_DOVI_MAPPING_POLYNOMIAL) {
            if (in->poly_order[i] < 1 || in->poly_order[i] > 2) return 0;
            piece[1] = in->poly_order[i];
            for (j = 0; j <= in->poly_order[i]; ++j)
                piece[2+j] = ldexp((double)in->poly_coef[i][j], -(int)denom);
        } else if (in->mapping_idc[i] == AV_DOVI_MAPPING_MMR) {
            if (in->mmr_order[i] < 1 || in->mmr_order[i] > 3) return 0;
            piece[1] = in->mmr_order[i];
            piece[2] = ldexp((double)in->mmr_constant[i], -(int)denom);
            for (j = 0; j < in->mmr_order[i]; ++j)
                for (k = 0; k < 7; ++k)
                    piece[3+7*j+k] = ldexp((double)in->mmr_coef[i][j][k], -(int)denom);
        } else return 0;
    }
    return 1;
}

int woka_dovi_metadata(const void *data, uint64_t size, unsigned version,
    double *output, uint64_t output_count)
{
    AVDOVIMetadata root;
    AVDOVIRpuDataHeader header;
    AVDOVIDataMapping mapping;
    AVDOVIColorMetadata color;
    double values[WOKA_DOVI_VALUES] = {0};
    size_t offsets[4], lengths[4];
    const unsigned char *bytes = data;
    int i, j;
    if (version != LIBAVUTIL_VERSION_INT) return -2;
    if (!output || output_count != WOKA_DOVI_VALUES) return -3;
    if (!data || size < sizeof(root) || size > SIZE_MAX) return -1;
    memcpy(&root, bytes, sizeof(root));
    if (root.num_ext_blocks < 0 || root.num_ext_blocks > AV_DOVI_MAX_EXT_BLOCKS) return -1;
    offsets[0] = root.header_offset; lengths[0] = sizeof(header);
    offsets[1] = root.mapping_offset; lengths[1] = sizeof(mapping);
    offsets[2] = root.color_offset; lengths[2] = sizeof(color);
    offsets[3] = root.ext_block_offset;
    if (root.num_ext_blocks && root.ext_block_size != sizeof(AVDOVIDmData)) return -1;
    lengths[3] = (size_t)root.num_ext_blocks * sizeof(AVDOVIDmData);
    if (!region(size, offsets[0], lengths[0], ALIGNMENT(AVDOVIRpuDataHeader)) ||
        !region(size, offsets[1], lengths[1], ALIGNMENT(AVDOVIDataMapping)) ||
        !region(size, offsets[2], lengths[2], ALIGNMENT(AVDOVIColorMetadata)) ||
        (lengths[3] && !region(size, offsets[3], lengths[3], ALIGNMENT(AVDOVIDmData)))) return -1;
    for (i = 0; i < 4; ++i)
        for (j = i+1; j < 4; ++j)
            if (lengths[i] && lengths[j] && !disjoint(offsets[i], lengths[i], offsets[j], lengths[j]))
                return -1;
    memcpy(&header, bytes + offsets[0], sizeof(header));
    memcpy(&mapping, bytes + offsets[1], sizeof(mapping));
    memcpy(&color, bytes + offsets[2], sizeof(color));
    if (header.bl_bit_depth < 8 || header.bl_bit_depth > 16 || header.coef_log2_denom > 32 ||
        header.bl_video_full_range_flag > 1 || color.source_min_pq > color.source_max_pq ||
        color.source_max_pq > 4095) return -4;
    values[0] = 1; values[1] = header.bl_bit_depth;
    values[2] = header.bl_video_full_range_flag; values[3] = header.coef_log2_denom;
    values[4] = color.source_min_pq; values[5] = color.source_max_pq;
    values[10] = header.disable_residual_flag;
    values[11] = header.ext_mapping_idc_0_4; values[12] = header.ext_mapping_idc_5_7;
    values[13] = mapping.nlq_method_idc;
    values[14] = mapping.mapping_color_space; values[15] = mapping.mapping_chroma_format_idc;
    for (i = 0; i < 9; ++i)
        if (!rational(color.ycc_to_rgb_matrix[i], values+16+i) ||
            !rational(color.rgb_to_lms_matrix[i], values+28+i)) return -4;
    for (i = 0; i < 3; ++i)
        if (!rational(color.ycc_to_rgb_offset[i], values+25+i) ||
            !curve(&mapping.curves[i], header.bl_bit_depth, header.coef_log2_denom,
                values+64+210*i)) return -4;
    values[37] = color.signal_eotf; values[38] = color.signal_bit_depth;
    values[39] = color.signal_color_space; values[40] = color.signal_chroma_format;
    values[41] = color.signal_full_range_flag;
    values[42] = header.el_bit_depth; values[43] = header.vdr_bit_depth;
    values[44] = header.rpu_type; values[45] = header.rpu_format;
    values[46] = header.vdr_rpu_profile; values[47] = header.vdr_rpu_level;
    values[48] = header.chroma_resampling_explicit_filter_flag;
    values[49] = header.coef_data_type; values[50] = header.vdr_rpu_normalized_idc;
    values[51] = header.spatial_resampling_filter_flag;
    values[52] = header.el_spatial_resampling_filter_flag;
    values[53] = color.signal_eotf_param0; values[54] = color.signal_eotf_param1;
    values[55] = color.signal_eotf_param2; values[56] = color.source_diagonal;
    values[57] = color.scene_refresh_flag; values[58] = color.dm_metadata_id;
    for (i = 0; i < root.num_ext_blocks; ++i) {
        AVDOVIDmData ext;
        memcpy(&ext, bytes + offsets[3] + (size_t)i * sizeof(ext), sizeof(ext));
        if (ext.level != 1) continue;
        if (values[6] || ext.l1.min_pq > ext.l1.max_pq || ext.l1.max_pq > 4095 ||
            ext.l1.avg_pq > 4095) return -4;
        values[6] = 1; values[7] = ext.l1.min_pq;
        values[8] = ext.l1.max_pq; values[9] = ext.l1.avg_pq;
    }
    memcpy(output, values, sizeof(values));
    return 0;
}
