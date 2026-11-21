#include <immintrin.h>
#include "woka_p5_simd.h"

void p5_simd_tables(struct p5_simd *p, const struct p5_tables *t)
{
    unsigned i;
    for (i = 0; i <= P5_EOTF_STEPS; ++i) p->eotf[i] = (float)t->eotf[i];
    for (i = 0; i <= P5_OETF_STEPS; ++i) p->oetf[i] = (float)t->oetf[i];
}

int p5_simd_prepare(struct p5_simd *p, const struct p5_lut *source)
{
    int c, k;
    for (k = 0; k < 9; ++k) {
        p->nonlinear[k] = (float)source->nonlinear[k];
        p->linear[k] = (float)source->linear[k];
    }
    for (c = 0; c < 3; ++c) {
        const double *curve = source->meta+64+210*c;
        p->bias[c] = (float)source->bias[c];
        for (k = 0; k < (int)curve[0]-1; ++k) if (curve[10+25*k] != 0) return 0;
        for (k = 0; k <= 8192; ++k) {
            double s = p5_clamp(k/8.0, curve[1], curve[(int)curve[0]])/1024;
            const double *piece;
            int segment = 0;
            while (segment < (int)curve[0]-2 && s >= curve[segment+2]/1024) ++segment;
            piece = curve+10+25*segment;
            p->curves[c][k] = (float)p5_clamp(piece[2]+s*(piece[3]+s*piece[4]), 0, 1);
        }
    }
    return 1;
}

static __m256 dot(const float *m, const __m256 *v)
{
    __m256 result = _mm256_mul_ps(_mm256_set1_ps(m[0]), v[0]);
    result = _mm256_add_ps(result, _mm256_mul_ps(_mm256_set1_ps(m[1]), v[1]));
    return _mm256_add_ps(result, _mm256_mul_ps(_mm256_set1_ps(m[2]), v[2]));
}

static __m256 lookup(const float *table, __m256 position, int last)
{
    __m256i index = _mm256_min_epi32(_mm256_cvttps_epi32(position), _mm256_set1_epi32(last-1));
    __m256 fraction = _mm256_sub_ps(position, _mm256_cvtepi32_ps(index));
    __m256 lo = _mm256_i32gather_ps(table, index, 4);
    __m256 hi = _mm256_i32gather_ps(table, _mm256_add_epi32(index, _mm256_set1_epi32(1)), 4);
    return _mm256_add_ps(lo, _mm256_mul_ps(fraction, _mm256_sub_ps(hi, lo)));
}

static void pixels(const struct p5_simd *p, const __m256 *input, __m256 *rgb)
{
    __m256 signal[3], linear[3];
    __m256 zero = _mm256_setzero_ps(), one = _mm256_set1_ps(1);
    int c;
    for (c = 0; c < 3; ++c) {
        __m256 x = input[c];
        __m256i index = _mm256_cvttps_epi32(_mm256_mul_ps(x, _mm256_set1_ps(8)));
        index = _mm256_min_epi32(_mm256_set1_epi32(8192), _mm256_max_epi32(_mm256_setzero_si256(), index));
        signal[c] = _mm256_i32gather_ps(p->curves[c], index, 4);
    }
    for (c = 0; c < 3; ++c) {
        __m256 pq = _mm256_add_ps(dot(p->nonlinear+c*3, signal), _mm256_set1_ps(p->bias[c]));
        pq = _mm256_min_ps(one, _mm256_max_ps(zero, pq));
        linear[c] = lookup(p->eotf, _mm256_mul_ps(pq, _mm256_set1_ps(P5_EOTF_STEPS)), P5_EOTF_STEPS);
    }
    for (c = 0; c < 3; ++c) {
        __m256 x = dot(p->linear+c*3, linear);
        __m256 bounded = _mm256_min_ps(_mm256_set1_ps(4), _mm256_max_ps(_mm256_set1_ps(p5_float(P5_OETF_BASE)), x));
        __m256i bits = _mm256_castps_si256(bounded);
        __m256i index = _mm256_srli_epi32(_mm256_sub_epi32(bits, _mm256_set1_epi32(P5_OETF_BASE)), P5_OETF_SHIFT);
        __m256 position = _mm256_add_ps(_mm256_cvtepi32_ps(index),
            _mm256_mul_ps(_mm256_cvtepi32_ps(_mm256_and_si256(bits, _mm256_set1_epi32((1<<P5_OETF_SHIFT)-1))),
                _mm256_set1_ps(1.0f/(1<<P5_OETF_SHIFT))));
        __m256 result = lookup(p->oetf, position, P5_OETF_STEPS);
        result = _mm256_blendv_ps(result, _mm256_set1_ps(0.0000007309559025783966f), _mm256_cmp_ps(x, zero, _CMP_LE_OQ));
        rgb[c] = result;
    }
}

void p5_simd_pixels(const struct p5_simd *p, const float input[3][8], float rgb[3][8])
{
    __m256 source[3], result[3];
    int c;
    for (c = 0; c < 3; ++c) source[c] = _mm256_loadu_ps(input[c]);
    pixels(p, source, result);
    for (c = 0; c < 3; ++c) _mm256_storeu_ps(rgb[c], result[c]);
}

static __m256 read4(const uint16_t *plane, int last)
{
    __m128i packed;
    if (last) {
        uint16_t edge[4] = {plane[0], plane[1], plane[2], plane[2]};
        packed = _mm_loadl_epi64((const __m128i *)edge);
    } else {
        packed = _mm_loadl_epi64((const __m128i *)plane);
    }
    return _mm256_permutevar8x32_ps(_mm256_cvtepi32_ps(_mm256_cvtepu16_epi32(packed)),
        _mm256_setr_epi32(0, 0, 1, 1, 2, 2, 3, 3));
}

static __m256 sample8(const uint16_t *top, const uint16_t *bottom, int x, int last, __m256 fy)
{
    __m256 a = read4(top+x/2, 0), b = read4(bottom+x/2, 0);
    __m256 left = _mm256_add_ps(a, _mm256_mul_ps(fy, _mm256_sub_ps(b, a)));
    __m256 ar = read4(top+x/2+1, last), br = read4(bottom+x/2+1, last);
    __m256 right = _mm256_add_ps(ar, _mm256_mul_ps(fy, _mm256_sub_ps(br, ar)));
    return _mm256_blend_ps(left, _mm256_mul_ps(_mm256_add_ps(left, right), _mm256_set1_ps(0.5f)), 0xaa);
}

void p5_simd_image_row(const struct p5_simd *p, const struct p5_image *image, uint8_t *rgba, int y)
{
    int w = image->w, h = image->h;
    int y0 = y ? (y-1)/2 : 0, y1 = y0+1 < h/2 ? y0+1 : y0, x, c;
    const uint16_t *cb = image->plane[1], *cr = image->plane[2];
    __m256 fy = _mm256_set1_ps(y ? (y%2 ? 0.25f : 0.75f) : 0);
    for (x = 0; x < w; x += 8) {
        __m256 input[3], rgb[3];
        __m256i channels[3], packed;
        input[0] = _mm256_cvtepi32_ps(_mm256_cvtepu16_epi32(_mm_loadu_si128(
            (const __m128i *)(image->plane[0]+(size_t)y*image->stride[0]+x))));
        input[1] = sample8(cb+(size_t)y0*image->stride[1], cb+(size_t)y1*image->stride[1], x, x == w-8, fy);
        input[2] = sample8(cr+(size_t)y0*image->stride[2], cr+(size_t)y1*image->stride[2], x, x == w-8, fy);
        pixels(p, input, rgb);
        for (c = 0; c < 3; ++c) {
            __m256 v = _mm256_min_ps(_mm256_set1_ps(1), _mm256_max_ps(_mm256_setzero_ps(), rgb[c]));
            channels[c] = _mm256_cvttps_epi32(_mm256_add_ps(_mm256_set1_ps(0.5f), _mm256_mul_ps(v, _mm256_set1_ps(255))));
        }
        packed = _mm256_or_si256(_mm256_set1_epi32((int)0xff000000u), channels[0]);
        packed = _mm256_or_si256(packed, _mm256_slli_epi32(channels[1], 8));
        packed = _mm256_or_si256(packed, _mm256_slli_epi32(channels[2], 16));
        _mm256_storeu_si256((__m256i *)(rgba+4*((size_t)y*w+x)), packed);
    }
}

void p5_simd_row(const struct p5_simd *p, const uint16_t *raw, uint8_t *rgba, int w, int h, int y)
{
    size_t count = (size_t)w*h;
    struct p5_image image = {{raw, raw+count, raw+count+count/4}, {w, w/2, w/2}, w, h};
    p5_simd_image_row(p, &image, rgba, y);
}
