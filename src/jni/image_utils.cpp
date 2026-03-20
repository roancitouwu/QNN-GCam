#include "qnn_wrapper.h"
#include <cstring>
#include <algorithm>

// Image format conversion utilities for QNN

// Convert RGBA to RGB (3 channels for model input)
void rgbaToRgb(const uint8_t* rgba, uint8_t* rgb, int width, int height) {
    int pixelCount = width * height;
    for (int i = 0; i < pixelCount; i++) {
        rgb[i * 3 + 0] = rgba[i * 4 + 0];  // R
        rgb[i * 3 + 1] = rgba[i * 4 + 1];  // G
        rgb[i * 3 + 2] = rgba[i * 4 + 2];  // B
        // Skip alpha
    }
}

// Convert RGB back to RGBA
void rgbToRgba(const uint8_t* rgb, uint8_t* rgba, int width, int height) {
    int pixelCount = width * height;
    for (int i = 0; i < pixelCount; i++) {
        rgba[i * 4 + 0] = rgb[i * 3 + 0];  // R
        rgba[i * 4 + 1] = rgb[i * 3 + 1];  // G
        rgba[i * 4 + 2] = rgb[i * 3 + 2];  // B
        rgba[i * 4 + 3] = 255;              // A (opaque)
    }
}

// Normalize pixel values to float [0, 1]
void normalizeToFloat(const uint8_t* input, float* output, int size) {
    for (int i = 0; i < size; i++) {
        output[i] = static_cast<float>(input[i]) / 255.0f;
    }
}

// Denormalize float [0, 1] back to uint8
void denormalizeToUint8(const float* input, uint8_t* output, int size) {
    for (int i = 0; i < size; i++) {
        float val = input[i] * 255.0f;
        val = std::max(0.0f, std::min(255.0f, val));
        output[i] = static_cast<uint8_t>(val);
    }
}

// Convert YUV420 (Camera2 format) to RGB
void yuv420ToRgb(const uint8_t* yPlane, const uint8_t* uPlane, const uint8_t* vPlane,
                  int yRowStride, int uvRowStride, int uvPixelStride,
                  uint8_t* rgb, int width, int height) {
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int yIndex = y * yRowStride + x;
            int uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride;
            
            int Y = yPlane[yIndex] & 0xFF;
            int U = (uPlane[uvIndex] & 0xFF) - 128;
            int V = (vPlane[uvIndex] & 0xFF) - 128;
            
            // YUV to RGB conversion
            int R = Y + (int)(1.402f * V);
            int G = Y - (int)(0.344f * U + 0.714f * V);
            int B = Y + (int)(1.772f * U);
            
            // Clamp
            R = std::max(0, std::min(255, R));
            G = std::max(0, std::min(255, G));
            B = std::max(0, std::min(255, B));
            
            int rgbIndex = (y * width + x) * 3;
            rgb[rgbIndex + 0] = static_cast<uint8_t>(R);
            rgb[rgbIndex + 1] = static_cast<uint8_t>(G);
            rgb[rgbIndex + 2] = static_cast<uint8_t>(B);
        }
    }
}

// Resize image using bilinear interpolation
void resizeBilinear(const uint8_t* input, int srcWidth, int srcHeight,
                    uint8_t* output, int dstWidth, int dstHeight, int channels) {
    float xRatio = static_cast<float>(srcWidth) / dstWidth;
    float yRatio = static_cast<float>(srcHeight) / dstHeight;
    
    for (int y = 0; y < dstHeight; y++) {
        for (int x = 0; x < dstWidth; x++) {
            float srcX = x * xRatio;
            float srcY = y * yRatio;
            
            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, srcWidth - 1);
            int y1 = std::min(y0 + 1, srcHeight - 1);
            
            float xFrac = srcX - x0;
            float yFrac = srcY - y0;
            
            for (int c = 0; c < channels; c++) {
                float v00 = input[(y0 * srcWidth + x0) * channels + c];
                float v01 = input[(y0 * srcWidth + x1) * channels + c];
                float v10 = input[(y1 * srcWidth + x0) * channels + c];
                float v11 = input[(y1 * srcWidth + x1) * channels + c];
                
                float v0 = v00 * (1 - xFrac) + v01 * xFrac;
                float v1 = v10 * (1 - xFrac) + v11 * xFrac;
                float v = v0 * (1 - yFrac) + v1 * yFrac;
                
                output[(y * dstWidth + x) * channels + c] = static_cast<uint8_t>(v);
            }
        }
    }
}
