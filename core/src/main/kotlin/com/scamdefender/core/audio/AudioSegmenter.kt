package com.scamdefender.core.audio

import com.scamdefender.core.domain.AudioChunk

data class SegmentationConfig(
    val windowMs: Long = 2500,
    val overlapMs: Long = 500,
    val sampleRateHz: Int = 16_000,
)

class AudioSegmenter(
    private val config: SegmentationConfig = SegmentationConfig(),
    private val vadEngine: VadEngine = EnergyVadEngine(),
) {
    private val windowSamples = (config.sampleRateHz * config.windowMs / 1000).toInt()
    private val hopSamples = (config.sampleRateHz * (config.windowMs - config.overlapMs) / 1000).toInt()

    fun segment(pcm: ShortArray, startTimestampMs: Long = 0): List<AudioChunk> {
        if (pcm.isEmpty()) return emptyList()

        val chunks = mutableListOf<AudioChunk>()
        var offset = 0
        var timestamp = startTimestampMs

        while (offset < pcm.size) {
            val end = minOf(offset + windowSamples, pcm.size)
            val window = pcm.copyOfRange(offset, end)
            val chunk = AudioChunk(window, config.sampleRateHz, timestamp)
            if (vadEngine.isSpeech(chunk) || window.size >= windowSamples / 2) {
                chunks.add(chunk)
            }
            if (end >= pcm.size) break
            offset += hopSamples
            timestamp += config.windowMs - config.overlapMs
        }
        return chunks
    }
}
