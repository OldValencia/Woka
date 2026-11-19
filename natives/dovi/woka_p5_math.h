/* DV5-101 verified colour math; internal, not an application ABI.
 * Double precision matrices/reshaping, interpolated PQ tables. No allocation
 * in the pixel loop; a prepared context and tables are immutable across rows.
 */
#ifndef WOKA_P5_LUT_H
#define WOKA_P5_LUT_H
#include <math.h>
#include <stdint.h>
#include <string.h>

#define P5_EOTF_STEPS 65536
#define P5_OETF_BASE 0x28000000u
#define P5_OETF_END  0x40800000u
#define P5_OETF_SHIFT 13
#define P5_OETF_STEPS ((P5_OETF_END-P5_OETF_BASE) >> P5_OETF_SHIFT)

struct p5_tables {
    double eotf[P5_EOTF_STEPS+1];
    double oetf[P5_OETF_STEPS+1];
};

struct p5_lut {
    const struct p5_tables *tables;
    double meta[704], nonlinear[9], bias[3], linear[9];
    int identity;
};

static double p5_clamp(double x, double lo, double hi)
{
    return x < lo ? lo : x > hi ? hi : x;
}

static double p5_eotf_exact(double x)
{
    double p = pow(x, 1.0/78.84375);
    return pow(fmax(p-0.8359375, 0)/(18.8515625-18.6875*p), 1.0/0.1593017578125);
}

static double p5_oetf_exact(double x)
{
    double p = pow(fmax(x, 0), 0.1593017578125);
    return pow((0.8359375+18.8515625*p)/(1+18.6875*p), 78.84375);
}

static float p5_float(uint32_t bits)
{
    float result;
    memcpy(&result, &bits, sizeof(result));
    return result;
}

static void p5_tables_init(struct p5_tables *t)
{
    unsigned i;
    for (i = 0; i <= P5_EOTF_STEPS; ++i)
        t->eotf[i] = p5_eotf_exact((double)i/P5_EOTF_STEPS);
    for (i = 0; i <= P5_OETF_STEPS; ++i)
        t->oetf[i] = p5_oetf_exact(p5_float(P5_OETF_BASE+(i<<P5_OETF_SHIFT)));
}

static void p5_lut_init(struct p5_lut *p, const struct p5_tables *t, const double *meta)
{
    const double hpe[9] = {3.06441879, -2.16597676, 0.10155818,
        -0.65612108, 1.78554118, -0.12943749, 0.01736321, -0.04725154, 1.03004253};
    int r, c, k;
    memcpy(p->meta, meta, sizeof(p->meta));
    memcpy(p->nonlinear, meta+16, sizeof(p->nonlinear));
    p->tables = t;
    p->identity = 1;
    for (c = 0; c < 3; ++c) {
        const double *curve = meta+64+c*210;
        p->identity &= curve[0] == 2 && curve[10] == 0 && curve[12] == 0 &&
            curve[13] == 1 && curve[14] == 0 && curve[1] == 0 && curve[2] == 1023;
    }
    for (r = 0; r < 3; ++r) {
        p->bias[r] = 0;
        for (k = 0; k < 3; ++k) p->bias[r] -= meta[16+r*3+k]*meta[25+k];
        for (c = 0; c < 3; ++c) {
            p->linear[r*3+c] = 0;
            for (k = 0; k < 3; ++k) p->linear[r*3+c] += hpe[r*3+k]*meta[28+k*3+c];
        }
    }
}

static double p5_eotf(const struct p5_tables *t, double x)
{
    double pos = p5_clamp(x, 0, 1)*P5_EOTF_STEPS;
    unsigned i = (unsigned)pos;
    return i == P5_EOTF_STEPS ? 1 : t->eotf[i]+(pos-i)*(t->eotf[i+1]-t->eotf[i]);
}

static double p5_oetf(const struct p5_tables *t, double x)
{
    float f = (float)x;
    uint32_t bits;
    unsigned i;
    double lo, hi, fraction;
    if (x <= 0) return 0.0000007309559025783966;
    memcpy(&bits, &f, sizeof(bits));
    if (bits < P5_OETF_BASE || bits >= P5_OETF_END) return p5_oetf_exact(x);
    i = (bits-P5_OETF_BASE)>>P5_OETF_SHIFT;
    lo = p5_float(P5_OETF_BASE+(i<<P5_OETF_SHIFT));
    hi = p5_float(P5_OETF_BASE+((i+1)<<P5_OETF_SHIFT));
    fraction = (x-lo)/(hi-lo);
    return t->oetf[i]+fraction*(t->oetf[i+1]-t->oetf[i]);
}

static void p5_lut_pixel(const struct p5_lut *p, const double *input, double *rgb)
{
    double signal[3], reshaped[3], linear[3];
    int c, k, order;
    for (c = 0; c < 3; ++c) {
        const double *curve = p->meta+64+c*210;
        signal[c] = p5_clamp(input[c], curve[1], curve[(int)curve[0]])/1024;
    }
    for (c = 0; c < 3; ++c) {
        const double *curve = p->meta+64+c*210, *piece;
        double value;
        int segment = 0;
        if (p->identity) { reshaped[c] = signal[c]; continue; }
        while (segment < (int)curve[0]-2 && signal[c] >= curve[segment+2]/1024) ++segment;
        piece = curve+10+25*segment;
        value = piece[2];
        if (piece[0] == 0) {
            value += signal[c]*(piece[3]+signal[c]*piece[4]);
        } else {
            double terms[7] = {signal[0], signal[1], signal[2], signal[0]*signal[1],
                signal[0]*signal[2], signal[1]*signal[2], signal[0]*signal[1]*signal[2]};
            for (k = 0; k < 7; ++k) {
                double power = terms[k];
                for (order = 0; order < (int)piece[1]; ++order) {
                    value += piece[3+7*order+k]*power;
                    power *= terms[k];
                }
            }
        }
        reshaped[c] = p5_clamp(value, 0, 1);
    }
    for (c = 0; c < 3; ++c) {
        const double *m = p->nonlinear+c*3;
        linear[c] = p5_eotf(p->tables, m[0]*reshaped[0]+m[1]*reshaped[1]+m[2]*reshaped[2]+p->bias[c]);
    }
    for (c = 0; c < 3; ++c) {
        const double *m = p->linear+c*3;
        rgb[c] = p5_oetf(p->tables, m[0]*linear[0]+m[1]*linear[1]+m[2]*linear[2]);
    }
}
#endif
