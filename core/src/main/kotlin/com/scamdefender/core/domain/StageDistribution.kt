package com.scamdefender.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class StageDistribution(
    val probabilities: Map<AttackStage, Float>,
) {
    val dominantStage: AttackStage
        get() = probabilities.maxByOrNull { it.value }?.key ?: AttackStage.STAGE_0

    val dominantProbability: Float
        get() = probabilities[dominantStage] ?: 0f

    companion object {
        fun uniform(): StageDistribution =
            StageDistribution(
                AttackStage.entries.associateWith { 1f / AttackStage.entries.size },
            )

        fun focused(stage: AttackStage, confidence: Float = 0.8f): StageDistribution {
            val remaining = (1f - confidence) / (AttackStage.entries.size - 1)
            return StageDistribution(
                AttackStage.entries.associateWith { if (it == stage) confidence else remaining },
            )
        }
    }
}

@Serializable
data class StageUpdate(
    val distribution: StageDistribution,
    val dominantStage: AttackStage,
    val transitionDetected: Boolean,
    val riskSlope: Float,
    val smoothedRisk: Float,
)
