/* Synthetic accuracy against the independent double/pow scalar above this
 * include. Real samples do not cover MMR or adversarial pivot/clipping cases.
 */
static int accuracy_test(void)
{
    double meta[704] = {0}, worst = 0;
    struct p5_tables *tables = malloc(sizeof(*tables));
    struct p5_lut prepared;
    uint32_t rng = 123456789;
    int c, k, mode, iteration, failures = 0, checks = 0;
#ifdef WOKA_P5_SIMD
    struct p5_simd *vector = p5_simd_supported() ? malloc(sizeof(*vector)) : NULL;
#endif
    if (!tables) return 1;
    p5_tables_init(tables);
#ifdef WOKA_P5_SIMD
    if (vector) p5_simd_tables(vector, tables);
#endif
    meta[0] = 1; meta[1] = 10;
    meta[16] = meta[20] = meta[24] = 1;
    meta[28] = meta[32] = meta[36] = 1;
    for (c = 0; c < 3; ++c) {
        double *curve = meta+64+c*210;
        curve[0] = 3; curve[1] = 0; curve[2] = 512; curve[3] = 1023;
        curve[11] = 2; curve[12] = 0.1; curve[13] = 0.3; curve[14] = 0.7;
        curve[36] = 2; curve[37] = -0.2; curve[38] = 1.2; curve[39] = 0.3;
    }
    for (mode = 0; mode <= 3; ++mode) {
        int vector_ready = 0;
        for (c = 0; c < 3; ++c) {
            double *piece = meta+64+c*210+10;
            piece[0] = mode ? 1 : 0;
            if (mode) {
                piece[1] = mode;
                for (k = 0; k < 21; ++k) piece[3+k] = (k%3 == 0 ? -0.03 : 0.04)*(c+1);
            }
        }
        p5_lut_init(&prepared, tables, meta);
#ifdef WOKA_P5_SIMD
        if (vector) vector_ready = p5_simd_prepare(vector, &prepared);
        if (vector) failures += vector_ready != (mode == 0);
#endif
        for (iteration = 0; iteration < 4096; ++iteration) {
            double input[3], expected[3], result[3];
            for (c = 0; c < 3; ++c) {
                rng = rng*1664525u+1013904223u;
                input[c] = (rng%8185)/8.0;
            }
            if (iteration < 3) input[0] = 512 + (iteration-1)*0.125;
            convert(meta, input, expected);
            p5_lut_pixel(&prepared, input, result);
            for (c = 0; c < 3; ++c) {
                double error = fabs(result[c]-expected[c]);
                worst = fmax(worst, error);
                failures += !isfinite(error) || error > 1.0/4096;
                ++checks;
            }
#ifdef WOKA_P5_SIMD
            if (vector_ready) {
                float inputs[3][8], results[3][8];
                for (c = 0; c < 3; ++c) for (k = 0; k < 8; ++k) inputs[c][k] = (float)input[c];
                p5_simd_pixels(vector, inputs, results);
                for (c = 0; c < 3; ++c) {
                    double error = fabs(results[c][0]-expected[c]);
                    worst = fmax(worst, error);
                    failures += !isfinite(error) || error > 1.0/4096;
                    ++checks;
                }
            }
#endif
        }
    }
#ifdef WOKA_P5_SIMD
    if (vector) {
        int w, h, x, y;
        uint16_t raw[24*6*3/2];
        uint16_t padded[3][32*6];
        uint8_t rgba[24*6*4+16];
        uint8_t strided[24*6*4];
        for (c = 0; c < 3; ++c) {
            double *curve = meta+64+c*210;
            memset(curve, 0, 210*sizeof(double));
            curve[0] = 2; curve[2] = 1023; curve[11] = 1; curve[13] = 1;
        }
        p5_lut_init(&prepared, tables, meta);
        failures += !p5_simd_prepare(vector, &prepared);
        for (w = 8; w <= 24; w += 8) for (h = 2; h <= 6; h += 2) {
            size_t n, pixels = (size_t)w*h;
            for (n = 0; n < pixels*3/2; ++n) {
                rng = rng*1664525u+1013904223u;
                raw[n] = (uint16_t)(rng%1024);
            }
            memset(rgba, 0xcd, sizeof(rgba));
            for (y = 0; y < h; ++y) p5_simd_row(vector, raw, rgba+8, w, h, y);
            {
                struct p5_image image = {{padded[0], padded[1], padded[2]}, {w+3, w/2+5, w/2+7}, w, h};
                memset(padded, 0xff, sizeof(padded));
                for (c = 0; c < 3; ++c) {
                    int plane_w = c ? w/2 : w, plane_h = c ? h/2 : h;
                    const uint16_t *source = raw+(c == 0 ? 0 : c == 1 ? pixels : pixels+pixels/4);
                    for (y = 0; y < plane_h; ++y)
                        memcpy(padded[c]+y*image.stride[c], source+y*plane_w, (size_t)plane_w*2);
                }
                for (y = 0; y < h; ++y) p5_simd_image_row(vector, &image, strided, y);
                failures += memcmp(strided, rgba+8, pixels*4) != 0;
                ++checks;
            }
            for (k = 0; k < 8; ++k) {
                failures += rgba[k] != 0xcd || rgba[8+pixels*4+k] != 0xcd;
                checks += 2;
            }
            for (y = 0; y < h; ++y) for (x = 0; x < w; ++x) {
                double input[3], rgb[3];
                input[0] = raw[y*w+x];
                input[1] = sample(raw+pixels, w/2, h/2, x*0.5, y*0.5-0.25);
                input[2] = sample(raw+pixels+pixels/4, w/2, h/2, x*0.5, y*0.5-0.25);
                convert(meta, input, rgb);
                for (c = 0; c < 3; ++c) {
                    int expected = (int)(clamp(rgb[c], 0, 1)*255+0.5);
                    failures += abs((int)rgba[8+4*(y*w+x)+c]-expected) > 1;
                    ++checks;
                }
                failures += rgba[8+4*(y*w+x)+3] != 255;
                ++checks;
            }
        }
        free(vector);
    }
#endif
    free(tables);
    printf("PQ LUT/MMR/SIMD checks=%d failures=%d worstPQ=%.9g\n", checks, failures, worst);
    return failures;
}
