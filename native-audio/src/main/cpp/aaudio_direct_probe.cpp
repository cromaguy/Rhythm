/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "aaudio_direct_probe.h"

#include <aaudio/AAudio.h>
#include <dlfcn.h>

#include <algorithm>

namespace rhythm::nativeaudio {

namespace {
using IsMMapUsedFn = bool (*)(AAudioStream*);
}

AAudioDirectProbeResult probeAAudioDirectOutput(std::int32_t preferredChannelCount) noexcept {
    AAudioDirectProbeResult result{};
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t res = AAudio_createStreamBuilder(&builder);
    if (res != AAUDIO_OK || builder == nullptr) {
        result.status = res;
        return result;
    }

    const std::int32_t channels = std::clamp(preferredChannelCount, 1, 8);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setChannelCount(builder, channels);

    AAudioStream* stream = nullptr;
    res = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    result.status = res;
    if (res != AAUDIO_OK || stream == nullptr) {
        return result;
    }

    result.exclusive = (AAudioStream_getSharingMode(stream) == AAUDIO_SHARING_MODE_EXCLUSIVE);
    result.sampleRate = AAudioStream_getSampleRate(stream);
    result.channelCount = AAudioStream_getChannelCount(stream);
    result.format = static_cast<std::int32_t>(AAudioStream_getFormat(stream));
    result.deviceId = AAudioStream_getDeviceId(stream);
    result.framesPerBurst = AAudioStream_getFramesPerBurst(stream);
    result.bufferCapacityFrames = AAudioStream_getBufferCapacityInFrames(stream);

    auto* isMMapUsed = reinterpret_cast<IsMMapUsedFn>(dlsym(RTLD_DEFAULT, "AAudioStream_isMMapUsed"));
    if (isMMapUsed != nullptr) {
        result.mmap = isMMapUsed(stream);
    } else {
        result.mmap = result.exclusive &&
            (AAudioStream_getPerformanceMode(stream) == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    }

    AAudioStream_close(stream);
    return result;
}

}  // namespace rhythm::nativeaudio
