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
 * Linear-phase Kaiser-windowed FIR lookup-table decimator converting 1-bit DSD64-DSD512
 * bitstreams into 32-bit IEEE float PCM with zero per-bit inner loops during playback.
 */
class DsdDecimator final {
public:
    DsdDecimator(std::int32_t channelCount, std::int32_t decimationFactor, bool lsbFirst);

    void reset() noexcept;

    std::size_t processInterleaved(
        const std::uint8_t* input,
        std::size_t frameBytes,
        float* outPcm) noexcept;

    std::size_t processPlanar(
        const std::uint8_t* input,
        std::size_t blockBytesPerChannel,
        float* outPcm) noexcept;

    [[nodiscard]] std::size_t maxOutputFrames(std::size_t inputByteFrames) const noexcept {
        return stepBytes_ == 0 ? 0 : (inputByteFrames + bytePhase_ + stepBytes_ - 1) / stepBytes_;
    }

    [[nodiscard]] std::int32_t channelCount() const noexcept { return channelCount_; }
    [[nodiscard]] std::int32_t decimationFactor() const noexcept { return decimationFactor_; }

private:
    static constexpr std::size_t kLutGroupsPer16x = 8;
    static constexpr float kSacdNominalGain = 0.70710678f;

    std::int32_t channelCount_{2};
    std::int32_t decimationFactor_{16};
    bool lsbFirst_{true};
    std::size_t stepBytes_{2};
    std::size_t filterBytes_{8};

    std::vector<float> lut_;
    std::vector<std::uint8_t> history_;
    std::size_t ringWritePos_{0};
    std::size_t bytePhase_{0};

    void buildFilterTables();
    float convolveChannel(std::size_t ch) const noexcept;
};

}  // namespace rhythm::nativeaudio
