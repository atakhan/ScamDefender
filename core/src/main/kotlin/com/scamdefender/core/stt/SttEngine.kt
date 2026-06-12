package com.scamdefender.core.stt

import com.scamdefender.core.domain.AudioChunk
import com.scamdefender.core.domain.SpeechSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface SttEngine {
    fun transcribe(chunks: List<AudioChunk>): List<SpeechSegment>
    fun transcribeStream(chunks: Flow<AudioChunk>): Flow<SpeechSegment> = flow {
        val buffer = mutableListOf<AudioChunk>()
        chunks.collect { chunk ->
            buffer.add(chunk)
            transcribe(listOf(chunk)).forEach { emit(it) }
        }
    }
}

class MockSttEngine(
    private val segments: List<SpeechSegment>,
) : SttEngine {
    override fun transcribe(chunks: List<AudioChunk>): List<SpeechSegment> {
        if (chunks.isEmpty()) return emptyList()
        val timestamp = chunks.first().timestampMs
        return segments.filter { it.timestampMs == timestamp }
    }
}
