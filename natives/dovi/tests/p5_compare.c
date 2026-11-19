/* DV5-101 scalar experiment. Independent CPU evaluation against pinned
 * libplacebo v7.387.0 output; not linked into the application library.
 * Input is 10-bit planar 4:2:0, with explicit left/center chroma siting.
 * PQ constants are SMPTE ST 2084; HPE inverse matrix matches the pinned
 * reference (see DV5-101), not the evolving libplacebo master branch.
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "woka_dovi_metadata.h"
#include "p5_lut.h"
#ifdef WOKA_P5_SIMD
#include "p5_simd.h"
#endif

static int corrected_hpe;

static int check_p010(const char *raw_path, const char *p010_path, int w, int h);

static double clamp(double x, double a, double b) { return fmin(fmax(x, a), b); }

static double eotf(double value)
{
    double p = pow(clamp(value, 0, 1), 1.0 / 78.84375);
    return pow(fmax(p - 0.8359375, 0) / (18.8515625 - 18.6875*p), 1.0 / 0.1593017578125);
}

static double oetf(double value)
{
    double p = pow(fmax(value, 0), 0.1593017578125);
    return pow((0.8359375 + 18.8515625*p) / (1 + 18.6875*p), 78.84375);
}

static void matrix(const double *m, const double *v, double *out)
{
    int i;
    for (i = 0; i < 3; ++i) out[i] = m[i*3]*v[0] + m[i*3+1]*v[1] + m[i*3+2]*v[2];
}

static double sample(const uint16_t *data, int w, int h, double x, double y)
{
    int x0, y0, x1, y1;
    double fx, fy;
    x = clamp(x, 0, w-1); y = clamp(y, 0, h-1);
    x0 = (int)x; y0 = (int)y;
    x1 = x0+1 < w ? x0+1 : x0; y1 = y0+1 < h ? y0+1 : y0;
    fx = x-x0; fy = y-y0;
    return (1-fy)*((1-fx)*data[y0*w+x0] + fx*data[y0*w+x1]) +
        fy*((1-fx)*data[y1*w+x0] + fx*data[y1*w+x1]);
}

static void reshape(const double *meta, const double *input, double *out)
{
    double s[3], denom = ldexp(1.0, (int)meta[1]);
    int c, i, k;
    for (c = 0; c < 3; ++c) {
        const double *curve = meta+64+210*c;
        s[c] = clamp(input[c]/denom, curve[1]/denom, curve[(int)curve[0]]/denom);
    }
    for (c = 0; c < 3; ++c) {
        const double *curve = meta+64+210*c, *piece;
        double result;
        int segment = 0, count = (int)curve[0];
        while (segment < count-2 && s[c] >= curve[segment+2]/denom) ++segment;
        piece = curve+10+25*segment;
        result = piece[2];
        if (piece[0] == 0) {
            result += piece[3]*s[c] + piece[4]*s[c]*s[c];
        } else {
            double terms[7] = {s[0], s[1], s[2], s[0]*s[1], s[0]*s[2], s[1]*s[2], s[0]*s[1]*s[2]};
            for (i = 0; i < (int)piece[1]; ++i)
                for (k = 0; k < 7; ++k) result += piece[3+7*i+k] * pow(terms[k], i+1);
        }
        out[c] = clamp(result, 0, 1);
    }
}

static void convert(const double *meta, const double *input, double *rgb)
{
    static const double hpe_inverse[9] = {
        3.06441879, -2.16597676, 0.10155818,
        -0.65612108, 1.78554118, -0.12943749,
        0.01736321, -0.04725154, 1.03004253
    };
    static const double hpe_2020[9] = {
        3.064396767855625, -2.165961226711146, 0.1015574148869036,
        -0.65613130540261551, 1.7855694129834121, -0.12943951030147041,
        0.017360633500538487, -0.047244531564539943, 1.0298894699757705
    };
    double reshaped[3], nonlinear[3], linear[3], lms[3];
    int c;
    reshape(meta, input, reshaped);
    for (c = 0; c < 3; ++c) reshaped[c] -= meta[25+c];
    matrix(meta+16, reshaped, nonlinear);
    for (c = 0; c < 3; ++c) linear[c] = eotf(nonlinear[c]);
    matrix(meta+28, linear, lms);
    matrix(corrected_hpe ? hpe_2020 : hpe_inverse, lms, rgb);
    for (c = 0; c < 3; ++c) rgb[c] = oetf(rgb[c]);
}

#include "p5_accuracy_test.h"

static void *load(const char *path, size_t size)
{
    FILE *file = fopen(path, "rb");
    void *data = malloc(size);
    if (!file || !data || fread(data, 1, size, file) != size || fgetc(file) != EOF) {
        fprintf(stderr, "Cannot read exact input: %s\n", path);
        exit(2);
    }
    fclose(file);
    return data;
}

static int compare(const void *a, const void *b)
{
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

static int check_p010(const char *raw_path, const char *p010_path, int w, int h)
{
    size_t n, pixels = (size_t)w*h, mismatches = 0;
    uint16_t *raw = load(raw_path, pixels*3), *packed = load(p010_path, pixels*3);
    for (n = 0; n < pixels; ++n) mismatches += packed[n] != raw[n]*64;
    for (n = 0; n < pixels/4; ++n) {
        mismatches += packed[pixels+2*n] != raw[pixels+n]*64;
        mismatches += packed[pixels+2*n+1] != raw[pixels+pixels/4+n]*64;
    }
    printf("P010 samples=%zu mismatches=%zu\n", pixels*3/2, mismatches);
    free(raw); free(packed);
    return mismatches ? 1 : 0;
}

static int self_test(void)
{
    double meta[WOKA_DOVI_VALUES] = {0}, input[3] = {256, 512, 768}, out[3];
    double expected[3] = {0.228125, 0.33564453125, 0.3981048583984375};
    int c, k, order, failures = 0;
    meta[1] = 10;
    for (c = 0; c < 3; ++c) {
        double *curve = meta+64+210*c;
        curve[0] = 2; curve[2] = 1023;
        curve[11] = 1; curve[13] = 1;
    }
    reshape(meta, input, out);
    for (c = 0; c < 3; ++c) failures += fabs(out[c] - input[c]/1024) > 1e-12;
    for (order = 1; order <= 3; ++order) {
        double *piece = meta+64+10;
        piece[0] = 1; piece[1] = order;
        for (k = 0; k < 21; ++k) piece[3+k] = 0.1;
        reshape(meta, input, out);
        failures += fabs(out[0] - expected[order-1]) > 1e-12;
    }
    meta[64] = 3; meta[65] = 0; meta[66] = 512; meta[67] = 1023;
    meta[64+35] = 0; meta[64+36] = 2;
    meta[64+37] = 0.1; meta[64+38] = 0.2; meta[64+39] = 0.3;
    input[0] = 512;
    reshape(meta, input, out);
    failures += fabs(out[0] - 0.275) > 1e-12; /* boundary chooses the right piece */
    input[0] = 2048;
    reshape(meta, input, out);
    failures += fabs(out[0] - (0.1 + 0.2*(1023.0/1024) + 0.3*pow(1023.0/1024, 2))) > 1e-12;
    for (k = 1; k < 10; ++k) failures += fabs(oetf(eotf(k/10.0))-k/10.0) > 1e-10;
    failures += fabs(eotf(0)) > 1e-15;
    failures += fabs(eotf(1)-1) > 1e-15;
    failures += accuracy_test();
    printf("DV5 scalar math failures: %d\n", failures);
    return failures ? 1 : 0;
}

int main(int argc, char **argv)
{
    double meta[WOKA_DOVI_VALUES], sum = 0, maximum = 0, linear_sum = 0, linear_max = 0;
    double *errors;
    uint16_t *raw;
    float *reference;
    FILE *csv;
    size_t pixels, count = 0, capacity;
    int w, h, step, x, y, c, i, index, worstx = 0, worsty = 0, worstc = 0;
    double shift, p99;
    struct p5_tables *tables = NULL;
    struct p5_lut fast;
    double scalar_max = 0;
    int use_lut;
    int use_simd;
#ifdef WOKA_P5_SIMD
    struct p5_simd *vector = NULL;
    float batch[3][8];
    uint8_t *packed = NULL;
    int byte_scalar_max = 0, byte_reference_max = 0, alpha_failures = 0;
#endif
    if (argc == 2 && strcmp(argv[1], "--self-test") == 0) return self_test();
    if (argc == 6 && strcmp(argv[1], "--check-p010") == 0) {
        w = atoi(argv[4]); h = atoi(argv[5]);
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || w%2 || h%2) return 2;
        return check_p010(argv[2], argv[3], w, h);
    }
    if (argc != 8 && argc != 9) {
        fprintf(stderr, "p5_compare raw.yuv metadata.csv reference.gbrpf32le width height step chromaXShift [hpe2020|lut|simd]\n");
        return 2;
    }
    corrected_hpe = argc == 9 && strcmp(argv[8], "hpe2020") == 0;
    use_simd = argc == 9 && strcmp(argv[8], "simd") == 0;
#ifdef WOKA_P5_SIMD
    if (use_simd && !p5_simd_supported()) { fprintf(stderr, "AVX2/FMA/OSXSAVE unavailable\n"); return 2; }
#endif
    use_lut = use_simd || (argc == 9 && strcmp(argv[8], "lut") == 0);
#ifndef WOKA_P5_SIMD
    if (use_simd) return 2;
#endif
    w = atoi(argv[4]); h = atoi(argv[5]); step = atoi(argv[6]); shift = atof(argv[7]);
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || w%2 || h%2 || step < 1) return 2;
    if (use_simd && (w%8 || step != 1 || shift != 0)) return 2;
    pixels = (size_t)w*h;
    raw = load(argv[1], pixels*3);
    reference = load(argv[3], pixels*3*sizeof(float));
    csv = fopen(argv[2], "r");
    if (!csv) return 2;
    for (i = 0; i < WOKA_DOVI_VALUES; ++i)
        if (fscanf(csv, "%d,%lf", &index, meta+i) != 2 || index != i) return 2;
    fclose(csv);
    if (meta[0] != 1 || meta[1] != 10 || meta[10] != 1 || meta[11] || meta[12] || meta[13] != -1)
        return 2;
    if (use_lut) {
        tables = malloc(sizeof(*tables));
        if (!tables) return 2;
        p5_tables_init(tables);
        p5_lut_init(&fast, tables, meta);
#ifdef WOKA_P5_SIMD
        if (use_simd) {
            vector = malloc(sizeof(*vector));
            if (!vector) return 2;
            p5_simd_tables(vector, tables);
            if (!p5_simd_prepare(vector, &fast)) return 2;
            packed = malloc(pixels*4);
            if (!packed) return 2;
        }
#endif
    }
    capacity = (size_t)((w+step-1)/step)*((h+step-1)/step)*3;
    errors = malloc(capacity*sizeof(double));
    if (!errors) return 2;
    for (y = 0; y < h; y += step) for (x = 0; x < w; x += step) {
        double input[3], rgb[3];
        input[0] = raw[(size_t)y*w+x];
        input[1] = sample(raw+pixels, w/2, h/2, x*0.5+shift, y*0.5-0.25);
        input[2] = sample(raw+pixels+pixels/4, w/2, h/2, x*0.5+shift, y*0.5-0.25);
        convert(meta, input, rgb);
#ifdef WOKA_P5_SIMD
        if (use_simd) {
            size_t offset = (size_t)y*w+x;
            if (x == 0) p5_simd_row(vector, raw, packed, w, h, y);
            for (c = 0; c < 3; ++c) {
                int plane = c == 0 ? 2 : c == 1 ? 0 : 1;
                int scalar_byte = (int)(clamp(rgb[c], 0, 1)*255+0.5);
                int ref_byte = (int)(clamp(reference[plane*pixels+offset], 0, 1)*255+0.5);
                int actual = packed[4*offset+c];
                int scalar_error = abs(actual-scalar_byte), ref_error = abs(actual-ref_byte);
                if (scalar_error > byte_scalar_max) byte_scalar_max = scalar_error;
                if (ref_error > byte_reference_max) byte_reference_max = ref_error;
            }
            alpha_failures += packed[4*offset+3] != 255;
        }
#endif
        if (use_lut) {
            double accelerated[3];
            p5_lut_pixel(&fast, input, accelerated);
#ifdef WOKA_P5_SIMD
            if (use_simd) {
                if (x%8 == 0) {
                    float inputs[3][8];
                    int lane;
                    for (lane = 0; lane < 8; ++lane) {
                        inputs[0][lane] = raw[(size_t)y*w+x+lane];
                        inputs[1][lane] = (float)sample(raw+pixels, w/2, h/2, (x+lane)*0.5, y*0.5-0.25);
                        inputs[2][lane] = (float)sample(raw+pixels+pixels/4, w/2, h/2, (x+lane)*0.5, y*0.5-0.25);
                    }
                    p5_simd_pixels(vector, inputs, batch);
                }
                for (c = 0; c < 3; ++c) accelerated[c] = batch[c][x%8];
            }
#endif
            for (c = 0; c < 3; ++c) {
                scalar_max = fmax(scalar_max, fabs(rgb[c]-accelerated[c]));
                rgb[c] = accelerated[c];
            }
        }
        for (c = 0; c < 3; ++c) {
            int plane = c == 0 ? 2 : c == 1 ? 0 : 1; /* planar G B R */
            double error = fabs(rgb[c] - reference[plane*pixels+(size_t)y*w+x]);
            double linear_error = 10000 * fabs(eotf(rgb[c]) - eotf(reference[plane*pixels+(size_t)y*w+x]));
            if (!isfinite(error)) { fprintf(stderr, "Non-finite result\n"); return 3; }
            errors[count++] = error; sum += error;
            linear_sum += linear_error;
            linear_max = fmax(linear_max, linear_error);
            if (error > maximum) { maximum = error; worstx = x; worsty = y; worstc = c; }
        }
    }
    qsort(errors, count, sizeof(double), compare);
    p99 = errors[(size_t)((count-1)*0.99)];
    printf("samples=%zu step=%d chromaXShift=%g mean=%.9g p99=%.9g max=%.9g worst=%d,%d,c%d\n",
        count, step, shift, sum/count, p99, maximum, worstx, worsty, worstc);
    printf("linearNitsMean=%.9g linearNitsMax=%.9g PQ-budget=%s (max=1/1024 p99=1/2048 mean=1/4096)\n",
        linear_sum/count, linear_max,
        maximum <= 1.0/1024 && p99 <= 1.0/2048 && sum/count <= 1.0/4096 ? "PASS" : "FAIL");
    if (use_lut) printf("%s vs double-pow scalar max=%.9g budget=1/4096 %s\n",
        use_simd ? "SIMD" : "LUT", scalar_max, scalar_max <= 1.0/4096 ? "PASS" : "FAIL");
#ifdef WOKA_P5_SIMD
    if (use_simd) printf("RGBA row byteMaxScalar=%d byteMaxReference=%d alphaFailures=%d budget=1-code %s\n",
        byte_scalar_max, byte_reference_max, alpha_failures,
        byte_scalar_max <= 1 && byte_reference_max <= 1 && !alpha_failures ? "PASS" : "FAIL");
#endif
    {
        double input[3], rgb[3];
        int plane = worstc == 0 ? 2 : worstc == 1 ? 0 : 1;
        input[0] = raw[(size_t)worsty*w+worstx];
        input[1] = sample(raw+pixels, w/2, h/2, worstx*0.5+shift, worsty*0.5-0.25);
        input[2] = sample(raw+pixels+pixels/4, w/2, h/2, worstx*0.5+shift, worsty*0.5-0.25);
        convert(meta, input, rgb);
        if (use_lut) p5_lut_pixel(&fast, input, rgb);
#ifdef WOKA_P5_SIMD
        if (use_simd) {
            float values[3][8], result[3][8];
            int lane;
            for (c = 0; c < 3; ++c) for (lane = 0; lane < 8; ++lane) values[c][lane] = (float)input[c];
            p5_simd_pixels(vector, values, result);
            for (c = 0; c < 3; ++c) rgb[c] = result[c][0];
        }
#endif
        printf("hpe=%s worstInput=%.9g,%.9g,%.9g cpu=%.9g reference=%.9g\n",
            corrected_hpe ? "2020" : "tag", input[0], input[1], input[2], rgb[worstc],
            reference[plane*pixels+(size_t)worsty*w+worstx]);
    }
    free(errors); free(reference); free(raw); free(tables);
#ifdef WOKA_P5_SIMD
    free(vector);
    free(packed);
    if (use_simd && (byte_scalar_max > 1 || byte_reference_max > 1 || alpha_failures)) return 1;
#endif
    return use_lut && scalar_max > 1.0/4096 ? 1 : 0;
}
