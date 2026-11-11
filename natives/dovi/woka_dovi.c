#include <stdint.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/version.h>

#ifdef _WIN32
#define WOKA_EXPORT __declspec(dllexport)
#else
#define WOKA_EXPORT __attribute__((visibility("default")))
#endif

/* Own ABI: negative = error; otherwise 0x10000 | (profile << 8) | compatibility.
 * The caller owns data. Nothing is allocated, retained, written or freed here.
 * A record from a different libavutil version is never dereferenced.
 */
WOKA_EXPORT int woka_dovi_read(const void *data, uint64_t size, unsigned avutil_version)
{
    const AVDOVIDecoderConfigurationRecord *config;
    if (avutil_version != LIBAVUTIL_VERSION_INT)
        return -2;
    if (!data || size != sizeof(AVDOVIDecoderConfigurationRecord))
        return -1;
    config = (const AVDOVIDecoderConfigurationRecord *)data;
    if (config->dv_version_major != 1 || config->dv_version_minor != 0 ||
        config->rpu_present_flag > 1 || config->el_present_flag > 1 ||
        config->bl_present_flag > 1 || config->dv_bl_signal_compatibility_id > 15)
        return -3;
    return 0x10000 | ((unsigned)config->dv_profile << 8) |
           config->dv_bl_signal_compatibility_id;
}
