#ifndef WOKA_DOVI_METADATA_H
#define WOKA_DOVI_METADATA_H
#include <stdint.h>

#ifdef _WIN32
#define WOKA_DOVI_EXPORT __declspec(dllexport)
#else
#define WOKA_DOVI_EXPORT __attribute__((visibility("default")))
#endif

/* Version 1: 704 native doubles, no compiler-dependent struct layout.
 * 0 version; 1 BL bits; 2 full range; 3 coefficient log2 denominator;
 * 4/5 source min/max PQ (12-bit codes); 6 L1 present; 7/8/9 L1 min/max/avg;
 * 10 residual disabled; 11/12 extended mapping flags; 13 NLQ method;
 * 14/15 mapping colour space/chroma; 16..24 nonlinear matrix (row-major);
 * 25..27 input offsets; 28..36 linear matrix (row-major);
 * 37..43 signal eotf, bit depth, colour space, chroma, range, EL bits, VDR bits;
 * 44..52 rpu type/format/profile/level, chroma resampling, coefficient type,
 * normalised IDC, spatial resampling, EL spatial resampling;
 * 53..58 eotf params 0/1/2, source diagonal, scene refresh, DM metadata ID;
 * 59..63 reserved. Curves start at 64, stride 210:
 *   0 pivot count; 1..9 pivots (raw codes, unused entries zero);
 *   10 + piece*25: method, order, constant, coefficients[21], reserved.
 * Polynomial: constant, x, x*x occupy constant and first two coefficients.
 * MMR: constant, then 3 groups of 7 coefficients. All coefficients divided by
 * 2^coef_log2_denom. Pivots deliberately remain integer codes: signal
 * normalisation belongs to the conversion, not an implicit bridge assumption.
 * 694..703 reserved. Caller supplies non-overlapping, writable aligned output.
 * Success = 0; -1 truncated/layout; -2 version; -3 output; -4 semantic data.
 * On failure output is untouched. No pointers retained, no heap allocation.
 */
#define WOKA_DOVI_VALUES 704
WOKA_DOVI_EXPORT int woka_dovi_metadata(const void *data, uint64_t size,
    unsigned avutil_version, double *output, uint64_t output_count);
#endif
