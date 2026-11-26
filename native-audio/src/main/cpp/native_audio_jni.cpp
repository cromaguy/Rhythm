/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "aaudio_direct_probe.h"
#include "dsd_decimator.h"
#include "sinc_resampler.h"
#include "usb_audio_output.h"

#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <new>

namespace {
using rhythm::nativeaudio::DsdDecimator;
using rhythm::nativeaudio::SincResampler;
using rhythm::nativeaudio::UsbAudioContext;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeCreateDsdDecimator(
    JNIEnv*, jobject, jint channelCount, jint decimationFactor, jboolean lsbFirst) {
    auto* ptr = new (std::nothrow) DsdDecimator(channelCount, decimationFactor, lsbFirst == JNI_TRUE);
    return reinterpret_cast<jlong>(ptr);
}

JNIEXPORT jint JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeProcessDsd(
    JNIEnv* env, jobject, jlong handle, jbyteArray input, jint offset, jint length,
    jboolean planar, jint blockBytesPerChannel, jfloatArray outputPcm) {
    auto* dec = reinterpret_cast<DsdDecimator*>(static_cast<std::uintptr_t>(handle));
    if (!dec || !input || !outputPcm || length <= 0 || offset < 0) return 0;

    const jint inLen = env->GetArrayLength(input);
    if (offset + length > inLen) return 0;

    jbyte* inPtr = env->GetByteArrayElements(input, nullptr);
    jfloat* outPtr = env->GetFloatArrayElements(outputPcm, nullptr);
    if (!inPtr || !outPtr) {
        if (inPtr) env->ReleaseByteArrayElements(input, inPtr, JNI_ABORT);
        if (outPtr) env->ReleaseFloatArrayElements(outputPcm, outPtr, JNI_ABORT);
        return 0;
    }

    const auto* unsignedBytes = reinterpret_cast<const std::uint8_t*>(inPtr + offset);
    std::size_t producedFrames = 0;
    if (planar == JNI_TRUE && blockBytesPerChannel > 0) {
        producedFrames = dec->processPlanar(unsignedBytes, static_cast<std::size_t>(blockBytesPerChannel), outPtr);
    } else {
        const std::size_t ch = static_cast<std::size_t>(std::max(1, dec->channelCount()));
        producedFrames = dec->processInterleaved(unsignedBytes, static_cast<std::size_t>(length) / ch, outPtr);
    }

    env->ReleaseByteArrayElements(input, inPtr, JNI_ABORT);
    env->ReleaseFloatArrayElements(outputPcm, outPtr, 0);
    return static_cast<jint>(producedFrames * static_cast<std::size_t>(dec->channelCount()));
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeResetDsdDecimator(
    JNIEnv*, jobject, jlong handle) {
    if (auto* dec = reinterpret_cast<DsdDecimator*>(static_cast<std::uintptr_t>(handle))) {
        dec->reset();
    }
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeDestroyDsdDecimator(
    JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<DsdDecimator*>(static_cast<std::uintptr_t>(handle));
}

JNIEXPORT jlong JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeCreateSincResampler(
    JNIEnv*, jobject, jint channelCount, jint inputRateHz, jint outputRateHz) {
    auto* ptr = new (std::nothrow) SincResampler(channelCount, inputRateHz, outputRateHz);
    return reinterpret_cast<jlong>(ptr);
}

JNIEXPORT jint JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeProcessSincResampler(
    JNIEnv* env, jobject, jlong handle, jfloatArray inputPcm, jint inputFrames,
    jfloatArray outputPcm, jint maxOutputCapacityFrames) {
    auto* rs = reinterpret_cast<SincResampler*>(static_cast<std::uintptr_t>(handle));
    if (!rs || !inputPcm || !outputPcm || inputFrames <= 0 || maxOutputCapacityFrames <= 0) return 0;

    jfloat* inPtr = env->GetFloatArrayElements(inputPcm, nullptr);
    jfloat* outPtr = env->GetFloatArrayElements(outputPcm, nullptr);
    if (!inPtr || !outPtr) {
        if (inPtr) env->ReleaseFloatArrayElements(inputPcm, inPtr, JNI_ABORT);
        if (outPtr) env->ReleaseFloatArrayElements(outputPcm, outPtr, JNI_ABORT);
        return 0;
    }

    const std::size_t produced = rs->processInterleaved(
        inPtr,
        static_cast<std::size_t>(inputFrames),
        outPtr,
        static_cast<std::size_t>(maxOutputCapacityFrames)
    );

    env->ReleaseFloatArrayElements(inputPcm, inPtr, JNI_ABORT);
    env->ReleaseFloatArrayElements(outputPcm, outPtr, 0);
    return static_cast<jint>(produced);
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeResetSincResampler(
    JNIEnv*, jobject, jlong handle) {
    if (auto* rs = reinterpret_cast<SincResampler*>(static_cast<std::uintptr_t>(handle))) {
        rs->reset();
    }
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeDestroySincResampler(
    JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<SincResampler*>(static_cast<std::uintptr_t>(handle));
}

JNIEXPORT jintArray JNICALL
Java_chromahub_rhythm_nativeaudio_NativeAudioBridge_nativeProbeAAudioDirectOutput(
    JNIEnv* env, jobject, jint preferredChannels) {
    const auto probe = rhythm::nativeaudio::probeAAudioDirectOutput(preferredChannels);
    jint values[9] = {
        probe.status,
        probe.exclusive ? 1 : 0,
        probe.mmap ? 1 : 0,
        probe.sampleRate,
        probe.channelCount,
        probe.format,
        probe.deviceId,
        probe.framesPerBurst,
        probe.bufferCapacityFrames
    };
    jintArray out = env->NewIntArray(9);
    if (out != nullptr) env->SetIntArrayRegion(out, 0, 9, values);
    return out;
}

JNIEXPORT jlong JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioCreate(
    JNIEnv*, jobject, jint fd, jint ifId, jint epOut, jint epFb,
    jint rate, jint ch, jint bits, jint maxPkt, jint dataInterval,
    jint feedbackPacketSize, jint feedbackInterval, jboolean continuousFeedback) {
    auto* ctx = rhythm::nativeaudio::createUsbAudioContext(
        fd, ifId, epOut, epFb, rate, ch, bits, maxPkt, dataInterval,
        feedbackPacketSize, feedbackInterval, continuousFeedback == JNI_TRUE);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jboolean JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioSetAltSetting(
    JNIEnv*, jobject, jlong handle, jint altSetting) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    return rhythm::nativeaudio::setUsbAltSetting(ctx, altSetting) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioSetSampleRate(
    JNIEnv*, jobject, jlong handle, jint sampleRateHz) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    return rhythm::nativeaudio::setUsbSampleRate(ctx, sampleRateHz) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioStart(
    JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    return rhythm::nativeaudio::startUsbAudioContext(ctx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioWrite(
    JNIEnv* env, jobject, jlong handle, jfloatArray pcm) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    if (!ctx || !pcm) return;
    const jint len = env->GetArrayLength(pcm);
    jfloat* ptr = env->GetFloatArrayElements(pcm, nullptr);
    if (ptr) {
        rhythm::nativeaudio::writeUsbAudioFloat(ctx, ptr, len);
        env->ReleaseFloatArrayElements(pcm, ptr, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioWriteRaw(
    JNIEnv* env, jobject, jlong handle, jbyteArray pcm, jint inputBitDepth) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    if (!ctx || !pcm) return;
    const jint len = env->GetArrayLength(pcm);
    jbyte* ptr = env->GetByteArrayElements(pcm, nullptr);
    if (ptr) {
        rhythm::nativeaudio::writeUsbAudioRaw(ctx, reinterpret_cast<const uint8_t*>(ptr), len, inputBitDepth);
        env->ReleaseByteArrayElements(pcm, ptr, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioStop(
    JNIEnv*, jobject, jlong handle) {
    rhythm::nativeaudio::stopUsbAudioContext(
        reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle)));
}

JNIEXPORT jint JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioDrainUrbs(
    JNIEnv*, jobject, jlong handle) {
    return rhythm::nativeaudio::drainUsbAudioUrbs(
        reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle)));
}

JNIEXPORT void JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioDestroy(
    JNIEnv*, jobject, jlong handle) {
    rhythm::nativeaudio::destroyUsbAudioContext(
        reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle)));
}

JNIEXPORT jboolean JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioIsRunning(
    JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    return (ctx && ctx->running.load()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_chromahub_rhythm_nativeaudio_UsbAudioStream_nativeUsbAudioFramesWritten(
    JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<UsbAudioContext*>(static_cast<std::uintptr_t>(handle));
    return ctx ? static_cast<jlong>(ctx->framesWritten) : 0L;
}

}  // extern "C"
