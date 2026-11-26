/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <atomic>
#include <cstdint>
#include <linux/usbdevice_fs.h>

namespace rhythm::nativeaudio {

constexpr int kUsbPacketsPerUrb = 8;
constexpr int kUsbNumUrbs = 80;
constexpr int kUsbUrbBufferSize = 16384;

struct UsbUrbSlot {
    struct usbdevfs_urb* urb = nullptr;
    uint8_t* buffer = nullptr;
    int dataLength = 0;
};

struct UsbAudioContext {
    int fd = -1;
    int interfaceId = 0;
    int endpointOut = 0;
    int endpointFeedback = 0;
    int32_t sampleRate = 48000;
    int32_t channelCount = 2;
    int32_t bitDepth = 24;
    int32_t bytesPerSample = 3;
    int32_t bytesPerFrame = 6;
    int32_t maxPacketSize = 0;
    int32_t dataInterval = 1;
    int32_t feedbackPacketSize = 4;
    int32_t feedbackInterval = 1;
    int32_t usbSpeed = 0;

    std::atomic<bool> running{false};
    uint8_t* transferBuffer = nullptr;
    int32_t transferBufferCapacity = 0;
    int64_t framesWritten = 0;

    UsbUrbSlot ring[kUsbNumUrbs]{};
    int submitIdx = 0;
    int reapIdx = 0;
    int urbsInFlight = 0;
    bool ringAllocated = false;

    double frameAccumulator = 0.0;
    double calibratedFpmf = 0.0;
    uint8_t residualBuffer[kUsbUrbBufferSize]{};
    int residualBytes = 0;

    struct usbdevfs_urb* feedbackUrb = nullptr;
    uint8_t feedbackBuffer[4]{};
    bool feedbackInFlight = false;
    bool continuousFeedback = false;
    int64_t feedbackCount = 0;
};

UsbAudioContext* createUsbAudioContext(
    int fd, int ifId, int epOut, int epFb,
    int rate, int ch, int bits, int maxPkt, int dataInterval,
    int feedbackPacketSize, int feedbackInterval, bool continuousFeedback);
bool setUsbAltSetting(UsbAudioContext* ctx, int altSetting);
bool setUsbSampleRate(UsbAudioContext* ctx, int sampleRateHz);
bool startUsbAudioContext(UsbAudioContext* ctx);
void writeUsbAudioFloat(UsbAudioContext* ctx, const float* samples, int totalSamples);
void writeUsbAudioRaw(UsbAudioContext* ctx, const uint8_t* pcm, int inputBytes, int inputBitDepth);
void stopUsbAudioContext(UsbAudioContext* ctx);
int drainUsbAudioUrbs(UsbAudioContext* ctx);
void destroyUsbAudioContext(UsbAudioContext* ctx);

}  // namespace rhythm::nativeaudio
