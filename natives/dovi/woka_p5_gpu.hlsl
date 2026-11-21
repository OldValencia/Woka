// DV5-104: same float LUT arithmetic as the verified AVX2 path. No display mapping.
ByteAddressBuffer Source : register(t0);
Buffer<float> Curves : register(t1);
Buffer<float> Tables : register(t2);
ByteAddressBuffer Reconstructed : register(t3);
RWByteAddressBuffer Output : register(u0);
cbuffer Parameters : register(b0) {
    float4 Nonlinear[3];
    float4 Linear[3];
    uint4 Size; // source width/height, destination width/height
};

float code(uint plane, int2 xy) {
    uint width = plane == 0 ? Size.x : Size.x / 2;
    uint offset = plane == 0 ? 0 : Size.x * Size.y * (plane == 1 ? 2 : 5) / (plane == 1 ? 1 : 2);
    uint index = xy.y * width + xy.x;
    uint pair = Source.Load(offset + (index / 2) * 4);
    return (pair >> ((index & 1) * 16)) & 65535;
}

float chroma(uint plane, uint2 xy) {
    float2 pos = clamp(float2(xy) * 0.5 - float2(0, 0.25), 0, float2(Size.xy / 2) - 1);
    int2 a = int2(pos), b = min(a + 1, int2(Size.xy / 2) - 1);
    float2 f = pos - a;
    float top = code(plane, a) + f.x * (code(plane, int2(b.x, a.y)) - code(plane, a));
    float bottom = code(plane, int2(a.x, b.y)) + f.x * (code(plane, b) - code(plane, int2(a.x, b.y)));
    return top + f.y * (bottom - top);
}

float lookup(uint base, float pos, uint last) {
    uint index = min((uint)pos, last - 1);
    float lo = Tables[base + index], hi = Tables[base + index + 1];
    return lo + (pos - index) * (hi - lo);
}

float eotf(float pq) { return lookup(0, saturate(pq) * 65536, 65536); }
float oetf(float value) {
    if (value <= 0) return 0.0000007309559025783966;
    uint bits = asuint(clamp(value, asfloat(0x28000000), 4.0));
    float pos = ((bits - 0x28000000) >> 13) + (bits & 8191) / 8192.0;
    return lookup(65537, pos, 50176);
}

float matrixRow(float4 m, float3 v) { return (m.x * v.x + m.y * v.y) + m.z * v.z; }
uint pack(float3 rgb) {
    uint3 codes = uint3(saturate(rgb) * 255 + 0.5);
    return codes.x | (codes.y << 8) | (codes.z << 16) | 0xff000000;
}

[numthreads(16, 16, 1)]
void Colour(uint3 at : SV_DispatchThreadID) {
    if (any(at.xy >= Size.xy)) return;
    float3 input = float3(code(0, at.xy), chroma(1, at.xy), chroma(2, at.xy));
    uint3 index = uint3(clamp(input * 8, 0, 8192));
    float3 signal = float3(Curves[index.x], Curves[8193 + index.y], Curves[16386 + index.z]);
    float3 light = float3(eotf(matrixRow(Nonlinear[0], signal) + Nonlinear[0].w),
        eotf(matrixRow(Nonlinear[1], signal) + Nonlinear[1].w),
        eotf(matrixRow(Nonlinear[2], signal) + Nonlinear[2].w));
    float3 rgb = float3(oetf(matrixRow(Linear[0], light)), oetf(matrixRow(Linear[1], light)),
        oetf(matrixRow(Linear[2], light)));
    Output.Store(4 * (at.y * Size.x + at.x), pack(rgb));
}

float3 readRgb(int2 at) {
    at = clamp(at, 0, int2(Size.xy) - 1);
    uint pixel = Reconstructed.Load(4 * (at.y * Size.x + at.x));
    return float3(pixel & 255, (pixel >> 8) & 255, (pixel >> 16) & 255);
}

// Antialiased triangle kernel, after full-resolution colour reconstruction/quantisation.
[numthreads(16, 16, 1)]
void Resize(uint3 at : SV_DispatchThreadID) {
    if (any(at.xy >= Size.zw)) return;
    float2 scale = float2(Size.xy) / Size.zw;
    float2 centre = (float2(at.xy) + 0.5) * scale - 0.5;
    float2 radius = max(scale, 1.0);
    int2 first = int2(ceil(centre - radius)), last = int2(floor(centre + radius));
    float3 sum = 0;
    float weight = 0;
    for (int y = first.y; y <= last.y; ++y) {
        float wy = max(0, 1 - abs(y - centre.y) / radius.y);
        for (int x = first.x; x <= last.x; ++x) {
            float w = wy * max(0, 1 - abs(x - centre.x) / radius.x);
            sum += w * readRgb(int2(x, y));
            weight += w;
        }
    }
    Output.Store(4 * (at.y * Size.z + at.x), pack(sum / (weight * 255)));
}
