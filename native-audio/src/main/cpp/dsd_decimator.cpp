/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "dsd_decimator.h"

#include <algorithm>
#include <cmath>

namespace rhythm::nativeaudio {

namespace {

constexpr double kPi = 3.14159265358979323846;

double besselI0(double x) noexcept {
    double sum = 1.0;
    double term = 1.0;
    const double halfX = x * 0.5;
    for (int k = 1; k <= 24; ++k) {
        const double ratio = halfX / static_cast<double>(k);
        term *= ratio * ratio;
        sum += term;
        if (term < sum * 1e-15) break;
    }
    return sum;
}

std::int32_t sanitizeDecimation(std::int32_t decimation) noexcept {
    switch (decimation) {
        case 8:
        case 16:
        case 32:
        case 64:
        case 128:
            return decimation;
        default:
            return 16;
    }
}

std::uint8_t reverseBits(std::uint8_t b) noexcept {
    b = static_cast<std::uint8_t>(((b & 0xF0u) >> 4) | ((b & 0x0Fu) << 4));
    b = static_cast<std::uint8_t>(((b & 0xCCu) >> 2) | ((b & 0x33u) << 2));
    b = static_cast<std::uint8_t>(((b & 0xAAu) >> 1) | ((b & 0x55u) << 1));
    return b;
}

}  // namespace

DsdDecimator::DsdDecimator(
    std::int32_t channelCount,
    std::int32_t decimationFactor,
    bool lsbFirst)
    : channelCount_(std::clamp(channelCount, 1, 8)),
      decimationFactor_(sanitizeDecimation(decimationFactor)),
      lsbFirst_(lsbFirst),
      stepBytes_(static_cast<std::size_t>(decimationFactor_ / 8)) {
    const std::size_t scale = std::max<std::size_t>(1, static_cast<std::size_t>(decimationFactor_) / 16);
    filterBytes_ = kLutGroupsPer16x * scale;
    history_.assign(static_cast<std::size_t>(channelCount_) * filterBytes_, 0x55u);
    buildFilterTables();
}

void DsdDecimator::reset() noexcept {
    std::fill(history_.begin(), history_.end(), 0x55u);
    ringWritePos_ = 0;
    bytePhase_ = 0;
}

void DsdDecimator::buildFilterTables() {
    const std::size_t numTaps = filterBytes_ * 8;
    std::vector<double> taps(numTaps, 0.0);

    const double cutoffNormalized = 0.25 / static_cast<double>(decimationFactor_);
    constexpr double kBeta = 9.0;
    const double i0Beta = besselI0(kBeta);
    const double halfN = static_cast<double>(numTaps - 1) * 0.5;

    double tapSum = 0.0;
    for (std::size_t i = 0; i < numTaps; ++i) {
        const double n = static_cast<double>(i) - halfN;
        const double sinc = (std::abs(n) < 1e-9)
            ? (2.0 * cutoffNormalized)
            : (std::sin(2.0 * kPi * cutoffNormalized * n) / (kPi * n));
        const double ratio = n / halfN;
        const double arg = std::max(0.0, 1.0 - ratio * ratio);
        const double win = besselI0(kBeta * std::sqrt(arg)) / i0Beta;
        const double w = sinc * win;
        taps[i] = w;
        tapSum += w;
    }

    const double gainScale = (tapSum > 1e-12) ? (static_cast<double>(kSacdNominalGain) / tapSum) : 1.0;
    for (std::size_t i = 0; i < numTaps; ++i) {
        taps[i] *= gainScale;
    }

    lut_.assign(filterBytes_ * 256, 0.0f);
    for (std::size_t byteIdx = 0; byteIdx < filterBytes_; ++byteIdx) {
        float* row = &lut_[byteIdx * 256];
        const std::size_t tapBase = byteIdx * 8;
        for (std::size_t byteVal = 0; byteVal < 256; ++byteVal) {
            const std::uint8_t ordered = lsbFirst_
                ? reverseBits(static_cast<std::uint8_t>(byteVal))
                : static_cast<std::uint8_t>(byteVal);
            double acc = 0.0;
            for (int bit = 0; bit < 8; ++bit) {
                const bool isOne = ((ordered >> (7 - bit)) & 0x01u) != 0;
                const double sym = isOne ? 1.0 : -1.0;
                acc += sym * taps[tapBase + static_cast<std::size_t>(bit)];
            }
            row[byteVal] = static_cast<float>(acc);
        }
    }
}

float DsdDecimator::convolveChannel(std::size_t ch) const noexcept {
    const std::uint8_t* chHist = &history_[ch * filterBytes_];
    float acc = 0.0f;
    std::size_t ringIdx = ringWritePos_;
    for (std::size_t g = 0; g < filterBytes_; ++g) {
        acc += lut_[g * 256 + chHist[ringIdx]];
        ++ringIdx;
        if (ringIdx == filterBytes_) ringIdx = 0;
    }
    return std::clamp(acc, -1.0f, 1.0f);
}

std::size_t DsdDecimator::processInterleaved(
    const std::uint8_t* input,
    std::size_t frameBytes,
    float* outPcm) noexcept {
    if (input == nullptr || outPcm == nullptr || frameBytes == 0) return 0;

    const std::size_t channels = static_cast<std::size_t>(channelCount_);
    std::size_t produced = 0;

    for (std::size_t i = 0; i < frameBytes; ++i) {
        const std::size_t base = i * channels;
        for (std::size_t ch = 0; ch < channels; ++ch) {
            history_[ch * filterBytes_ + ringWritePos_] = input[base + ch];
        }
        ++ringWritePos_;
        if (ringWritePos_ == filterBytes_) ringWritePos_ = 0;

        ++bytePhase_;
        if (bytePhase_ >= stepBytes_) {
            bytePhase_ = 0;
            const std::size_t outBase = produced * channels;
            for (std::size_t ch = 0; ch < channels; ++ch) {
                outPcm[outBase + ch] = convolveChannel(ch);
            }
            ++produced;
        }
    }
    return produced;
}

std::size_t DsdDecimator::processPlanar(
    const std::uint8_t* input,
    std::size_t blockBytesPerChannel,
    float* outPcm) noexcept {
    if (input == nullptr || outPcm == nullptr || blockBytesPerChannel == 0) return 0;

    const std::size_t channels = static_cast<std::size_t>(channelCount_);
    std::size_t produced = 0;

    for (std::size_t i = 0; i < blockBytesPerChannel; ++i) {
        for (std::size_t ch = 0; ch < channels; ++ch) {
            history_[ch * filterBytes_ + ringWritePos_] = input[ch * blockBytesPerChannel + i];
        }
        ++ringWritePos_;
        if (ringWritePos_ == filterBytes_) ringWritePos_ = 0;

        ++bytePhase_;
        if (bytePhase_ >= stepBytes_) {
            bytePhase_ = 0;
            const std::size_t outBase = produced * channels;
            for (std::size_t ch = 0; ch < channels; ++ch) {
                outPcm[outBase + ch] = convolveChannel(ch);
            }
            ++produced;
        }
    }
    return produced;
}

}  // namespace rhythm::nativeaudio
