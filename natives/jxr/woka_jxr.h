/*
 * woka_jxr — flat C API over jxrlib for Java FFM (Panama).
 *
 * All functions are reentrant: no global state is kept between calls.
 * Pixels come back as non-premultiplied RGBA8, row-major, top-down, no row padding,
 * width * height * 4 bytes, sRGB-encoded. See README.md for the per-format conversion rules.
 */
#ifndef WOKA_JXR_H
#define WOKA_JXR_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#  define WOKA_JXR_API __declspec(dllexport)
#else
#  define WOKA_JXR_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* Return codes. */
#define WOKA_JXR_OK                 0
#define WOKA_JXR_ERR_ARGS           1  /* null pointer / empty input */
#define WOKA_JXR_ERR_DECODE         2  /* not JPEG XR, corrupt, or jxrlib failure */
#define WOKA_JXR_ERR_UNSUPPORTED    3  /* pixel format we do not convert (n-channel, CMYK+alpha, 64bpp CMYK) */
#define WOKA_JXR_ERR_NOMEM          4  /* allocation failed */
#define WOKA_JXR_ERR_TOO_LARGE      5  /* width * height exceeds WOKA_JXR_MAX_PIXELS */

#define WOKA_JXR_MAX_PIXELS ((size_t)1 << 28)

/* Decodes the whole image. On success *out_rgba is malloc'd (free with woka_jxr_free). */
WOKA_JXR_API int woka_jxr_decode(const uint8_t* data, size_t size,
                                 int* out_width, int* out_height, uint8_t** out_rgba);

/* Parses the container and codestream header only; no pixel decode. */
WOKA_JXR_API int woka_jxr_info(const uint8_t* data, size_t size, int* out_width, int* out_height);

/* Frees a buffer returned by woka_jxr_decode. Null is a no-op. */
WOKA_JXR_API void woka_jxr_free(uint8_t* p);

#ifdef __cplusplus
}
#endif

#endif /* WOKA_JXR_H */
