package com.scamdefender.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DetectedPatternType {
    @SerialName("AUTHORITY_PLAY")
    AUTHORITY_PLAY,

    @SerialName("URGENCY_ESCALATION")
    URGENCY_ESCALATION,

    @SerialName("ISOLATION_SIGNAL")
    ISOLATION_SIGNAL,

    @SerialName("ACTION_REQUEST")
    ACTION_REQUEST,

    @SerialName("FEAR_THREAT")
    FEAR_THREAT,

    @SerialName("PROBLEM_INJECTION")
    PROBLEM_INJECTION,
}

@Serializable
data class DetectedPattern(
    val type: DetectedPatternType,
    val confidence: Float,
    val matchedPhrase: String? = null,
    val source: PatternSource = PatternSource.KEYWORD,
)

@Serializable
enum class PatternSource {
    @SerialName("keyword")
    KEYWORD,

    @SerialName("semantic")
    SEMANTIC,

    @SerialName("fsm")
    FSM,
}

@Serializable
data class PatternDetectionResult(
    val patterns: List<DetectedPattern>,
    val llmTrigger: Boolean,
    val fsmState: String,
)
