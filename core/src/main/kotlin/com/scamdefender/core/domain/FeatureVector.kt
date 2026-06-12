package com.scamdefender.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class FeatureVector(
    val urgency: Float,
    val authority: Float,
    val actionRequest: Float,
    val fearSignal: Float,
    val isolation: Float = 0f,
    val sentenceType: SentenceType = SentenceType.STATEMENT,
) {
    fun aggregateScore(): Float =
        (urgency + authority + actionRequest + fearSignal + isolation) / 5f
}
