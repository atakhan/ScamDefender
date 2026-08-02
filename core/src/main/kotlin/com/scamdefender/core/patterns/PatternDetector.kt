package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.PatternDetectionResult
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.scenarios.ScenarioOntology

class PatternDetector(
    ontology: ScenarioOntology = ScenarioOntology.load(),
    semanticMatcher: SemanticMatcher = OnnxSemanticMatcher.createOrFallback(
        ontology.patternsBank.semanticAnchors,
        ontology::patternTypeForName,
    ),
) {
    private val resolver: (String) -> DetectedPatternType? = ontology::patternTypeForName

    private val ahoMatcher = AhoCorasickMatcher(ontology.patternsBank.phrases, resolver)
    private val semanticMatcher: SemanticMatcher = semanticMatcher
    private val fsm =
        PatternFsm(
            ontology.patternsBank.fsmTransitions.mapNotNull(resolver),
        )

    fun detect(segment: SpeechSegment, features: FeatureVector? = null): PatternDetectionResult {
        val keywordMatches = ahoMatcher.match(segment.text)
        val semanticMatches = semanticMatcher.match(segment.text)
        val softMatches = features?.let { SoftPatternInferencer.infer(it) }.orEmpty()
        val merged = suppressNegatedActions(segment.text, merge(keywordMatches, semanticMatches, softMatches))

        val llmTrigger = fsm.update(merged.map { it.type })
        return PatternDetectionResult(
            patterns = merged,
            llmTrigger = llmTrigger,
            fsmState = fsm.currentState.name,
        )
    }

    fun sequenceProgression(): Float = fsm.sequenceProgression()

    fun reset() = fsm.reset()

    private fun suppressNegatedActions(text: String, patterns: List<DetectedPattern>): List<DetectedPattern> {
        val normalized = text.lowercase()
        if (ACTION_NEGATIONS.none { it in normalized }) return patterns
        return patterns.filterNot { it.type == DetectedPatternType.ACTION_REQUEST }
    }

    private fun merge(vararg groups: List<DetectedPattern>): List<DetectedPattern> {
        val byType = mutableMapOf<DetectedPatternType, DetectedPattern>()
        groups.asList().flatten().forEach { pattern ->
            val existing = byType[pattern.type]
            if (existing == null || pattern.confidence > existing.confidence) {
                byType[pattern.type] = pattern
            }
        }
        return byType.values.toList()
    }

    companion object {
        val ACTION_NEGATIONS =
            listOf(
                "не запрашиваем", "не просим", "не требуется", "не дикту",
                "никаких оплат", "никаких переводов", "не нужно",
                "ставить не нужно", "никогда не",
            )
    }
}
