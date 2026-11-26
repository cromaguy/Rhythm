/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "sinc_resampler.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace rhythm::nativeaudio {

namespace {

constexpr double kPi = 3.14159265358979323846;

double besselI0(double x) noexcept {
    double sum = 1.0;
    double term = 1.0;
    const double halfX = x * 0.5;
    for (int k = 1; k <= 20; ++k) {
        const double r = halfX / static_cast<double>(k);
        term *= r * r;
        sum += term;
        if (term < sum * 1e-12) break;
    }
    return sum;
}

}  // namespace

SincResampler::SincResampler(
    std::int32_t channelCount,
    std::int32_t inputRateHz,
    std::int32_t outputRateHz)
    : channelCount_(std::clamp(channelCount, 1, 8)),
      inputRateHz_(std::max(8000, inputRateHz)),
      outputRateHz_(std::max(8000, outputRateHz)),
      stepInputPerOutput_(static_cast<double>(inputRateHz_) / static_cast<double>(outputRateHz_)),
      cutoffScale_(std::min(1.0, static_cast<double>(outputRateHz_) / static_cast<double>(inputRateHz_)) * 0.92),
      ringSize_(kWindowTaps + 4) {
    ring_.assign(ringSize_ * static_cast<std::size_t>(channelCount_), 0.0f);
}

void SincResampler::reset() noexcept {
    std::fill(ring_.begin(), ring_.end(), 0.0f);
    writePos_ = 0;
    phase_ = 0.0;
}

float SincResampler::interpolateSample(std::size_t ch, double frac) const noexcept {
    const double i0Beta = besselI0(kKaiserBeta);
    const std::size_t channels = static_cast<std::size_t>(channelCount_);
    const std::size_t oldestBase = (writePos_ + ringSize_ - kWindowTaps) % ringSize_;

    double sum = 0.0;
    double weightSum = 0.0;
    for (std::size_t tap = 0; tap < kWindowTaps; ++tap) {
        const double offset = static_cast<double>(tap) - static_cast<double>(kHalfWidth) - frac;
        const double normPos = offset / static_cast<double>(kHalfWidth);
        if (std::abs(normPos) >= 1.0) continue;

        const double x = kPi * cutoffScale_ * offset;
        const double sinc = (std::abs(x) < 1e-9) ? cutoffScale_ : (cutoffScale_ * std::sin(x) / x);
        const double win = besselI0(kKaiserBeta * std::sqrt(std::max(0.0, 1.0 - normPos * normPos))) / i0Beta;
        const double w = sinc * win;

        const std::size_t ringFrame = (oldestBase + tap) % ringSize_;
        sum += static_cast<double>(ring_[ringFrame * channels + ch]) * w;
        weightSum += w;
    }

    const double normalized = (weightSum > 1e-9) ? (sum / weightSum) : 0.0;
    return static_cast<float>(std::clamp(normalized, -1.0, 1.0));
}

std::size_t SincResampler::processInterleaved(
    const float* inputPcm,
    std::size_t inputFrames,
    float* outputPcm,
    std::size_t maxOutputCapacityFrames) noexcept {
    if (inputPcm == nullptr || outputPcm == nullptr || inputFrames == 0) return 0;

    const std::size_t channels = static_cast<std::size_t>(channelCount_);
    if (inputRateHz_ == outputRateHz_) {
        const std::size_t copyFrames = std::min(inputFrames, maxOutputCapacityFrames);
        std::memcpy(outputPcm, inputPcm, copyFrames * channels * sizeof(float));
        return copyFrames;
    }

    std::size_t outFrames = 0;
    for (std::size_t i = 0; i < inputFrames; ++i) {
        for (std::size_t ch = 0; ch < channels; ++ch) {
            ring_[writePos_ * channels + ch] = inputPcm[i * channels + ch];
        }
        writePos_ = (writePos_ + 1) % ringSize_;

        while (phase_ < 1.0 && outFrames < maxOutputCapacityFrames) {
            for (std::size_t ch = 0; ch < channels; ++ch) {
                outputPcm[outFrames * channels + ch] = interpolateSample(ch, phase_);
            }
            ++outFrames;
            phase_ += stepInputPerOutput_;
        }
        phase_ -= 1.0;
    }
    return outFrames;
}

}  // namespace rhythm::nativeaudio
