package com.scamdefender.core.stages

import com.scamdefender.core.domain.AttackStage
import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.domain.StageDistribution
import com.scamdefender.core.domain.StageUpdate

class StageTransitionDetector(
    private val emaAlpha: Float = 0.3f,
    private val transitionSlopeThreshold: Float = 0.04f,
    private val dominanceMargin: Float = 0.15f,
) {
    private var smoothedRisk = 0f
    private var previousRisk = 0f
    private var previousTimestampMs = 0L
    private var previousDominant: AttackStage = AttackStage.STAGE_0
    private val signalHistory = mutableSetOf<DetectedPatternType>()

    fun reset() {
        smoothedRisk = 0f
        previousRisk = 0f
        previousTimestampMs = 0L
        previousDominant = AttackStage.STAGE_0
        signalHistory.clear()
    }

    fun update(
        segment: SpeechSegment,
        features: FeatureVector,
        patterns: List<DetectedPattern>,
    ): StageUpdate {
        val newSignalType = patterns.any { it.type !in signalHistory }
        patterns.forEach { signalHistory.add(it.type) }

        val instantRisk = computeInstantRisk(features, patterns)
        smoothedRisk = emaAlpha * instantRisk + (1f - emaAlpha) * smoothedRisk

        val distribution = buildDistribution(features, patterns, smoothedRisk)
        val dominant = distribution.dominantStage

        val deltaT = (segment.timestampMs - previousTimestampMs).coerceAtLeast(1L)
        val riskSlope =
            if (previousTimestampMs == 0L) {
                0f
            } else {
                (smoothedRisk - previousRisk) / (deltaT / 1000f)
            }

        val dominanceShift =
            dominant != previousDominant &&
                distribution.dominantProbability >= previousDominantProbability(distribution) + dominanceMargin

        val acceleration = riskSlope >= transitionSlopeThreshold

        val transitionDetected =
            dominanceShift && (acceleration || newSignalType) && dominant.ordinal > previousDominant.ordinal

        previousRisk = smoothedRisk
        previousTimestampMs = segment.timestampMs
        previousDominant = dominant

        return StageUpdate(
            distribution = distribution,
            dominantStage = dominant,
            transitionDetected = transitionDetected,
            riskSlope = riskSlope,
            smoothedRisk = smoothedRisk,
        )
    }

    private fun previousDominantProbability(distribution: StageDistribution): Float =
        distribution.probabilities[previousDominant] ?: 0f

    private fun computeInstantRisk(features: FeatureVector, patterns: List<DetectedPattern>): Float {
        val patternBoost = patterns.maxOfOrNull { it.confidence } ?: 0f
        return (
            features.aggregateScore() * 0.5f +
                patternBoost * 0.5f
            ).coerceIn(0f, 1f)
    }

    private fun buildDistribution(
        features: FeatureVector,
        patterns: List<DetectedPattern>,
        risk: Float,
    ): StageDistribution {
        val scores = mutableMapOf<AttackStage, Float>()

        scores[AttackStage.STAGE_0] = (1f - risk) * 0.3f
        scores[AttackStage.STAGE_1] =
            features.authority * 0.9f + patternScore(patterns, DetectedPatternType.AUTHORITY_PLAY)
        scores[AttackStage.STAGE_2] =
            features.fearSignal * 0.8f + patternScore(patterns, DetectedPatternType.PROBLEM_INJECTION) +
                patternScore(patterns, DetectedPatternType.FEAR_THREAT)
        scores[AttackStage.STAGE_3] = features.urgency * 0.9f + patternScore(patterns, DetectedPatternType.URGENCY_ESCALATION)
        scores[AttackStage.STAGE_4] = features.isolation * 0.95f + patternScore(patterns, DetectedPatternType.ISOLATION_SIGNAL)
        scores[AttackStage.STAGE_5] = features.actionRequest * 0.95f + patternScore(patterns, DetectedPatternType.ACTION_REQUEST)
        scores[AttackStage.STAGE_6] = risk * 0.3f

        val total = scores.values.sum().coerceAtLeast(0.001f)
        return StageDistribution(scores.mapValues { (_, v) -> v / total })
    }

    private fun patternScore(patterns: List<DetectedPattern>, type: DetectedPatternType): Float =
        patterns.filter { it.type == type }.maxOfOrNull { it.confidence } ?: 0f
}
