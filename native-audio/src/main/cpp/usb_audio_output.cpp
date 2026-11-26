/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "usb_audio_output.h"

#include <android/log.h>
#include <linux/usb/ch9.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <new>

#ifndef USBDEVFS_URB_ISO_ASAP
#define USBDEVFS_URB_ISO_ASAP 0x02
#endif

namespace rhythm::nativeaudio {

namespace {

constexpr const char* kUsbTag = "RhythmUsbAudio";
#define USB_LOGI(...) __android_log_print(ANDROID_LOG_INFO, kUsbTag, __VA_ARGS__)
#define USB_LOGW(...) __android_log_print(ANDROID_LOG_WARN, kUsbTag, __VA_ARGS__)
#define USB_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kUsbTag, __VA_ARGS__)

inline float clampFloat(float v) {
    return v > 1.0f ? 1.0f : (v < -1.0f ? -1.0f : v);
}

void convertFloatToInt16(const float* src, uint8_t* dst, int n) {
    auto* out = reinterpret_cast<int16_t*>(dst);
    for (int i = 0; i < n; ++i) {
        const long v = std::clamp(std::lrintf(clampFloat(src[i]) * 32768.0f), -32768L, 32767L);
        out[i] = static_cast<int16_t>(v);
    }
}

void convertFloatToInt24(const float* src, uint8_t* dst, int n) {
    for (int i = 0; i < n; ++i) {
        const long v = std::clamp(std::lrintf(clampFloat(src[i]) * 8388608.0f), -8388608L, 8388607L);
        dst[i * 3] = static_cast<uint8_t>(v & 0xFF);
        dst[i * 3 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
        dst[i * 3 + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
    }
}

void convertFloatToInt32(const float* src, uint8_t* dst, int n) {
    auto* out = reinterpret_cast<int32_t*>(dst);
    for (int i = 0; i < n; ++i) {
        const double s = static_cast<double>(clampFloat(src[i])) * 2147483648.0;
        const long long v = std::clamp(std::llround(s), -2147483648LL, 2147483647LL);
        out[i] = static_cast<int32_t>(v);
    }
}

void padInt16ToInt24(const uint8_t* src, uint8_t* dst, int n) {
    for (int i = 0; i < n; ++i) {
        dst[i * 3] = 0;
        dst[i * 3 + 1] = src[i * 2];
        dst[i * 3 + 2] = src[i * 2 + 1];
    }
}

void padInt16ToInt32(const uint8_t* src, uint8_t* dst, int n) {
    auto* out = reinterpret_cast<int32_t*>(dst);
    const auto* in16 = reinterpret_cast<const int16_t*>(src);
    for (int i = 0; i < n; ++i) {
        out[i] = static_cast<int32_t>(static_cast<uint32_t>(static_cast<int32_t>(in16[i])) << 16);
    }
}

void padInt24ToInt32(const uint8_t* src, uint8_t* dst, int n) {
    auto* out = reinterpret_cast<int32_t*>(dst);
    for (int i = 0; i < n; ++i) {
        uint32_t u = static_cast<uint32_t>(src[i * 3]) |
            (static_cast<uint32_t>(src[i * 3 + 1]) << 8) |
            (static_cast<uint32_t>(src[i * 3 + 2]) << 16);
        if (u & 0x800000u) u |= 0xFF000000u;
        out[i] = static_cast<int32_t>(u << 8);
    }
}

void packInt32ToInt24(const uint8_t* src, uint8_t* dst, int n) {
    for (int i = 0; i < n; ++i) {
        dst[i * 3] = src[i * 4 + 1];
        dst[i * 3 + 1] = src[i * 4 + 2];
        dst[i * 3 + 2] = src[i * 4 + 3];
    }
}

void packInt24ToInt16(const uint8_t* src, uint8_t* dst, int n) {
    for (int i = 0; i < n; ++i) {
        dst[i * 2] = src[i * 3 + 1];
        dst[i * 2 + 1] = src[i * 3 + 2];
    }
}

void packInt32ToInt16(const uint8_t* src, uint8_t* dst, int n) {
    for (int i = 0; i < n; ++i) {
        dst[i * 2] = src[i * 4 + 2];
        dst[i * 2 + 1] = src[i * 4 + 3];
    }
}

bool allocRing(UsbAudioContext* ctx) {
    const size_t urbSize = sizeof(struct usbdevfs_urb) +
        kUsbPacketsPerUrb * sizeof(struct usbdevfs_iso_packet_desc);
    for (int i = 0; i < kUsbNumUrbs; ++i) {
        ctx->ring[i].urb = static_cast<struct usbdevfs_urb*>(std::calloc(1, urbSize));
        ctx->ring[i].buffer = static_cast<uint8_t*>(std::malloc(kUsbUrbBufferSize));
        ctx->ring[i].dataLength = 0;
        if (!ctx->ring[i].urb || !ctx->ring[i].buffer) {
            for (int j = 0; j <= i; ++j) {
                std::free(ctx->ring[j].urb);
                std::free(ctx->ring[j].buffer);
                ctx->ring[j].urb = nullptr;
                ctx->ring[j].buffer = nullptr;
            }
            return false;
        }
    }
    ctx->ringAllocated = true;
    return true;
}

void freeRing(UsbAudioContext* ctx) {
    if (!ctx->ringAllocated) return;
    for (int i = 0; i < kUsbNumUrbs; ++i) {
        std::free(ctx->ring[i].urb);
        std::free(ctx->ring[i].buffer);
        ctx->ring[i].urb = nullptr;
        ctx->ring[i].buffer = nullptr;
    }
    ctx->ringAllocated = false;
}

double packetPeriodMicroframes(const UsbAudioContext* ctx) {
    const int interval = ctx->dataInterval > 0 ? ctx->dataInterval : 1;
    if (ctx->usbSpeed == USB_SPEED_FULL) return 8.0 * interval;
    return static_cast<double>(1u << std::min(interval - 1, 15));
}

bool submitFeedbackUrb(UsbAudioContext* ctx) {
    if (!ctx->continuousFeedback || ctx->endpointFeedback <= 0 || !ctx->feedbackUrb ||
        ctx->feedbackInFlight) {
        return false;
    }
    struct usbdevfs_urb* u = ctx->feedbackUrb;
    const size_t sz = sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc);
    std::memset(u, 0, sz);
    u->type = USBDEVFS_URB_TYPE_ISO;
    u->flags = USBDEVFS_URB_ISO_ASAP;
    u->endpoint = static_cast<unsigned char>(ctx->endpointFeedback);
    u->buffer = ctx->feedbackBuffer;
    u->buffer_length = ctx->feedbackPacketSize;
    u->number_of_packets = 1;
    u->iso_frame_desc[0].length = static_cast<unsigned>(ctx->feedbackPacketSize);
    if (ioctl(ctx->fd, USBDEVFS_SUBMITURB, u) < 0) {
        USB_LOGW("submitFeedbackUrb failed errno=%d (%s)", errno, std::strerror(errno));
        return false;
    }
    ctx->feedbackInFlight = true;
    return true;
}

void handleFeedbackCompletion(UsbAudioContext* ctx) {
    ctx->feedbackInFlight = false;
    ctx->feedbackCount++;
    if (ctx->feedbackUrb->iso_frame_desc[0].actual_length >= static_cast<unsigned>(ctx->feedbackPacketSize)) {
        const uint8_t* fb = ctx->feedbackBuffer;
        uint32_t raw = static_cast<uint32_t>(fb[0]) |
            (static_cast<uint32_t>(fb[1]) << 8) |
            (static_cast<uint32_t>(fb[2]) << 16);
        if (ctx->feedbackPacketSize == 4) raw |= static_cast<uint32_t>(fb[3]) << 24;
        double newFpmf = raw / (ctx->feedbackPacketSize == 3 ? 16384.0 : 65536.0);
        const double period = ctx->usbSpeed == USB_SPEED_FULL
            ? static_cast<double>(ctx->dataInterval)
            : packetPeriodMicroframes(ctx);
        const double nominal = ctx->sampleRate * period / 8000.0;
        newFpmf *= period;
        if (newFpmf > nominal * 0.99 && newFpmf < nominal * 1.01) {
            ctx->calibratedFpmf = newFpmf;
        }
    }
    if (ctx->running.load()) submitFeedbackUrb(ctx);
}

int reapOldestUrb(UsbAudioContext* ctx, int timeoutMs) {
    struct usbdevfs_urb* completed = nullptr;
    const int iterations = timeoutMs * 8;
    for (int i = 0; i < iterations; ++i) {
        const int ret = ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &completed);
        if (ret == 0 && completed != nullptr) {
            if (completed == ctx->feedbackUrb) {
                handleFeedbackCompletion(ctx);
                completed = nullptr;
                continue;
            }
            ctx->reapIdx = (ctx->reapIdx + 1) % kUsbNumUrbs;
            if (ctx->urbsInFlight > 0) ctx->urbsInFlight--;
            return 0;
        }
        if (ret < 0 && errno != EAGAIN) return -1;
        usleep(125);
    }
    return -2;
}

int submitRingUrb(UsbAudioContext* ctx, const int* pktSizes, int numPackets, int totalBytes) {
    UsbUrbSlot* slot = &ctx->ring[ctx->submitIdx];
    struct usbdevfs_urb* u = slot->urb;
    const size_t clearSize = sizeof(struct usbdevfs_urb) +
        static_cast<size_t>(numPackets) * sizeof(struct usbdevfs_iso_packet_desc);
    std::memset(u, 0, clearSize);
    u->type = USBDEVFS_URB_TYPE_ISO;
    u->flags = USBDEVFS_URB_ISO_ASAP;
    u->endpoint = static_cast<unsigned char>(ctx->endpointOut);
    u->buffer = slot->buffer;
    u->buffer_length = totalBytes;
    u->number_of_packets = numPackets;
    for (int i = 0; i < numPackets; ++i) {
        u->iso_frame_desc[i].length = static_cast<unsigned>(pktSizes[i]);
    }
    slot->dataLength = totalBytes;
    if (ioctl(ctx->fd, USBDEVFS_SUBMITURB, u) < 0) {
        USB_LOGE("USBDEVFS_SUBMITURB failed errno=%d (%s)", errno, std::strerror(errno));
        return -1;
    }
    ctx->submitIdx = (ctx->submitIdx + 1) % kUsbNumUrbs;
    ctx->urbsInFlight++;
    return 0;
}

void submitPcmToUrbs(UsbAudioContext* ctx, const uint8_t* pcmData, int totalBytes) {
    const uint8_t* data = pcmData;
    int dataLen = totalBytes;
    uint8_t* merged = nullptr;
    if (ctx->residualBytes > 0 && totalBytes > 0) {
        dataLen = ctx->residualBytes + totalBytes;
        merged = static_cast<uint8_t*>(std::malloc(static_cast<size_t>(dataLen)));
        if (merged) {
            std::memcpy(merged, ctx->residualBuffer, static_cast<size_t>(ctx->residualBytes));
            std::memcpy(merged + ctx->residualBytes, pcmData, static_cast<size_t>(totalBytes));
            data = merged;
        }
        ctx->residualBytes = 0;
    }

    int offset = 0;
    const double fpmf = ctx->calibratedFpmf;
    while (offset < dataLen && ctx->running.load()) {
        int pktSizes[kUsbPacketsPerUrb]{};
        int numPackets = 0;
        int urbBytes = 0;
        const double savedAccumulator = ctx->frameAccumulator;
        for (int p = 0; p < kUsbPacketsPerUrb; ++p) {
            const int remaining = dataLen - offset - urbBytes;
            if (remaining <= 0) break;
            ctx->frameAccumulator += fpmf;
            const int frames = static_cast<int>(ctx->frameAccumulator);
            ctx->frameAccumulator -= frames;
            const int b = frames * ctx->bytesPerFrame;
            if (ctx->maxPacketSize > 0 && b > ctx->maxPacketSize) {
                ctx->running.store(false);
                break;
            }
            if (urbBytes + b > kUsbUrbBufferSize || b > remaining) break;
            pktSizes[p] = b;
            urbBytes += b;
            numPackets++;
        }
        if (numPackets <= 0 || urbBytes <= 0) {
            ctx->frameAccumulator = savedAccumulator;
            break;
        }
        if (numPackets < kUsbPacketsPerUrb) {
            ctx->frameAccumulator = savedAccumulator;
            const int leftover = dataLen - offset;
            if (leftover > 0 && leftover <= kUsbUrbBufferSize) {
                std::memcpy(ctx->residualBuffer, data + offset, static_cast<size_t>(leftover));
                ctx->residualBytes = leftover;
            }
            break;
        }
        if (ctx->urbsInFlight >= kUsbNumUrbs) {
            int res = reapOldestUrb(ctx, 200);
            if (res == -2) res = reapOldestUrb(ctx, 300);
            if (res < 0) {
                ctx->running.store(false);
                std::free(merged);
                return;
            }
        }
        UsbUrbSlot* slot = &ctx->ring[ctx->submitIdx];
        if (slot->dataLength > urbBytes) {
            std::memset(slot->buffer + urbBytes, 0, static_cast<size_t>(slot->dataLength - urbBytes));
        }
        std::memcpy(slot->buffer, data + offset, static_cast<size_t>(urbBytes));
        if (submitRingUrb(ctx, pktSizes, numPackets, urbBytes) < 0) {
            ctx->running.store(false);
            std::free(merged);
            return;
        }
        offset += urbBytes;
    }

    const int leftover = dataLen - offset;
    if (leftover > 0 && leftover <= kUsbUrbBufferSize && ctx->residualBytes == 0) {
        std::memcpy(ctx->residualBuffer, data + offset, static_cast<size_t>(leftover));
        ctx->residualBytes = leftover;
    }
    std::free(merged);
}

}  // namespace

UsbAudioContext* createUsbAudioContext(
    int fd, int ifId, int epOut, int epFb,
    int rate, int ch, int bits, int maxPkt, int dataInterval,
    int feedbackPacketSize, int feedbackInterval, bool continuousFeedback) {
    if (fd < 0 || ch <= 0 || (bits != 16 && bits != 24 && bits != 32) || rate <= 0) return nullptr;
    const int usbSpeed = ioctl(fd, USBDEVFS_GET_SPEED, 0);
    if (usbSpeed != USB_SPEED_FULL && usbSpeed != USB_SPEED_HIGH &&
        usbSpeed != USB_SPEED_SUPER && usbSpeed != USB_SPEED_SUPER_PLUS) {
        return nullptr;
    }
    auto* ctx = new (std::nothrow) UsbAudioContext();
    if (!ctx) return nullptr;
    ctx->fd = fd;
    ctx->interfaceId = ifId;
    ctx->endpointOut = epOut;
    ctx->endpointFeedback = epFb;
    ctx->sampleRate = rate;
    ctx->channelCount = ch;
    ctx->bitDepth = bits;
    ctx->bytesPerSample = bits / 8;
    ctx->bytesPerFrame = ctx->bytesPerSample * ch;
    ctx->maxPacketSize = maxPkt;
    ctx->dataInterval = dataInterval > 0 ? dataInterval : 1;
    ctx->feedbackPacketSize = (feedbackPacketSize == 3 || feedbackPacketSize == 4) ? feedbackPacketSize : 4;
    ctx->feedbackInterval = feedbackInterval > 0 ? feedbackInterval : ctx->dataInterval;
    ctx->usbSpeed = usbSpeed;
    ctx->calibratedFpmf = rate * packetPeriodMicroframes(ctx) / 8000.0;
    ctx->continuousFeedback = continuousFeedback && epFb > 0;
    const size_t fbSz = sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc);
    ctx->feedbackUrb = static_cast<struct usbdevfs_urb*>(std::calloc(1, fbSz));
    if (!ctx->feedbackUrb || !allocRing(ctx)) {
        std::free(ctx->feedbackUrb);
        freeRing(ctx);
        delete ctx;
        return nullptr;
    }
    USB_LOGI("Created UAC stream rate=%d ch=%d bits=%d ep=0x%02x", rate, ch, bits, epOut);
    return ctx;
}

bool setUsbAltSetting(UsbAudioContext* ctx, int altSetting) {
    if (!ctx) return false;
    struct usbdevfs_setinterface si{};
    si.interface = static_cast<unsigned>(ctx->interfaceId);
    si.altsetting = static_cast<unsigned>(altSetting);
    return ioctl(ctx->fd, USBDEVFS_SETINTERFACE, &si) >= 0;
}

bool setUsbSampleRate(UsbAudioContext* ctx, int sampleRateHz) {
    if (!ctx || sampleRateHz <= 0) return false;
    ctx->sampleRate = sampleRateHz;
    ctx->calibratedFpmf = sampleRateHz * packetPeriodMicroframes(ctx) / 8000.0;
    return true;
}

bool startUsbAudioContext(UsbAudioContext* ctx) {
    if (!ctx) return false;
    ctx->running.store(true);
    ctx->framesWritten = 0;
    ctx->submitIdx = 0;
    ctx->reapIdx = 0;
    ctx->urbsInFlight = 0;
    ctx->frameAccumulator = 0.0;
    ctx->residualBytes = 0;
    ctx->calibratedFpmf = ctx->sampleRate * packetPeriodMicroframes(ctx) / 8000.0;
    submitFeedbackUrb(ctx);
    return true;
}

void writeUsbAudioFloat(UsbAudioContext* ctx, const float* samples, int totalSamples) {
    if (!ctx || !ctx->running.load() || !samples || totalSamples <= 0) return;
    const int totalBytes = totalSamples * ctx->bytesPerSample;
    if (!ctx->transferBuffer || ctx->transferBufferCapacity < totalBytes) {
        std::free(ctx->transferBuffer);
        ctx->transferBuffer = static_cast<uint8_t*>(std::malloc(static_cast<size_t>(totalBytes)));
        ctx->transferBufferCapacity = ctx->transferBuffer ? totalBytes : 0;
    }
    if (!ctx->transferBuffer) return;
    switch (ctx->bitDepth) {
        case 16: convertFloatToInt16(samples, ctx->transferBuffer, totalSamples); break;
        case 24: convertFloatToInt24(samples, ctx->transferBuffer, totalSamples); break;
        case 32: convertFloatToInt32(samples, ctx->transferBuffer, totalSamples); break;
        default: return;
    }
    submitPcmToUrbs(ctx, ctx->transferBuffer, totalBytes);
    ctx->framesWritten += totalSamples / std::max(1, ctx->channelCount);
}

void writeUsbAudioRaw(UsbAudioContext* ctx, const uint8_t* pcm, int inputBytes, int inputBitDepth) {
    if (!ctx || !ctx->running.load() || !pcm || inputBytes <= 0 || inputBitDepth <= 0) return;
    const int inputBps = inputBitDepth / 8;
    if (inputBps <= 0) return;
    const int totalSamples = inputBytes / inputBps;
    const int outputBytes = totalSamples * ctx->bytesPerSample;
    if (!ctx->transferBuffer || ctx->transferBufferCapacity < outputBytes) {
        std::free(ctx->transferBuffer);
        ctx->transferBuffer = static_cast<uint8_t*>(std::malloc(static_cast<size_t>(outputBytes)));
        ctx->transferBufferCapacity = ctx->transferBuffer ? outputBytes : 0;
    }
    if (!ctx->transferBuffer) return;

    if (inputBitDepth == ctx->bitDepth) {
        std::memcpy(ctx->transferBuffer, pcm, static_cast<size_t>(inputBytes));
    } else if (inputBitDepth == 16 && ctx->bitDepth == 24) {
        padInt16ToInt24(pcm, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 16 && ctx->bitDepth == 32) {
        padInt16ToInt32(pcm, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 24 && ctx->bitDepth == 32) {
        padInt24ToInt32(pcm, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 32 && ctx->bitDepth == 24) {
        packInt32ToInt24(pcm, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 24 && ctx->bitDepth == 16) {
        packInt24ToInt16(pcm, ctx->transferBuffer, totalSamples);
    } else if (inputBitDepth == 32 && ctx->bitDepth == 16) {
        packInt32ToInt16(pcm, ctx->transferBuffer, totalSamples);
    } else {
        return;
    }
    submitPcmToUrbs(ctx, ctx->transferBuffer, outputBytes);
    ctx->framesWritten += totalSamples / std::max(1, ctx->channelCount);
}

void stopUsbAudioContext(UsbAudioContext* ctx) {
    if (ctx) ctx->running.store(false);
}

int drainUsbAudioUrbs(UsbAudioContext* ctx) {
    if (!ctx) return 0;
    ctx->running.store(false);
    int drained = 0;
    if (ctx->feedbackInFlight && ctx->feedbackUrb) {
        ioctl(ctx->fd, USBDEVFS_DISCARDURB, ctx->feedbackUrb);
        for (int i = 0; i < 500 && ctx->feedbackInFlight; ++i) {
            struct usbdevfs_urb* c = nullptr;
            if (ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c) == 0 && c != nullptr) {
                if (c == ctx->feedbackUrb) ctx->feedbackInFlight = false;
                else if (ctx->urbsInFlight > 0) {
                    ctx->reapIdx = (ctx->reapIdx + 1) % kUsbNumUrbs;
                    ctx->urbsInFlight--;
                    drained++;
                }
                continue;
            }
            if (errno != EAGAIN) break;
            usleep(100);
        }
    }
    while (ctx->urbsInFlight > 0) {
        if (reapOldestUrb(ctx, 500) == 0) drained++;
        else break;
    }
    if (ctx->urbsInFlight > 0) {
        const int toDiscard = ctx->urbsInFlight;
        for (int i = 0; i < toDiscard; ++i) {
            const int idx = (ctx->reapIdx + i) % kUsbNumUrbs;
            ioctl(ctx->fd, USBDEVFS_DISCARDURB, ctx->ring[idx].urb);
        }
        int reaped = 0;
        for (int attempt = 0; attempt < 500 && reaped < toDiscard; ++attempt) {
            struct usbdevfs_urb* c = nullptr;
            const int ret = ioctl(ctx->fd, USBDEVFS_REAPURBNDELAY, &c);
            if (ret == 0 && c == ctx->feedbackUrb) ctx->feedbackInFlight = false;
            else if (ret == 0 && c != nullptr) reaped++;
            else if (ret < 0 && errno != EAGAIN) break;
            else usleep(1000);
        }
        drained += reaped;
    }
    ctx->urbsInFlight = 0;
    ctx->submitIdx = 0;
    ctx->reapIdx = 0;
    ctx->frameAccumulator = 0.0;
    ctx->residualBytes = 0;
    if (ctx->running.load() && ctx->endpointFeedback > 0 && !ctx->feedbackInFlight) {
        submitFeedbackUrb(ctx);
    }
    return drained;
}

void destroyUsbAudioContext(UsbAudioContext* ctx) {
    if (!ctx) return;
    ctx->running.store(false);
    if (ctx->urbsInFlight > 0 || ctx->feedbackInFlight) drainUsbAudioUrbs(ctx);
    freeRing(ctx);
    if (!ctx->feedbackInFlight) std::free(ctx->feedbackUrb);
    ctx->feedbackUrb = nullptr;
    std::free(ctx->transferBuffer);
    ctx->transferBuffer = nullptr;
    delete ctx;
}

}  // namespace rhythm::nativeaudio
