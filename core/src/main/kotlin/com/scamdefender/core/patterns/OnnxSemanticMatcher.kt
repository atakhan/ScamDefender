package com.scamdefender.core.patterns

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.PatternSource
import com.scamdefender.core.ml.ManagedOnnxSession
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.ml.OnnxRuntimeHolder
import com.scamdefender.core.scenarios.SemanticAnchor
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * MiniLM-L12-v2 ONNX semantic matcher.
 * Anchor phrases are encoded once at startup; runtime encodes only the input segment.
 */
class OnnxSemanticMatcher(
    anchors: List<SemanticAnchor>,
    private val patternResolver: (String) -> DetectedPatternType?,
    modelPaths: ModelPaths = ModelPaths.resolve(),
    private val similarityThreshold: Float = 0.62f,
) : SemanticMatcher {
    private val session: OrtSession? = ManagedOnnxSession(modelPaths.minilmOnnx).session
    private val tokenizer: HuggingFaceTokenizer? =
        if (modelPaths.minilmTokenizer.isFile) {
            HuggingFaceTokenizer.newInstance(modelPaths.minilmTokenizer.toPath())
        } else {
            null
        }

    private data class AnchorEmbedding(
        val pattern: DetectedPatternType,
        val phrase: String,
        val vector: FloatArray,
    )

    private val anchorEmbeddings: List<AnchorEmbedding> = buildAnchorEmbeddings(anchors)

    override fun match(text: String): List<DetectedPattern> {
        if (text.isBlank() || session == null || tokenizer == null || anchorEmbeddings.isEmpty()) {
            return emptyList()
        }

        val query = encode(text) ?: return emptyList()
        return anchorEmbeddings.mapNotNull { anchor ->
            val similarity = cosine(query, anchor.vector)
            if (similarity >= similarityThreshold) {
                DetectedPattern(
                    type = anchor.pattern,
                    confidence = similarity.coerceIn(0f, 1f),
                    matchedPhrase = anchor.phrase,
                    source = PatternSource.SEMANTIC,
                )
            } else {
                null
            }
        }
    }

    private fun buildAnchorEmbeddings(anchors: List<SemanticAnchor>): List<AnchorEmbedding> {
        if (session == null || tokenizer == null) return emptyList()
        return anchors.flatMap { anchor ->
            val type = patternResolver(anchor.pattern) ?: return@flatMap emptyList()
            (listOf(anchor.anchor) + anchor.similarPhrases).mapNotNull { phrase ->
                val vector = encode(phrase) ?: return@mapNotNull null
                AnchorEmbedding(type, phrase, vector)
            }
        }
    }

    private fun encode(text: String): FloatArray? {
        val session = session ?: return null
        val tokenizer = tokenizer ?: return null
        val encoding: Encoding = tokenizer.encode(text)
        val inputIds = encoding.ids
        val attentionMask = encoding.attentionMask
        val seqLen = inputIds.size.toLong()

        val env = OnnxRuntimeHolder.environment
        val idsTensor =
            OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(inputIds),
                longArrayOf(1, seqLen),
            )
        val maskTensor =
            OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(attentionMask),
                longArrayOf(1, seqLen),
            )
        val tokenTypeTensor =
            OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(inputIds.size)),
                longArrayOf(1, seqLen),
            )

        val inputs = linkedMapOf<String, OnnxTensor>()
        val inputNames = session.inputNames.toSet()
        if ("input_ids" in inputNames) inputs["input_ids"] = idsTensor
        if ("attention_mask" in inputNames) inputs["attention_mask"] = maskTensor
        if ("token_type_ids" in inputNames) inputs["token_type_ids"] = tokenTypeTensor

        return try {
            session.run(inputs).use { result ->
                val hidden = result.get(0).value
                meanPool(hidden, attentionMask)
            }
        } finally {
            idsTensor.close()
            maskTensor.close()
            tokenTypeTensor.close()
        }
    }

    private fun meanPool(hidden: Any, attentionMask: LongArray): FloatArray {
        val tokenVectors =
            when (hidden) {
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (hidden as Array<Array<FloatArray>>)[0]
                }
                else -> return FloatArray(0)
            }
        if (tokenVectors.isEmpty()) return FloatArray(0)

        val dim = tokenVectors[0].size
        val pooled = FloatArray(dim)
        var count = 0f
        for (i in tokenVectors.indices) {
            if (attentionMask.getOrElse(i) { 0L } == 0L) continue
            count += 1f
            for (j in 0 until dim) {
                pooled[j] += tokenVectors[i][j]
            }
        }
        if (count == 0f) return pooled
        for (j in pooled.indices) pooled[j] /= count
        return normalize(pooled)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm == 0f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    companion object {
        fun createOrFallback(
            anchors: List<SemanticAnchor>,
            patternResolver: (String) -> DetectedPatternType?,
            modelPaths: ModelPaths = ModelPaths.resolve(),
        ): SemanticMatcher =
            if (modelPaths.minilmAvailable) {
                OnnxSemanticMatcher(anchors, patternResolver, modelPaths)
            } else {
                KeywordSemanticMatcher(anchors, patternResolver)
            }
    }
}
