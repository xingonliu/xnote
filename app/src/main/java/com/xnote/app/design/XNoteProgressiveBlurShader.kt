// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package com.xnote.app.design

// -- Constants

// Adapted from Haze 1.7.2 HazeShaders.kt: paired Gaussian samples and float accumulation.
// XNote uses a smooth edge-distance radius ramp and clamps sampling to the captured layer.
internal const val XNoteProgressiveBlurShader = """
    uniform shader content;
    uniform float2 size;
    uniform float2 direction;
    uniform float blurRadius;
    uniform float bottomEdge;

    float gaussian(float x, float sigma) {
        return exp(-(x * x) / (2.0 * sigma * sigma));
    }

    float4 sampleContent(float2 coord) {
        return content.eval(clamp(coord, float2(0.5), size - float2(0.5)));
    }

    half4 main(float2 coord) {
        float edgeCoordinate = mix(coord.y, size.y - coord.y, bottomEdge);
        float intensity = 1.0 - smoothstep(0.0, size.y, edgeCoordinate);
        float radius = min(blurRadius * intensity, 150.0);
        float r = floor(radius);
        float sigma = max(radius / 2.0, 1.0);
        float weightSum = 1.0;
        float4 result = sampleContent(coord);

        for (float i = 1.0; i < 150.0; i += 2.0) {
            if (i >= r) { break; }
            float weightL = gaussian(i, sigma);
            float weightH = gaussian(i + 1.0, sigma);
            float weight = weightL + weightH;
            float2 offset = direction * (i + weightH / weight);
            result += weight * (sampleContent(coord - offset) + sampleContent(coord + offset));
            weightSum += 2.0 * weight;
        }

        if (mod(r, 2.0) == 1.0) {
            float weight = gaussian(r, sigma);
            float2 offset = direction * r;
            result += weight * (sampleContent(coord - offset) + sampleContent(coord + offset));
            weightSum += 2.0 * weight;
        }

        return half4(result / weightSum);
    }
"""
