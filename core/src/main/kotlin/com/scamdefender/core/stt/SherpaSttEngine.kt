package com.scamdefender.core.stt

import com.scamdefender.core.domain.AudioChunk
import com.scamdefender.core.domain.SegmentMeta
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.ml.SherpaJarPaths
import com.scamdefender.core.ml.SherpaModelType
import java.io.Closeable

/**
 * Russian STT via Sherpa-ONNX.
 * Supports offline NeMo transducer models such as GigaAM v2.
 */
class SherpaSttEngine(
    private val modelPaths: ModelPaths = ModelPaths.resolve(),
) : SttEngine, Closeable {
    private val delegate: SttEngine = createDelegate()

    override fun transcribe(chunks: List<AudioChunk>): List<SpeechSegment> = delegate.transcribe(chunks)

    override fun close() {
        if (delegate is Closeable) delegate.close()
    }

    private fun createDelegate(): SttEngine {
        if (!SherpaJarPaths.isOnClasspath()) {
            return FallbackSttEngine("Sherpa JARs missing — run scripts/setup-models.ps1")
        }
        if (!modelPaths.sherpaAvailable) {
            return FallbackSttEngine("Sherpa model files missing in ${modelPaths.sherpaDir}")
        }
        return runCatching {
            when (modelPaths.sherpaModelType) {
                SherpaModelType.NEMO_TRANSDUCER_OFFLINE -> SherpaOnnxOfflineEngine(modelPaths)
                SherpaModelType.UNKNOWN -> error("Unsupported Sherpa model layout in ${modelPaths.sherpaDir}")
            }
        }.getOrElse { error ->
            FallbackSttEngine("Sherpa-ONNX init failed: ${error.message}")
        }
    }

    companion object {
        fun isAvailable(modelPaths: ModelPaths = ModelPaths.resolve()): Boolean =
            SherpaJarPaths.isOnClasspath() && modelPaths.sherpaAvailable
    }
}

private class FallbackSttEngine(
    private val reason: String,
) : SttEngine {
    override fun transcribe(chunks: List<AudioChunk>): List<SpeechSegment> {
        if (chunks.isNotEmpty()) {
            System.err.println("[STT] $reason")
        }
        return emptyList()
    }
}

/**
 * Offline NeMo transducer (GigaAM v2/v3).
 * Docs: model-type=nemo_transducer, encoder.int8.onnx + decoder.onnx + joiner.onnx
 */
private class SherpaOnnxOfflineEngine(
    private val modelPaths: ModelPaths,
) : SttEngine, Closeable {
    private val recognizer: Any
    private val createStream: () -> Any
    private val acceptWaveform: (Any, FloatArray, Int) -> Unit
    private val decode: (Any) -> Unit
    private val getResultText: (Any) -> String
    private val releaseStream: (Any) -> Unit
    private val releaseRecognizer: (Any) -> Unit

    init {
        val recognizerClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
        val config = buildOfflineConfig(modelPaths)
        recognizer = recognizerClass.getConstructor(config.javaClass).newInstance(config)

        val streamClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineStream")
        createStream = { recognizerClass.getMethod("createStream").invoke(recognizer) }
        acceptWaveform = { stream, samples, sampleRate ->
            streamClass.getMethod("acceptWaveform", FloatArray::class.java, Int::class.javaPrimitiveType)
                .invoke(stream, samples, sampleRate)
        }
        decode = { stream -> recognizerClass.getMethod("decode", streamClass).invoke(recognizer, stream) }
        getResultText = { stream ->
            val result = recognizerClass.getMethod("getResult", streamClass).invoke(recognizer, stream)
            result.javaClass.getMethod("getText").invoke(result) as String
        }
        releaseStream = { stream -> streamClass.getMethod("release").invoke(stream) }
        releaseRecognizer = { recognizerClass.getMethod("release").invoke(recognizer) }
    }

    override fun transcribe(chunks: List<AudioChunk>): List<SpeechSegment> {
        if (chunks.isEmpty()) return emptyList()

        val sampleRate = chunks.first().sampleRateHz
        val pcm = concatenateChunks(chunks)
        if (pcm.isEmpty()) return emptyList()

        val stream = createStream()
        return try {
            acceptWaveform(stream, pcm, sampleRate)
            decode(stream)
            val text = getResultText(stream).trim()
            if (text.isBlank()) {
                emptyList()
            } else {
                listOf(
                    SpeechSegment(
                        text = text,
                        timestampMs = chunks.first().timestampMs,
                        metadata = SegmentMeta(durationMs = pcm.size * 1000L / sampleRate),
                    ),
                )
            }
        } finally {
            releaseStream(stream)
        }
    }

    override fun close() {
        releaseRecognizer(recognizer)
    }

    private fun concatenateChunks(chunks: List<AudioChunk>): FloatArray {
        val totalSamples = chunks.sumOf { it.pcmSamples.size }
        val merged = FloatArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.pcmSamples.forEachIndexed { index, sample ->
                merged[offset + index] = sample / 32768.0f
            }
            offset += chunk.pcmSamples.size
        }
        return merged
    }

    private fun buildOfflineConfig(modelPaths: ModelPaths): Any {
        val featureConfig =
            sherpaBuilder("com.k2fsa.sherpa.onnx.FeatureConfig\$Builder")
                .also { builder ->
                    invoke(builder, "setSampleRate", 16_000)
                    invoke(builder, "setFeatureDim", 80)
                }.let(::buildSherpa)

        val transducerConfig =
            sherpaBuilder("com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig\$Builder")
                .also { builder ->
                    invoke(builder, "setEncoder", modelPaths.sherpaEncoder.absolutePath)
                    invoke(builder, "setDecoder", modelPaths.sherpaDecoder.absolutePath)
                    invoke(builder, "setJoiner", modelPaths.sherpaJoiner.absolutePath)
                }.let(::buildSherpa)

        val modelConfig =
            sherpaBuilder("com.k2fsa.sherpa.onnx.OfflineModelConfig\$Builder")
                .also { builder ->
                    invoke(builder, "setTransducer", transducerConfig)
                    invoke(builder, "setTokens", modelPaths.sherpaTokens.absolutePath)
                    invoke(builder, "setNumThreads", 2)
                    invoke(builder, "setDebug", false)
                    invoke(builder, "setModelType", "nemo_transducer")
                }.let(::buildSherpa)

        return sherpaBuilder("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig\$Builder")
            .also { builder ->
                invoke(builder, "setFeatureConfig", featureConfig)
                invoke(builder, "setOfflineModelConfig", modelConfig)
                invoke(builder, "setDecodingMethod", "greedy_search")
            }.let(::buildSherpa)
    }

    private fun sherpaBuilder(className: String): Any =
        Class.forName(className).getConstructor().newInstance()

    private fun buildSherpa(builder: Any): Any = builder.javaClass.getMethod("build").invoke(builder)

    private fun invoke(target: Any, method: String, arg: Any) {
        val paramType =
            when (arg) {
                is Int -> Int::class.javaPrimitiveType
                is Boolean -> Boolean::class.javaPrimitiveType
                is String -> String::class.java
                else -> arg.javaClass
            }
        target.javaClass.getMethod(method, paramType).invoke(target, arg)
    }
}
