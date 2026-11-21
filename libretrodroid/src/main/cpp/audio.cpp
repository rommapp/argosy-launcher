/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "log.h"

#include "audio.h"
#include <algorithm>
#include <cmath>
#include <memory>

namespace libretrodroid {

namespace {
    // Ignore tempo changes within this band of 1.0 -- keeps us on the bypass
    // path during normal playback and avoids routing samples through SoundTouch
    // when the user has only nudged the speed by a negligible amount.
    constexpr double kStretchBypassEpsilon = 0.02;
}

Audio::Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio, int32_t audioLatencyMs) {
    LOGI("Audio initialization has been called with input sample rate %d", sampleRate);

    contentRefreshRate = refreshRate;
    inputSampleRate = sampleRate;
    requestedLatencyMs = audioLatencyMs;
    audioLatencySettings = findBestLatencySettings(preferLowLatencyAudio);
    initializeStream();
}

bool Audio::initializeStream() {
    LOGI("Using low latency stream: %d", audioLatencySettings->useLowLatencyStream);

    int32_t audioBufferSize = computeAudioBufferSize();

    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(2);
    builder.setDirection(oboe::Direction::Output);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    if (audioLatencySettings->useLowLatencyStream) {
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder.setFramesPerCallback(audioBufferSize / 10);
    }

    oboe::Result result = builder.openManagedStream(stream);
    if (result == oboe::Result::OK) {
        baseConversionFactor = (double) inputSampleRate / stream->getSampleRate();
        fifoBuffer = std::make_unique<oboe::FifoBuffer>(2, audioBufferSize);
        temporaryAudioBuffer = std::unique_ptr<int16_t[]>(new int16_t[audioBufferSize]);
        latencyTuner = std::make_unique<oboe::LatencyTuner>(*stream);

        // SoundTouch operates in stereo float and is configured for the emulator's
        // native sample rate. The final rate conversion to stream->getSampleRate()
        // stays in LinearResampler so SoundTouch only does time-stretch.
        timeStretcher = std::make_unique<soundtouch::SoundTouch>();
        timeStretcher->setChannels(2);
        timeStretcher->setSampleRate(inputSampleRate);
        timeStretcher->setTempo(1.0);
        timeStretcher->setRate(1.0);
        timeStretcher->setPitch(1.0);
        lastStretchTempo = 1.0;

        // audioBufferSize is an int16 sample count (stereo interleaved). Reuse the
        // same capacity for the float scratch buffers so even worst-case reads at
        // max playbackSpeed fit.
        stretchBufferFrameCapacity = audioBufferSize / 2;
        stretchInputBuffer = std::unique_ptr<float[]>(new float[audioBufferSize]);
        stretchOutputBuffer = std::unique_ptr<float[]>(new float[audioBufferSize]);
        return true;
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
        stream = nullptr;
        latencyTuner = nullptr;
        return false;
    }
}

std::unique_ptr<Audio::AudioLatencySettings> Audio::findBestLatencySettings(bool preferLowLatencyAudio) {
    if (oboe::AudioStreamBuilder::isAAudioRecommended() && preferLowLatencyAudio) {
        return std::make_unique<AudioLatencySettings>(LOW_LATENCY_SETTINGS);
    } else {
        return std::make_unique<AudioLatencySettings>(DEFAULT_LATENCY_SETTINGS);
    }
}

int32_t Audio::computeAudioBufferSize() {
    double maxLatency = computeMaximumLatency();
    LOGI("Average audio latency set to: %f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    return roundToEven(inputSampleRate / sampleRateDivisor);
}

double Audio::computeMaximumLatency() const {
    double maxLatency = requestedLatencyMs > 0
        ? (double) requestedLatencyMs
        : (audioLatencySettings->bufferSizeInVideoFrames / contentRefreshRate) * 1000;
    return std::max(maxLatency, 32.0);
}

void Audio::start() {
    startRequested = true;
    if (stream != nullptr)
        stream->requestStart();
}

void Audio::stop() {
    startRequested = false;
    if (stream != nullptr)
        stream->requestStop();
}

Audio::~Audio() {
    if (stream != nullptr) {
        stream->stop();
        stream->close();
    }
}

void Audio::write(const int16_t *data, size_t frames) {
    fifoBuffer->write(data, frames * 2);
}

void Audio::setPlaybackSpeed(const double newPlaybackSpeed) {
    playbackSpeed = newPlaybackSpeed;
}

void Audio::setPitchPreservation(bool enabled) {
    // Just flip the flag. The audio-callback thread owns the SoundTouch state and
    // will pick up the new setting on the next onAudioReady. Calling clear() or
    // setTempo() here would race with putSamples/receiveSamples.
    pitchPreservationEnabled = enabled;
}

void Audio::setOutputVolume(float volume) {
    outputVolume = volume;
}

void Audio::resetBufferState() {
    errorIntegral = 0.0;
    framesToSubmit = 0.0;
    if (fifoBuffer) {
        fifoBuffer->setReadCounter(fifoBuffer->getWriteCounter());
    }
    if (timeStretcher) {
        timeStretcher->clear();
        lastStretchTempo = 1.0;
    }
}

void Audio::updateTiming(int32_t newSampleRate, double newRefreshRate) {
    LOGI("Audio timing update: sampleRate %d -> %d, refreshRate %f -> %f",
         inputSampleRate, newSampleRate, contentRefreshRate, newRefreshRate);
    inputSampleRate = newSampleRate;
    contentRefreshRate = newRefreshRate;
    if (stream != nullptr) {
        baseConversionFactor = (double) inputSampleRate / stream->getSampleRate();
    }
    if (timeStretcher) {
        timeStretcher->setSampleRate(inputSampleRate);
        timeStretcher->clear();
        lastStretchTempo = 1.0;
    }
    errorIntegral = 0.0;
    framesToSubmit = 0.0;
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    double dynamicBufferFactor = computeDynamicBufferConversionFactor(0.001 * numFrames);
    const bool stretchActive = pitchPreservationEnabled && timeStretcher != nullptr &&
        std::abs(playbackSpeed - 1.0) > kStretchBypassEpsilon;

    // FIFO read rate covers sample-rate conversion, PI-controller buffer balancing, and
    // tempo. Without stretching, resampling the oversized buffer down to numFrames yields
    // a speed+pitch-shifted output in one pass. With stretching, SoundTouch folds the
    // tempo into the time axis (pitch preserved), and the resampler handles only
    // inputSampleRate -> stream->getSampleRate().
    double fifoReadFactor = baseConversionFactor * dynamicBufferFactor * playbackSpeed;

    framesToSubmit += numFrames * fifoReadFactor;
    int32_t currentFramesToSubmit = std::round(framesToSubmit);
    framesToSubmit -= currentFramesToSubmit;

    if (currentFramesToSubmit > stretchBufferFrameCapacity) {
        currentFramesToSubmit = stretchBufferFrameCapacity;
    }

    fifoBuffer->readNow(temporaryAudioBuffer.get(), currentFramesToSubmit * 2);

    auto outputArray = reinterpret_cast<int16_t *>(audioData);

    if (stretchActive) {
        if (std::abs(playbackSpeed - lastStretchTempo) > 1e-6) {
            timeStretcher->setTempo(playbackSpeed);
            lastStretchTempo = playbackSpeed;
        }

        const int32_t sampleCount = currentFramesToSubmit * 2;
        constexpr float kInt16ToFloat = 1.0f / 32768.0f;
        for (int32_t i = 0; i < sampleCount; ++i) {
            stretchInputBuffer[i] = temporaryAudioBuffer[i] * kInt16ToFloat;
        }
        timeStretcher->putSamples(stretchInputBuffer.get(), currentFramesToSubmit);

        const int32_t wantedOutputFrames = std::min(
            (int32_t) std::round(currentFramesToSubmit / playbackSpeed),
            stretchBufferFrameCapacity
        );
        const uint received = timeStretcher->receiveSamples(
            stretchOutputBuffer.get(),
            (uint) wantedOutputFrames
        );
        const int32_t gotFrames = (int32_t) received;

        const int32_t gotSamples = gotFrames * 2;
        for (int32_t i = 0; i < gotSamples; ++i) {
            float s = stretchOutputBuffer[i] * 32768.0f;
            if (s > 32767.0f) s = 32767.0f;
            else if (s < -32768.0f) s = -32768.0f;
            temporaryAudioBuffer[i] = (int16_t) std::lrintf(s);
        }
        for (int32_t i = gotSamples; i < wantedOutputFrames * 2; ++i) {
            temporaryAudioBuffer[i] = 0;
        }
        currentFramesToSubmit = wantedOutputFrames;
    }

    resampler.resample(temporaryAudioBuffer.get(), currentFramesToSubmit, outputArray, numFrames);

    if (outputVolume != 1.0f) {
        const int32_t totalSamples = numFrames * 2;
        for (int32_t i = 0; i < totalSamples; ++i) {
            float s = outputArray[i] * outputVolume;
            if (s > 32767.0f) s = 32767.0f;
            else if (s < -32768.0f) s = -32768.0f;
            outputArray[i] = (int16_t) std::lrintf(s);
        }
    }

    latencyTuner->tune();

    return oboe::DataCallbackResult::Continue;
}

// To prevent audio buffer overruns or underruns we set up a PI controller. The idea is to run the
// audio slower when the buffer is empty and faster when it's full.
double Audio::computeDynamicBufferConversionFactor(double dt) {
    double framesCapacityInBuffer = fifoBuffer->getBufferCapacityInFrames();
    double framesAvailableInBuffer = fifoBuffer->getFullFramesAvailable();

    // Error is represented by normalized distance to half buffer utilization. Range [-1.0, 1.0]
    double errorMeasure = (framesCapacityInBuffer - 2.0f * framesAvailableInBuffer) / framesCapacityInBuffer;

    errorIntegral = std::clamp(errorIntegral + errorMeasure * dt, -maxi / ki, maxi / ki);

    // Wikipedia states that human ear resolution is around 3.6 Hz within the octave of 1000–2000 Hz.
    // This changes continuously, so we should try to keep it a very low value.
    double proportionalAdjustment = std::clamp(kp * errorMeasure, -maxp, maxp);

    // Ki is a lot lower, so it's safe if it exceeds the ear threshold. Hopefully convergence will
    // be slow enough to be not perceptible. We need to battle test this value.
    double integralAdjustment = std::clamp(ki * errorIntegral, -maxi, maxi);

    double finalAdjustment = proportionalAdjustment + integralAdjustment;

    LOGD("Audio speed adjustments (p: %f) (i: %f)", proportionalAdjustment, integralAdjustment);

    return 1.0 - (finalAdjustment);
}

int32_t Audio::roundToEven(int32_t x) {
    return (x / 2) * 2;
}

void Audio::onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) {
    AudioStreamErrorCallback::onErrorAfterClose(oldStream, result);
    LOGI("Stream error in oboe::onErrorAfterClose %s", oboe::convertToText(result));

    if (result != oboe::Result::ErrorDisconnected)
        return;

    initializeStream();
    if (startRequested) {
        start();
    }
}

} //namespace libretrodroid
