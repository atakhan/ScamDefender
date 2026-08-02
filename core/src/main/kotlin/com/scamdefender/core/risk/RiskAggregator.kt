package com.scamdefender.core.risk

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.PatternSource
import com.scamdefender.core.domain.ScenarioClassification
import com.scamdefender.core.domain.StageUpdate
import kotlin.math.exp
import kotlin.math.ln

data class RiskWeights(
    val pattern: Float = 0.28f,
    val llmConfidence: Float = 0.18f,
    val urgency: Float = 0.12f,
    val authority: Float = 0.10f,
    val sequenceProgression: Float = 0.22f,
    val isolationAction: Float = 0.10f,
)

data class RiskAggregatorConfig(
    val weights: RiskWeights = RiskWeights(),
    val decayHalfLifeMs: Long = 30_000,
)

class RiskAggregator(
    private val config: RiskAggregatorConfig = RiskAggregatorConfig(),
) {
    private var accumulatedScore = 0f
    private var lastUpdateMs = 0L
    private val seenTypes = mutableSetOf<DetectedPatternType>()

    fun reset() {
        accumulatedScore = 0f
        lastUpdateMs = 0L
        seenTypes.clear()
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
        patterns.forEach { seenTypes += it.type }

        val patternScore = weightedPatternScore(patterns)
        val llmConfidence = scenario?.confidence ?: 0f
        val isolationAction =
            maxOf(features.isolation, features.actionRequest) *
                if (seenTypes.size >= 3) 1f else 0.55f

        // Sequence only counts once several attack layers appear — blocks early-only legit alerts.
        // PROBLEM+ACTION (delivery-style) is enough pressure without authority framing.
        val hasProblemAndAction =
            DetectedPatternType.PROBLEM_INJECTION in seenTypes &&
                DetectedPatternType.ACTION_REQUEST in seenTypes
        val gatedSequence =
            when {
                seenTypes.size >= 4 -> sequenceProgression
                seenTypes.size == 3 -> sequenceProgression * 0.75f
                hasProblemAndAction -> sequenceProgression * 0.65f
                seenTypes.size == 2 -> sequenceProgression * 0.35f
                else -> sequenceProgression * 0.15f
            }

        val w = config.weights
        val instant =
            w.pattern * patternScore +
                w.llmConfidence * llmConfidence +
                w.urgency * features.urgency +
                w.authority * features.authority +
                w.sequenceProgression * gatedSequence +
                w.isolationAction * isolationAction +
                stageUpdate.smoothedRisk * 0.08f

        val earlyOnly = isEarlyOnlyAttack()
        val lateFeaturesLow =
            features.urgency < 0.4f && features.isolation < 0.35f && features.actionRequest < 0.35f

        val growth =
            when {
                earlyOnly && lateFeaturesLow -> instant * 0.4f
                onlyEarlySoftSignals(patterns) && lateFeaturesLow -> instant * 0.55f
                else -> instant
            }

        accumulatedScore = (accumulatedScore + growth).coerceIn(0f, 1f)
        // Early framing (authority/problem) without pressure/action stays below SUSPICIOUS.
        if (earlyOnly && lateFeaturesLow) {
            accumulatedScore = accumulatedScore.coerceAtMost(EARLY_ONLY_SCORE_CAP)
        }
        lastUpdateMs = timestampMs
        return accumulatedScore
    }

    private fun isEarlyOnlyAttack(): Boolean {
        val late =
            setOf(
                DetectedPatternType.URGENCY_ESCALATION,
                DetectedPatternType.ISOLATION_SIGNAL,
                DetectedPatternType.ACTION_REQUEST,
            )
        return seenTypes.isNotEmpty() && seenTypes.none { it in late }
    }

    private fun weightedPatternScore(patterns: List<DetectedPattern>): Float {
        if (patterns.isEmpty()) return 0f
        return patterns.maxOf { pattern ->
            val sourceFactor =
                when (pattern.source) {
                    PatternSource.KEYWORD -> 1f
                    PatternSource.SEMANTIC -> 0.92f
                    PatternSource.HEURISTIC -> 0.7f
                    PatternSource.FSM -> 0.85f
                }
            pattern.confidence * sourceFactor
        }
    }

    private fun onlyEarlySoftSignals(patterns: List<DetectedPattern>): Boolean {
        if (patterns.isEmpty()) return true
        val early =
            setOf(
                DetectedPatternType.AUTHORITY_PLAY,
                DetectedPatternType.PROBLEM_INJECTION,
                DetectedPatternType.FEAR_THREAT,
            )
        return patterns.all {
            it.type in early && it.source == PatternSource.HEURISTIC
        } && seenTypes.all { it in early }
    }

    private fun applyDecay(timestampMs: Long) {
        if (lastUpdateMs == 0L) return
        val elapsed = timestampMs - lastUpdateMs
        if (elapsed <= 0) return
        val lambda = ln(2.0) / config.decayHalfLifeMs
        val factor = exp(-lambda * elapsed).toFloat()
        accumulatedScore *= factor
    }

    companion object {
        /** Just under RiskStateMachine.suspicious (0.40). */
        const val EARLY_ONLY_SCORE_CAP = 0.38f
    }
}
