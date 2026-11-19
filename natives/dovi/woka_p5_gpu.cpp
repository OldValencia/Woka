#include <d3d11.h>
#include <d3dcompiler.h>
#include <wrl/client.h>
#include <cstring>
#include <new>
#include <vector>
#include "woka_p5_gpu.h"
#include "woka_p5_shader.h"

using Microsoft::WRL::ComPtr;
namespace {
constexpr unsigned EOTF_COUNT = 65537, OETF_COUNT = 50177, CURVE_COUNT = 3 * 8193;
struct Buffer {
    ComPtr<ID3D11Buffer> data;
    ComPtr<ID3D11ShaderResourceView> read;
    ComPtr<ID3D11UnorderedAccessView> write;
};
struct Parameters {
    float nonlinear[3][4], linear[3][4];
    unsigned size[4];
};
struct Gpu {
    ComPtr<ID3D11Device> device;
    ComPtr<ID3D11DeviceContext> context;
    ComPtr<ID3D11ComputeShader> colour, resize;
    Buffer source, curves, tables, full, scaled;
    ComPtr<ID3D11Buffer> constants, staging;
    int w = 0, h = 0, outw = 0, outh = 0;

    bool buffer(Buffer &out, unsigned bytes, bool dynamic, bool raw, bool writable, const void *initial = nullptr) {
        D3D11_BUFFER_DESC desc = {};
        desc.ByteWidth = bytes;
        desc.Usage = dynamic ? D3D11_USAGE_DYNAMIC : D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE | (writable ? D3D11_BIND_UNORDERED_ACCESS : 0);
        desc.CPUAccessFlags = dynamic ? D3D11_CPU_ACCESS_WRITE : 0;
        desc.MiscFlags = raw ? D3D11_RESOURCE_MISC_BUFFER_ALLOW_RAW_VIEWS : 0;
        D3D11_SUBRESOURCE_DATA data = {};
        data.pSysMem = initial;
        if (FAILED(device->CreateBuffer(&desc, initial ? &data : nullptr, &out.data))) return false;
        D3D11_SHADER_RESOURCE_VIEW_DESC srv = {};
        srv.Format = raw ? DXGI_FORMAT_R32_TYPELESS : DXGI_FORMAT_R32_FLOAT;
        srv.ViewDimension = D3D11_SRV_DIMENSION_BUFFEREX;
        srv.BufferEx.NumElements = bytes / 4;
        srv.BufferEx.Flags = raw ? D3D11_BUFFEREX_SRV_FLAG_RAW : 0;
        if (FAILED(device->CreateShaderResourceView(out.data.Get(), &srv, &out.read))) return false;
        if (writable) {
            D3D11_UNORDERED_ACCESS_VIEW_DESC uav = {};
            uav.Format = DXGI_FORMAT_R32_TYPELESS;
            uav.ViewDimension = D3D11_UAV_DIMENSION_BUFFER;
            uav.Buffer.NumElements = bytes / 4;
            uav.Buffer.Flags = D3D11_BUFFER_UAV_FLAG_RAW;
            if (FAILED(device->CreateUnorderedAccessView(out.data.Get(), &uav, &out.write))) return false;
        }
        return true;
    }

    bool shader(const char *entry, ComPtr<ID3D11ComputeShader> &out) {
        ComPtr<ID3DBlob> code, error;
        HRESULT hr = D3DCompile(WOKA_P5_SHADER, sizeof(WOKA_P5_SHADER) - 1, "woka_p5_gpu.hlsl",
            nullptr, nullptr, entry, "cs_5_0", D3DCOMPILE_OPTIMIZATION_LEVEL3 | D3DCOMPILE_IEEE_STRICTNESS,
            0, &code, &error);
        if (FAILED(hr)) return false;
        return SUCCEEDED(device->CreateComputeShader(code->GetBufferPointer(), code->GetBufferSize(), nullptr, &out));
    }

    bool init() {
        D3D_FEATURE_LEVEL requested = D3D_FEATURE_LEVEL_11_0, actual;
        if (FAILED(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
            &requested, 1, D3D11_SDK_VERSION, &device, &actual, &context))) return false;
        if (!shader("Colour", colour) || !shader("Resize", resize)) return false;
        D3D11_BUFFER_DESC desc = {};
        desc.ByteWidth = sizeof(Parameters);
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
        return SUCCEEDED(device->CreateBuffer(&desc, nullptr, &constants)) &&
            buffer(curves, CURVE_COUNT * sizeof(float), true, false, false);
    }

    bool dimensions(const p5_gpu_input &in, int width, int height) {
        if (w == in.width && h == in.height && outw == width && outh == height) return true;
        source = {}; full = {}; scaled = {}; staging.Reset();
        w = h = outw = outh = 0;
        if (!buffer(source, in.width * in.height * 3, true, true, false) ||
            !buffer(full, in.width * in.height * 4, false, true, true)) return false;
        if ((width != in.width || height != in.height) &&
            !buffer(scaled, width * height * 4, false, true, true)) return false;
        D3D11_BUFFER_DESC desc = {};
        desc.ByteWidth = width * height * 4;
        desc.Usage = D3D11_USAGE_STAGING;
        desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
        if (FAILED(device->CreateBuffer(&desc, nullptr, &staging))) return false;
        w = in.width; h = in.height; outw = width; outh = height;
        return true;
    }

    bool render(const p5_gpu_input &in, void *output, int stride, int width, int height) {
        if (!dimensions(in, width, height)) return false;
        if (!tables.data) {
            std::vector<float> joined(EOTF_COUNT + OETF_COUNT);
            std::memcpy(joined.data(), in.eotf, EOTF_COUNT * sizeof(float));
            std::memcpy(joined.data() + EOTF_COUNT, in.oetf, OETF_COUNT * sizeof(float));
            if (!buffer(tables, static_cast<unsigned>(joined.size() * sizeof(float)), false, false, false, joined.data())) return false;
        }
        D3D11_MAPPED_SUBRESOURCE mapped;
        if (FAILED(context->Map(source.data.Get(), 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) return false;
        auto *target = static_cast<uint8_t *>(mapped.pData);
        for (int c = 0; c < 3; ++c) {
            int cw = c ? w / 2 : w, ch = c ? h / 2 : h;
            for (int y = 0; y < ch; ++y) {
                std::memcpy(target, in.plane[c] + static_cast<size_t>(y) * in.stride[c], cw * 2);
                target += cw * 2;
            }
        }
        context->Unmap(source.data.Get(), 0);
        if (FAILED(context->Map(curves.data.Get(), 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) return false;
        std::memcpy(mapped.pData, in.curves, CURVE_COUNT * sizeof(float));
        context->Unmap(curves.data.Get(), 0);
        Parameters params = {};
        for (int c = 0; c < 3; ++c) {
            std::memcpy(params.nonlinear[c], in.nonlinear + c * 3, 3 * sizeof(float));
            std::memcpy(params.linear[c], in.linear + c * 3, 3 * sizeof(float));
            params.nonlinear[c][3] = in.bias[c];
        }
        params.size[0] = w; params.size[1] = h; params.size[2] = width; params.size[3] = height;
        context->UpdateSubresource(constants.Get(), 0, nullptr, &params, 0, 0);
        ID3D11ShaderResourceView *reads[] = {source.read.Get(), curves.read.Get(), tables.read.Get(), nullptr};
        ID3D11UnorderedAccessView *write = full.write.Get();
        ID3D11Buffer *cb = constants.Get();
        context->CSSetConstantBuffers(0, 1, &cb);
        context->CSSetShaderResources(0, 4, reads);
        context->CSSetUnorderedAccessViews(0, 1, &write, nullptr);
        context->CSSetShader(colour.Get(), nullptr, 0);
        context->Dispatch((w + 15) / 16, (h + 15) / 16, 1);
        write = nullptr;
        context->CSSetUnorderedAccessViews(0, 1, &write, nullptr);
        if (scaled.data) {
            reads[3] = full.read.Get();
            write = scaled.write.Get();
            context->CSSetShaderResources(0, 4, reads);
            context->CSSetUnorderedAccessViews(0, 1, &write, nullptr);
            context->CSSetShader(resize.Get(), nullptr, 0);
            context->Dispatch((width + 15) / 16, (height + 15) / 16, 1);
        }
        ID3D11ShaderResourceView *empty[4] = {};
        write = nullptr;
        context->CSSetShaderResources(0, 4, empty);
        context->CSSetUnorderedAccessViews(0, 1, &write, nullptr);
        context->CopyResource(staging.Get(), scaled.data ? scaled.data.Get() : full.data.Get());
        if (FAILED(context->Map(staging.Get(), 0, D3D11_MAP_READ, 0, &mapped))) return false;
        for (int y = 0; y < height; ++y) {
            std::memcpy(static_cast<uint8_t *>(output) + static_cast<size_t>(y) * stride,
                static_cast<uint8_t *>(mapped.pData) + static_cast<size_t>(y) * width * 4, width * 4);
        }
        context->Unmap(staging.Get(), 0);
        return true;
    }
};
}

void *p5_gpu_create(void) {
    auto *gpu = new (std::nothrow) Gpu;
    if (gpu && gpu->init()) return gpu;
    delete gpu;
    return nullptr;
}
void p5_gpu_destroy(void *context) { delete static_cast<Gpu *>(context); }
int p5_gpu_render(void *context, const p5_gpu_input *input, void *output, int stride, int width, int height) {
    try { return static_cast<Gpu *>(context)->render(*input, output, stride, width, height) ? 0 : 1; }
    catch (const std::bad_alloc &) { return 1; }
}
