package com.scamdefender.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import com.scamdefender.core.domain.AudioChunk
import com.scamdefender.core.ml.ManagedOnnxSession
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.ml.OnnxRuntimeHolder
import java.nio.LongBuffer
import kotlin.math.min

/**
 * Silero VAD v4/v5 ONNX inference with recurrent state.
 * Falls back to [EnergyVadEngine] when the model file is missing.
 */
class SileroVadEngine(
    modelPaths: ModelPaths = ModelPaths.resolve(),
    private val threshold: Float = 0.5f,
    private val fallback: VadEngine = EnergyVadEngine(),
) : VadEngine {
    private val managed = ManagedOnnxSession(modelPaths.sileroVad)
    private val session: OrtSession? = managed.session

    private val windowSize = 512
    private val contextSize = 64
    private val effectiveWindow = windowSize + contextSize
    private val stateTensor = FloatArray(2 * 1 * 128)

    private var context = FloatArray(contextSize)
    private var sampleRate = 16_000
    private val usesCombinedState: Boolean

    init {
        usesCombinedState = session?.inputNames?.contains("state") == true
    }

    override fun isSpeech(chunk: AudioChunk): Boolean {
        if (session == null) return fallback.isSpeech(chunk)
        if (chunk.pcmSamples.isEmpty()) return false

        sampleRate = chunk.sampleRateHz
        val samples = pcmToFloat(chunk.pcmSamples)
        if (samples.size < windowSize / 4) return fallback.isSpeech(chunk)

        var offset = 0
        var maxProb = 0f
        var speechWindows = 0
        var totalWindows = 0

        while (offset < samples.size) {
            val end = min(offset + windowSize, samples.size)
            val window = samples.copyOfRange(offset, end)
            val padded =
                if (window.size < windowSize) {
                    FloatArray(windowSize).also { window.copyInto(it) }
                } else {
                    window
                }
            val prob = runInference(padded)
            maxProb = maxOf(maxProb, prob)
            if (prob >= threshold) speechWindows++
            totalWindows++
            if (end >= samples.size) break
            offset += windowSize
        }

        return maxProb >= threshold || (totalWindows > 0 && speechWindows.toFloat() / totalWindows >= 0.3f)
    }

    fun reset() {
        context = FloatArray(contextSize)
        stateTensor.fill(0f)
    }

    private fun runInference(window: FloatArray): Float {
        val session = session ?: return 0f
        val inputAudio = FloatArray(effectiveWindow)
        context.copyInto(inputAudio, 0, 0, contextSize)
        window.copyInto(inputAudio, contextSize, 0, min(window.size, windowSize))
        context = inputAudio.copyOfRange(effectiveWindow - contextSize, effectiveWindow)

        return session.run(buildInputs(inputAudio)).use { result ->
            val prob = extractProbability(result.get(0).value)
            if (result.size() > 1) {
                copyStateOutput(result.get(1).value, stateTensor)
            }
            prob
        }
    }

    private fun buildInputs(audio: FloatArray): Map<String, OnnxTensor> {
        val env = OnnxRuntimeHolder.environment
        val inputs = linkedMapOf<String, OnnxTensor>()
        val names = session?.inputNames.orEmpty().toSet()

        when {
            "input" in names -> inputs["input"] = OnnxTensor.createTensor(env, arrayOf(audio))
            "x" in names -> inputs["x"] = OnnxTensor.createTensor(env, arrayOf(audio))
        }

        if ("sr" in names) {
            inputs["sr"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(sampleRate.toLong())), longArrayOf())
        }

        if (usesCombinedState && "state" in names) {
            inputs["state"] = OnnxTensor.createTensor(env, wrapState(stateTensor, 2, 1, 128))
        } else {
            if ("h" in names) inputs["h"] = OnnxTensor.createTensor(env, wrapState(stateTensor, 2, 1, 64))
            if ("c" in names) inputs["c"] = OnnxTensor.createTensor(env, wrapState(FloatArray(128), 2, 1, 64))
        }

        return inputs
    }

    private fun copyStateOutput(value: Any?, target: FloatArray) {
        val flat = flatten(value) ?: return
        flat.copyInto(target, 0, 0, minOf(flat.size, target.size))
    }

    private fun flatten(value: Any?): FloatArray? =
        when (value) {
            is FloatArray -> value
            is Array<*> -> {
                when (val first = value.firstOrNull()) {
                    is FloatArray -> {
                        if (value.size == 1) first else flattenNested(value)
                    }
                    is Array<*> -> flattenNested(value)
                    else -> null
                }
            }
            else -> null
        }

    private fun flattenNested(value: Array<*>): FloatArray? {
        @Suppress("UNCHECKED_CAST")
        val level1 = value as Array<Array<FloatArray>>
        val total = level1.sumOf { layer -> layer.sumOf { row -> row.size } }
        val out = FloatArray(total)
        var idx = 0
        for (layer in level1) {
            for (row in layer) {
                for (v in row) out[idx++] = v
            }
        }
        return out
    }

    private fun extractProbability(value: Any?): Float =
        when (value) {
            is FloatArray -> value.firstOrNull() ?: 0f
            is Array<*> -> {
                when (val first = value.firstOrNull()) {
                    is FloatArray -> first.firstOrNull() ?: 0f
                    is Float -> first
                    else -> 0f
                }
            }
            is Float -> value
            else -> 0f
        }

    private fun wrapState(data: FloatArray, d0: Int, d1: Int, d2: Int): Array<Array<FloatArray>> {
        val out = Array(d0) { Array(d1) { FloatArray(d2) } }
        var idx = 0
        for (i in 0 until d0) {
            for (j in 0 until d1) {
                for (k in 0 until d2) {
                    if (idx < data.size) out[i][j][k] = data[idx++]
                }
            }
        }
        return out
    }

    private fun pcmToFloat(samples: ShortArray): FloatArray =
        FloatArray(samples.size) { i -> samples[i] / 32768.0f }

    companion object {
        fun createOrFallback(modelPaths: ModelPaths = ModelPaths.resolve()): VadEngine =
            if (modelPaths.sileroAvailable) SileroVadEngine(modelPaths) else EnergyVadEngine()
    }
}
