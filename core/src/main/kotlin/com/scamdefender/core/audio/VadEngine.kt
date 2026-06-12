package com.scamdefender.core.audio

import com.scamdefender.core.domain.AudioChunk

interface VadEngine {
    fun isSpeech(chunk: AudioChunk): Boolean
}

/**
 * Energy-based VAD fallback for JVM demo when Silero ONNX model is unavailable.
 * Production Android build should use SileroVadEngine with OnnxRuntime.
 */
class EnergyVadEngine(
    private val energyThreshold: Double = 500.0,
) : VadEngine {
    override fun isSpeech(chunk: AudioChunk): Boolean {
        if (chunk.pcmSamples.isEmpty()) return false
        val energy = chunk.pcmSamples.map { it * it.toDouble() }.average()
        return energy > energyThreshold
    }
}
