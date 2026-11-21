#ifndef WOKA_P5_H
#define WOKA_P5_H
#include "woka_dovi_metadata.h"

/* ABI 1. One owner prepares a decoded AVFrame. Successful prepare borrows its
 * pixel planes until finish; disjoint row ranges may be rendered concurrently.
 * Join every worker BEFORE finish, prepare, destroy, or advancing FFmpeg.
 * Failed prepare invalidates the old frame; rows then refuses to touch output.
 * Frames/output remain caller-owned. No FFmpeg library is linked.
 * Status: -1 geometry/output, -2 version, -3 missing/malformed metadata,
 * -4 unsupported signal, -5 not prepared; 0 success.
 */
WOKA_DOVI_EXPORT int woka_p5_version(unsigned avutil_version);
WOKA_DOVI_EXPORT void *woka_p5_create(unsigned avutil_version);
WOKA_DOVI_EXPORT void woka_p5_destroy(void *context);
WOKA_DOVI_EXPORT int woka_p5_prepare(void *context, const void *frame, unsigned avutil_version);
WOKA_DOVI_EXPORT int woka_p5_rows(const void *context, void *output, uint64_t size,
    int stride, int first_row, int end_row);
WOKA_DOVI_EXPORT void woka_p5_finish(void *context);
/* Optional synchronous GPU path; 1 means unavailable (caller uses CPU), negative = invalid arguments.
 * No row workers may be active. Output may be resized only after full-resolution reconstruction. */
WOKA_DOVI_EXPORT int woka_p5_gpu(void *context, void *output, uint64_t size, int stride, int width, int height);
#endif
