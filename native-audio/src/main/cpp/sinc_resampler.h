/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace rhythm::nativeaudio {

/**
 * Self-contained linear-phase Kaiser-windowed sinc resampler (32 taps/phase) requiring zero
 * external third-party source downloads for reproducible offline builds.
 */
class SincResampler final {
public:
    SincResampler(std::int32_t channelCount, std::int32_t inputRateHz, std::int32_t outputRateHz);

    void reset() noexcept;

    [[nodiscard]] std::size_t maxOutputFrames(std::size_t inputFrames) const noexcept {
        if (inputRateHz_ <= 0 || outputRateHz_ <= 0) return 0;
        const double ratio = static_cast<double>(outputRateHz_) / static_cast<double>(inputRateHz_);
        return static_cast<std::size_t>(static_cast<double>(inputFrames) * ratio) + 4;
    }

    std::size_t processInterleaved(
        const float* inputPcm,
        std::size_t inputFrames,
        float* outputPcm,
        std::size_t maxOutputCapacityFrames) noexcept;

private:
    static constexpr std::size_t kHalfWidth = 16;
    static constexpr std::size_t kWindowTaps = kHalfWidth * 2;
    static constexpr float kKaiserBeta = 8.5f;

    std::int32_t channelCount_{2};
    std::int32_t inputRateHz_{44100};
    std::int32_t outputRateHz_{48000};
    double stepInputPerOutput_{1.0};
    double cutoffScale_{1.0};
    double phase_{0.0};

    std::vector<float> ring_;
    std::size_t ringSize_{0};
    std::size_t writePos_{0};

    float interpolateSample(std::size_t ch, double frac) const noexcept;
};

}  // namespace rhythm::nativeaudio
