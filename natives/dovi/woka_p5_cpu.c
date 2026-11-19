/* Baseline compilation unit: capability detection itself must not use AVX. */
#include <intrin.h>
int p5_simd_supported(void)
{
    int info[4];
    __cpuid(info, 0);
    if (info[0] < 7) return 0;
    __cpuid(info, 1);
    if ((info[2] & ((1<<12) | (1<<26) | (1<<27) | (1<<28))) !=
        ((1<<12) | (1<<26) | (1<<27) | (1<<28))) return 0;
    if ((_xgetbv(0) & 6) != 6) return 0;
    __cpuidex(info, 7, 0);
    return (info[1] & (1<<5)) != 0;
}
