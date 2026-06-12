package com.scamdefender.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class ScenarioClassification(
    val scenario: ScamScenario,
    val stage: AttackStage,
    val confidence: Float,
)

@Serializable
data class DetectionEvent(
    val timestampMs: Long,
    val riskLevel: RiskLevel,
    val riskScore: Float,
    val stage: AttackStage,
    val scenario: ScamScenario?,
    val explanation: List<String>,
    val patterns: List<DetectedPattern> = emptyList(),
    val stageTransition: Boolean = false,
)

@Serializable
data class SegmentReport(
    val segment: SpeechSegment,
    val features: FeatureVector,
    val patterns: PatternDetectionResult,
    val stageUpdate: StageUpdate,
    val detectionEvent: DetectionEvent?,
)

@Serializable
data class PipelineReport(
    val source: String,
    val segments: List<SegmentReport>,
    val events: List<DetectionEvent>,
    val finalRiskLevel: RiskLevel,
    val finalRiskScore: Float,
)
