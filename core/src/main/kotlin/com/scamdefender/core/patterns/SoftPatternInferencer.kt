package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.PatternSource

/**
 * Maps strong feature channels to soft pattern hits so sequence/FSM can advance
 * when phrase-bank keywords miss paraphrases.
 */
object SoftPatternInferencer {
    fun infer(features: FeatureVector): List<DetectedPattern> {
        val out = mutableListOf<DetectedPattern>()
        fun add(type: DetectedPatternType, score: Float, threshold: Float = 0.42f) {
            if (score >= threshold) {
                out +=
                    DetectedPattern(
                        type = type,
                        confidence = (score * 0.72f).coerceIn(0.4f, 0.78f),
                        matchedPhrase = "feature:${type.name.lowercase()}",
                        source = PatternSource.HEURISTIC,
                    )
            }
        }
        add(DetectedPatternType.AUTHORITY_PLAY, features.authority)
        add(DetectedPatternType.PROBLEM_INJECTION, features.fearSignal)
        add(DetectedPatternType.FEAR_THREAT, features.fearSignal, threshold = 0.55f)
        add(DetectedPatternType.URGENCY_ESCALATION, features.urgency)
        add(DetectedPatternType.ISOLATION_SIGNAL, features.isolation)
        add(DetectedPatternType.ACTION_REQUEST, features.actionRequest, threshold = 0.45f)
        return out
    }
}
