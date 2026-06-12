package com.scamdefender.core.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SentenceType {
    QUESTION,
    COMMAND,
    STATEMENT,
}

@Serializable
data class SegmentMeta(
    val durationMs: Long = 0,
    val pauseBeforeMs: Long = 0,
    val speechRateWpm: Float? = null,
    val overlapDetected: Boolean = false,
)

@Serializable
data class SpeechSegment(
    val text: String,
    val timestampMs: Long,
    val metadata: SegmentMeta = SegmentMeta(),
)

@Serializable
data class AudioChunk(
    val pcmSamples: ShortArray,
    val sampleRateHz: Int,
    val timestampMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false
        return pcmSamples.contentEquals(other.pcmSamples) &&
            sampleRateHz == other.sampleRateHz &&
            timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = pcmSamples.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
