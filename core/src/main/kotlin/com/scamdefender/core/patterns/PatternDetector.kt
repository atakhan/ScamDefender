package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
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

    fun detect(segment: SpeechSegment): PatternDetectionResult {
        val keywordMatches = ahoMatcher.match(segment.text)
        val semanticMatches = semanticMatcher.match(segment.text)
        val merged = merge(keywordMatches, semanticMatches)

        val llmTrigger = fsm.update(merged.map { it.type })
        return PatternDetectionResult(
            patterns = merged,
            llmTrigger = llmTrigger,
            fsmState = fsm.currentState.name,
        )
    }

    fun reset() = fsm.reset()

    private fun merge(
        keyword: List<DetectedPattern>,
        semantic: List<DetectedPattern>,
    ): List<DetectedPattern> {
        val byType = mutableMapOf<DetectedPatternType, DetectedPattern>()
        (keyword + semantic).forEach { pattern ->
            val existing = byType[pattern.type]
            if (existing == null || pattern.confidence > existing.confidence) {
                byType[pattern.type] = pattern
            }
        }
        return byType.values.toList()
    }
}
