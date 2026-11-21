/* DV5-101 verified AVX2 polynomial kernel. Call only after CPU capability check.
 * Polynomial samples use exact eighth-code table indices (left 4:2:0 bilinear).
 * MMR stays on the measured scalar path until a vector implementation is tested.
 */
#ifndef WOKA_P5_SIMD_H
#define WOKA_P5_SIMD_H
#include "woka_p5_math.h"
struct p5_simd {
    float eotf[P5_EOTF_STEPS+1], oetf[P5_OETF_STEPS+1];
    float curves[3][8193], nonlinear[9], bias[3], linear[9];
};
struct p5_image {
    const uint16_t *plane[3];
    int stride[3]; /* uint16_t elements, not bytes */
    int w, h;
};
int p5_simd_supported(void);
void p5_simd_tables(struct p5_simd *p, const struct p5_tables *t);
int p5_simd_prepare(struct p5_simd *p, const struct p5_lut *source);
void p5_simd_pixels(const struct p5_simd *p, const float input[3][8], float rgb[3][8]);
void p5_simd_row(const struct p5_simd *p, const uint16_t *raw, uint8_t *rgba, int w, int h, int y);
void p5_simd_image_row(const struct p5_simd *p, const struct p5_image *image, uint8_t *rgba, int y);
#endif
