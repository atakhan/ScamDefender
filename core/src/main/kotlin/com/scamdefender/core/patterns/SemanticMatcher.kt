package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.PatternSource
import com.scamdefender.core.scenarios.SemanticAnchor

interface SemanticMatcher {
    fun match(text: String): List<DetectedPattern>
}

/**
 * Keyword-overlap semantic matcher for JVM demo.
 * Replace with MiniLM ONNX inference on Android when model assets are available.
 */
class KeywordSemanticMatcher(
    anchors: List<SemanticAnchor>,
    private val patternResolver: (String) -> DetectedPatternType?,
    private val similarityThreshold: Float = 0.45f,
) : SemanticMatcher {
    private data class AnchorEntry(
        val phrases: Set<String>,
        val pattern: DetectedPatternType,
    )

    private val entries =
        anchors.mapNotNull { anchor ->
            val type = patternResolver(anchor.pattern) ?: return@mapNotNull null
            AnchorEntry(
                phrases = (listOf(anchor.anchor) + anchor.similarPhrases).map { it.lowercase() }.toSet(),
                pattern = type,
            )
        }

    override fun match(text: String): List<DetectedPattern> {
        val normalized = text.lowercase()
        val tokens = normalized.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }.toSet()
        if (tokens.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            val best =
                entry.phrases.maxOfOrNull { phrase ->
                    jaccard(tokens, phrase.split(Regex("""\s+""")).toSet())
                } ?: 0f
            if (best >= similarityThreshold) {
                DetectedPattern(
                    type = entry.pattern,
                    confidence = best.coerceIn(0f, 1f),
                    matchedPhrase = entry.phrases.first(),
                    source = PatternSource.SEMANTIC,
                )
            } else {
                null
            }
        }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersection = a.intersect(b).size.toFloat()
        val union = a.union(b).size.toFloat()
        return intersection / union
    }
}
