package com.scamdefender.core.risk

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.ScenarioClassification
import com.scamdefender.core.domain.StageUpdate
import com.scamdefender.core.patterns.PatternFsm
import kotlin.math.exp
import kotlin.math.ln

data class RiskWeights(
    val pattern: Float = 0.30f,
    val llmConfidence: Float = 0.25f,
    val urgency: Float = 0.15f,
    val authority: Float = 0.15f,
    val sequenceProgression: Float = 0.15f,
)

data class RiskAggregatorConfig(
    val weights: RiskWeights = RiskWeights(),
    val decayHalfLifeMs: Long = 30_000,
)

class RiskAggregator(
    private val config: RiskAggregatorConfig = RiskAggregatorConfig(),
    private val fsm: PatternFsm? = null,
) {
    private var accumulatedScore = 0f
    private var lastUpdateMs = 0L

    fun reset() {
        accumulatedScore = 0f
        lastUpdateMs = 0L
    }

    fun compute(
        timestampMs: Long,
        features: FeatureVector,
        patterns: List<DetectedPattern>,
        stageUpdate: StageUpdate,
        scenario: ScenarioClassification?,
        sequenceProgression: Float,
    ): Float {
        applyDecay(timestampMs)

        val patternScore = patterns.maxOfOrNull { it.confidence } ?: 0f
        val llmConfidence = scenario?.confidence ?: 0f
        val w = config.weights

        val instant =
            w.pattern * patternScore +
                w.llmConfidence * llmConfidence +
                w.urgency * features.urgency +
                w.authority * features.authority +
                w.sequenceProgression * sequenceProgression +
                stageUpdate.smoothedRisk * 0.1f

        accumulatedScore = (accumulatedScore + instant).coerceIn(0f, 1f)
        lastUpdateMs = timestampMs
        return accumulatedScore
    }

    private fun applyDecay(timestampMs: Long) {
        if (lastUpdateMs == 0L) return
        val elapsed = timestampMs - lastUpdateMs
        if (elapsed <= 0) return
        val lambda = ln(2.0) / config.decayHalfLifeMs
        val factor = exp(-lambda * elapsed).toFloat()
        accumulatedScore *= factor
    }
}
