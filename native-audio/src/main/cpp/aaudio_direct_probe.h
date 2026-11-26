/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <cstdint>

namespace rhythm::nativeaudio {

struct AAudioDirectProbeResult {
    std::int32_t status = -1;
    bool exclusive = false;
    bool mmap = false;
    std::int32_t sampleRate = 0;
    std::int32_t channelCount = 0;
    std::int32_t format = 0;
    std::int32_t deviceId = 0;
    std::int32_t framesPerBurst = 0;
    std::int32_t bufferCapacityFrames = 0;
};

AAudioDirectProbeResult probeAAudioDirectOutput(std::int32_t preferredChannelCount) noexcept;

}  // namespace rhythm::nativeaudio
