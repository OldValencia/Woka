#ifndef WOKA_P5_GPU_H
#define WOKA_P5_GPU_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
struct p5_gpu_input {
    const uint16_t *plane[3];
    int stride[3], width, height;
    const float *curves, *eotf, *oetf, *nonlinear, *bias, *linear;
};
void *p5_gpu_create(void);
void p5_gpu_destroy(void *context);
int p5_gpu_render(void *context, const struct p5_gpu_input *input,
    void *output, int stride, int width, int height);
#ifdef __cplusplus
}
#endif
#endif
