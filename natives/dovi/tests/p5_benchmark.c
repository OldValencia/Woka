/* DV5-101 kernel throughput only: no decoder, swscale, upload or UI costs.
 * OpenMP is confined to this research executable, never the packaged DLL.
 */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include "p5_lut.h"
#ifdef WOKA_P5_SIMD
#include "p5_simd.h"
#endif
#ifdef _OPENMP
#include <omp.h>
#endif
#ifdef _WIN32
#include <windows.h>
#endif

static double seconds(void)
{
#ifdef _WIN32
    LARGE_INTEGER now, frequency;
    QueryPerformanceCounter(&now);
    QueryPerformanceFrequency(&frequency);
    return (double)now.QuadPart/(double)frequency.QuadPart;
#else
    struct timespec now;
    timespec_get(&now, TIME_UTC);
    return now.tv_sec + now.tv_nsec*1e-9;
#endif
}

static double chroma(const uint16_t *plane, int w, int h, int x, int y)
{
    int x0 = x/2, x1 = x0+1 < w ? x0+1 : x0;
    int y0 = y ? (y-1)/2 : 0, y1 = y0+1 < h ? y0+1 : y0;
    double fx = x%2 ? 0.5 : 0, fy = y ? (y%2 ? 0.25 : 0.75) : 0;
    return (1-fy)*((1-fx)*plane[y0*w+x0]+fx*plane[y0*w+x1]) +
        fy*((1-fx)*plane[y1*w+x0]+fx*plane[y1*w+x1]);
}

static void render(const struct p5_lut *p, const uint16_t *raw, uint8_t *rgba, int w, int h)
{
    int y;
    size_t pixels = (size_t)w*h;
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (y = 0; y < h; ++y) {
        int x, c;
        for (x = 0; x < w; ++x) {
            double input[3], rgb[3];
            size_t offset = (size_t)y*w+x;
            input[0] = raw[offset];
            input[1] = chroma(raw+pixels, w/2, h/2, x, y);
            input[2] = chroma(raw+pixels+pixels/4, w/2, h/2, x, y);
            p5_lut_pixel(p, input, rgb);
            for (c = 0; c < 3; ++c) rgba[4*offset+c] = (uint8_t)(p5_clamp(rgb[c], 0, 1)*255+0.5);
            rgba[4*offset+3] = 255;
        }
    }
}

#ifdef WOKA_P5_SIMD
static void render_simd(const struct p5_simd *p, const uint16_t *raw, uint8_t *rgba, int w, int h)
{
    int y;
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (y = 0; y < h; ++y) {
        p5_simd_row(p, raw, rgba, w, h, y);
    }
}
#endif

int main(int argc, char **argv)
{
    struct p5_tables *tables;
    struct p5_lut prepared;
    double meta[704], start, elapsed, init;
    int w, h, iterations, threads, i, index;
    size_t pixels, n;
    size_t workspace;
    uint64_t checksum = 0;
    uint16_t *raw;
    uint8_t *rgba;
    FILE *file;
    int simd = argc == 8 && strcmp(argv[7], "simd") == 0;
#ifdef WOKA_P5_SIMD
    struct p5_simd *vector = NULL;
    if (simd && !p5_simd_supported()) { fprintf(stderr, "AVX2/FMA/OSXSAVE unavailable\n"); return 2; }
#else
    if (simd) return 2;
#endif
    if (argc != 7 && argc != 8) {
        fprintf(stderr, "p5_benchmark raw.yuv metadata.csv width height iterations threads [simd]\n");
        return 2;
    }
    w = atoi(argv[3]); h = atoi(argv[4]); iterations = atoi(argv[5]); threads = atoi(argv[6]);
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || w%2 || h%2 || iterations < 1 || threads < 1) return 2;
    pixels = (size_t)w*h;
    workspace = pixels*7+sizeof(*tables)+sizeof(prepared);
    raw = malloc(pixels*3); rgba = malloc(pixels*4); tables = malloc(sizeof(*tables));
    if (!raw || !rgba || !tables) return 2;
    file = fopen(argv[1], "rb");
    if (!file || fread(raw, 1, pixels*3, file) != pixels*3 || fgetc(file) != EOF) return 2;
    fclose(file);
    file = fopen(argv[2], "r");
    if (!file) return 2;
    for (i = 0; i < 704; ++i) if (fscanf(file, "%d,%lf", &index, meta+i) != 2 || index != i) return 2;
    fclose(file);
#ifdef _OPENMP
    omp_set_num_threads(threads);
#else
    if (threads != 1) { fprintf(stderr, "OpenMP unavailable\n"); return 2; }
#endif
    start = seconds();
    p5_tables_init(tables);
    p5_lut_init(&prepared, tables, meta);
#ifdef WOKA_P5_SIMD
    if (simd) {
        if (w%8) return 2;
        vector = malloc(sizeof(*vector));
        workspace += sizeof(*vector);
        if (!vector) return 2;
        p5_simd_tables(vector, tables);
        if (!p5_simd_prepare(vector, &prepared)) return 2;
    }
#endif
    init = seconds()-start;
    for (i = 0; i < 3; ++i) {
#ifdef WOKA_P5_SIMD
        if (simd) { render_simd(vector, raw, rgba, w, h); continue; }
#endif
        render(&prepared, raw, rgba, w, h);
    }
    start = seconds();
    for (i = 0; i < iterations; ++i) {
        p5_lut_init(&prepared, tables, meta);
#ifdef WOKA_P5_SIMD
        if (simd) {
            if (!p5_simd_prepare(vector, &prepared)) return 2;
            render_simd(vector, raw, rgba, w, h);
            continue;
        }
#endif
        render(&prepared, raw, rgba, w, h);
    }
    elapsed = seconds()-start;
    for (n = 0; n < pixels*4; ++n) checksum += rgba[n];
    printf("%dx%d simd=%d threads=%d frames=%d ms/frame=%.3f fps=%.3f initMs=%.3f workspaceBytes=%zu checksum=%llu\n",
        w, h, simd, threads, iterations, elapsed*1000/iterations, iterations/elapsed, init*1000,
        workspace, (unsigned long long)checksum);
    free(raw); free(rgba); free(tables);
#ifdef WOKA_P5_SIMD
    free(vector);
#endif
    return 0;
}
